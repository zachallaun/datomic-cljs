**[Install](#install)** |
**[Minimum Viable Snippet](#minimum-viable-snippet)** |
**[Using Datomic REST](#using-datomic-rest)** |
**[API Overview](#api-overview)** |
**[Limitations](#limitations)** |
**[Developing](#developing)**

# ClojureScript, meet Datomic

datomic-cljs provides an interface to Datomic that is as close as possible to the native [Clojure API][clojure-api].
It approximates Clojure's blocking API with [core.async][core.async].
It supports both Node.js and modern browsers.

_**Warning:** This is incomplete, alpha software. Everything is subject to change._

## Install

**Leiningen**

```clj
[com.zachallaun/datomic-cljs "0.0.1-alpha-1"]
```

**npm**

If you're targeting Node, datomic-cljs has an additional dependency.
Add the following to your `package.json`:

```js
"dependencies" : {
  "request" : "2.27.0"
}
```

## Minimum Viable Snippet

```clj
(ns example
  (:require [datomic-cljs.api :as d]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; Find the entity ID associated with a person named "Frank".
;; Change his name to be "Franky".
(go
  (let [conn (d/connect "localhost" 9898 "local" "friends")
        [[eid]] (<! (d/q '[:find ?e :where [?e :person/name "Frank"]]
                         (d/db conn)))]
    (<! (d/transact conn [[:db/add eid :person/name "Franky"]]))))
```

## Using Datomic REST

datomic-cljs talks to a Datomic database through the Datomic REST service.
Datomic REST is a standalone server that sits in front of a transactor and exposes Datomic through HTTP.

For the sake of these examples, I'll assume that you're using [Datomic Free][datomic-free] and that you have a `DATOMIC_HOME` environment variable set, pointing to the directory of your peer library.

First, start a transactor.

```sh
$ $DATOMIC_HOME/bin/transactor $DATOMIC_HOME/config/samples/free-transactor-template.properties
```

By default, this will start a Datomic Free transactor at `datomic:free://localhost:4334/`.
With a transactor in place, you can start the Datomic REST service.
If you're planning to use datomic-cljs in the browser, you'll have to include the `--origins` flag to specify allowed origins for cross-origin resource sharing.
If you're planning to use datomic-cljs on node, `--origins` can be omitted from the following.

```sh
$ $DATOMIC_HOME/bin/rest --port 9898 --origins http://0.0.0.0:8000 local datomic:free://localhost:4334/
```

The `rest` script accepts repeated alias/transactor pairs.
`local` is an alias that specifies your transactor.

Once you have the service running, you can visit http://localhost:9898 in your browser to see documentation for the REST API and interact with the service through web forms.

## API Overview

In general, datomic-cljs exposes the same API as its Clojure counterpart.
But, where the Clojure library would block awaiting a result to communicate, datomic-cljs returns a core.async channel.
To learn more about core.async, I recommend David Nolen's article [Communicating Sequential Processes][csp] and the [core.async API documentation][core.async-docs].

See [examples/friends.cljs](/examples/friends.cljs) for an example of much of what follows.

### Namespaces of Interest

```clj
(defn example
  (:require [datomic-cljs.api :as d])
  (:require-macros [datomic-cljs.macros :refer [<?]]))
```

Everything exciting happens in `datomic-cljs.api`, with the exception of the single `<?` macro from `datomic-cljs.macros`.
(See [Error Handling](#error-handling) for information on `<?`.)

### Creating and Connecting to Databases

#### Create a connection to an existing database

```clj
(def conn (d/connect "localhost" ;; hostname of your running REST service
                     9898        ;; port
                     "local"     ;; transactor alias
                     "friends")) ;; database name
```

This returns a core.async channel that will contain either a database connection or an error.

#### Create a database

```clj
(go
  (let [conn (<? (d/create-database "localhost" 9898 "local" "friends"))]
    ...))
```

Accepts the same arguments as `connect`, but will attempt to create a new database, connecting to it if it already exists.
This returns a core.async channel that will contain either a database connection or an error.


### Database Values, Query, Entity, and Transactions

For the most part, these are quite similar to their Clojure API counterparts, except that they return core.async channels eventually containing either results or errors.
(See the [Datomic reference][datomic-reference] for more information on what's possible.)

#### Differences

##### `datomic-cljs.api/entity`

This function differs in two ways.
Its result is just a plain hash-map, in contrast to the Clojure API's lazily-evaluating, hash-map-like Entity object.
Attributes in the Entity that are refs to other entities will not contain nested entity maps.  Instead, they will contain entity ids.
This avoids circular references.
To access nested entities, you'll have to pass the entity ids back to `datomic-cljs.api/entity`.

```clj
;; Find an entity whose name is "Caroll".
;; Get all of her friend entities asynchronously, parking on the first.
(go
  (let [db (d/db conn)
        [[eid]] (<? (d/q '[:find ?e :where [?e :person/name "Caroll"]] db))
        friends (->> (<? (d/entity db eid))
                     :person/friends
                     (map #(d/entity db %)))]
    (<? (first friends))))
```

##### `datomic-cljs.api/limit`

Added.
This limits the results to a certain number.
It composes in the same way as the other query operations.

##### `datomic-cljs.api/offset`

Added.
This begins returning results at a certain number.
It composes in the same way as the other query operations.

### Error Handling

Errors are put directly on return channels.
To keep from wrapping each parking take in a conditional, `datomic-cljs.macros/<?` is introduced.
Instead of `cljs.core.async/<!`, use `datomic-cljs.macros/<?` to take from channels.
If a `js/Error` is taken, it will be thrown.
Here's the Minimum Viable Snippet rewritten to handle errors:

```clj
(ns example
  (:require [datomic-cljs.api :as d]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [datomic-cljs.macros :refer [<?]]))

;; Find the entity ID associated with a person named "Frank".
;; Change his name to be "Franky".
;; If any async operation returns an error, rethrow it!
(go
  (try
    (let [conn (d/connect "localhost" 9898 "local" "friends")
          [[eid]] (<? (d/q '[:find ?e :where [?e :person/name "Frank"]]
                           (d/db conn)))]
      (<? (d/transact conn [[:db/add eid :person/name "Franky"]])))
    (catch js/Error e
      (println "Something went wrong!"))))
```

## Limitations

There's work left to be done.
For missing pieces of the API, see the bottom of [api.cljs](/src/datomic_cljs/api.cljs).

Things we don't have but probably should:

1. For the browser, some kind of authentication story.
Transaction is currently wide open.
- Much better test coverage, including tests for things like malformed input.

## Developing

Testing involves shoving errors into a vector and printing it.
If the vector's empty, there are no errors and we rejoice.

Assumptions are documented at the top of [t_api.cljs][t-api-notes].

### Node

```sh
$ lein cljsbuild once
$ node target/test.js
```

### Browser

```sh
$ lein cljsbuild once
$ python -m SimpleHTTPServer 8000 # then visit 0.0.0.0:8000
```

This will serve the project root.
Navigate to the index, which will include the browser tests, and open the console to see the results.

_Note:_ You may see nasty CORS-related errors in the browser tests.
They're unavoidable.

[clojure-api]: http://docs.datomic.com/clojure/index.html
[core.async]: https://github.com/clojure/core.async
[datomic-free]: https://my.datomic.com/downloads/free
[csp]: http://swannodette.github.io/2013/07/12/communicating-sequential-processes/
[core.async-docs]: http://clojure.github.io/core.async/
[datomic-reference]: http://docs.datomic.com/
[t-api-notes]: https://github.com/zachallaun/datomic-cljs/blob/master/test/datomic_cljs/t_api.cljs#L11
