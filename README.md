**[Install](#install)** |
**[Minimum Viable Snippet](#minimum-viable-snippet)** |
**[Using Datomic REST](#using-datomic-rest)** |
**[API Overview](#api-overview)** |
**[To Do](#to-do)**

# ClojureScript, meet Datomic

The goal of datomic-cljs is to provide an interface to Datomic that is as close as possible to the native [Clojure API](http://docs.datomic.com/clojure/index.html).
This is accomplished by approximating Clojure's blocking API with [core.async](https://github.com/clojure/core.async).
datomic-cljs currently targets Node.js, though browser support is a possible future feature.

_**Warning:** This is incomplete, alpha software. Anything is subject to change._

## Install

Add the following dependency your `project.clj`:

```clj
[com.zachallaun/datomic-cljs "TODO"]
```

This will transitively include core.async, but you'll likely want to specify your own version since it's a fast-moving target.

## Minimum Viable Snippet

```clj
(ns example
  (:require [datomic-cljs.api :as d]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(go
  (let [conn (d/connect "localhost" 9898 "local" "friends")
        eid (ffirst (<! (d/q '[:find ?e :where [?e :person/name "Frank"]]
                             (d/db conn))))]
    (<! (d/transact conn [[:db/add eid :person/name "Franky"]]))))
```

## Using Datomic REST

datomic-cljs talks to a Datomic database by way of the Datomic REST service.
Datomic REST is a standalone server that sits in front of a transactor and exposes Datomic through HTTP.
For the sake of these examples, I'll assume that you're using [Datomic Free](https://my.datomic.com/downloads/free) and that you have a `DATOMIC_HOME` environment variable set, pointing to the directory of your peer library.

First, start a transactor.

```sh
$ $DATOMIC_HOME/bin/transactor $DATOMIC_HOME/config/samples/free-transactor-template.properties
```

By default, this will start a Datomic Free transactor at `datomic:free://localhost:4334/`.
With a transactor in place, you can start the Datomic REST service.

```sh
$ $DATOMIC_HOME/bin/rest local datomic:free://localhost:4334/
```

The `rest` script accepts repeated alias/transactor pairs; you'll refer to the alias (in this case, `local`) to specify a transactor when interacting with the REST API.

It's worth noting that once you have the service running, you can visit http://localhost:9898 to see documentation for the REST API and generally interact with the service through web forms.
(These pages are returned when `Content-Type: text/html` is requested.)

## API Overview

In general, datomic-cljs exposes the same API as its Clojure counterpart, except that any operation that requires hitting the REST API returns a core.async channel.
To learn more about core.async, I recommend David Nolen's article [Communicating Sequential Processes](http://swannodette.github.io/2013/07/12/communicating-sequential-processes/) and the [core.async API documentation](http://clojure.github.io/core.async/).

See [friends.cljs](/examples/friends.cljs) for an example of much of what follows.

### Namespaces of Interest

```clj
(defn example
  (:require [datomic-cljs.api :as d])
  (:require-macros [datomic-cljs.macros :refer [<?]]))
```

For the most part, everything exciting happens in `datomic-cljs.api`, with the exception of the single `<?` macro from `datomic-cljs.macros`.
(See [Error Handling](#error-handling) for information on `<?`.)

### Creating and Connecting to Databases

To create a connection to an existing database, use `datomic-cljs.api/connect`.

```clj
(def conn (d/connect "localhost" ;; hostname of your running REST service
                     9898        ;; port
                     "local"     ;; transactor alias
                     "friends")) ;; database name
```

To create a new database, use `datomic-cljs.api/create-database`.
The function `datomic-cljs.api/create-database` accepts the same arguments as `connect`, but will attempt to create a new database or connect to it if it already exists.
It returns a core.async channel that will eventually contain either a database connection or an error.

```clj
(go
  (let [conn (<? (d/create-database "localhost" 9898 "local" "friends"))]
    ...))
```

### Accessing a Database Value

To access the current value of the database referred to by `conn`, use `datomic-cljs.api/db`.

```clj
(d/db conn)
```

datomic-cljs also supports database values that are `as-of` or `since` a transaction or instant.

```clj
(d/as-of (d/db conn) #inst "2012")
(d/since (d/db conn) #inst "2013")
```

### Query, Entity, and Transactions

For the most part, these are quite similar to their Clojure API counterparts, except that they return core.async channels eventually containing either results or errors.
See the [Datomic reference](http://docs.datomic.com/) for more information on what's possible.

However, `datomic-cljs.api/entity` differs in two ways.
First, its result is eventually just a plain hash-map, in contrast to the Clojure API's lazily-evaluating, hash-map-like Entity object.
The second difference is a consequence of the first: entity attributes that are refs to other entities will not contain nested entity maps, but instead entity ids.
This is due to the possibility of circular references.
To access nested entities, you'll have to pass the entity ids back to `datomic-cljs.api/entity`.

### Error Handling

The technique du jour for asyncronous error handling in JavaScript-land is to pass binary callback functions, the first argument being a possible error and the second being a possible result.
Error handling then becomes an explicit conditional.
Because I wanted a Datomic API as close to Clojure's as possible, I chose not to put `[error data]` tuples into the core.async channels that are returned from API calls.
Instead, I've taken a cue from David Nolen's article [Asyncronous Error Handling](http://swannodette.github.io/2013/08/31/asynchronous-error-handling/).

In the event of success, data is placed directly on the channel, and in the event of an error, an error is placed directly on the channel.
A new channel take primitive is then introduced, `datomic-cljs.macros/<?`, which is similar to `clojure.core.async/<!` except that it will throw errors that come down the channel.
We could rewrite the Minimum Viable Snippet above to handle errors like so:

```clj
(ns example
  (:require [datomic-cljs.api :as d]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [datomic-cljs.macros :refer [<?]]))

(go
  (try
    (let [conn (d/connect "localhost" 9898 "local" "friends")
          eid (ffirst (<? (d/q '[:find ?e :where [?e :person/name "Frank"]]
                               (d/db conn))))]
      (<? (d/transact conn [[:db/add eid :person/name "Franky"]])))
    (catch js/Error e
      (println "Something went wrong!"))))
```

## To Do

There is a _lot_ left to be done.
What I have now are the bones of the API, but very little of the flesh.

Not everything can be done through the Datomic REST api, but see the bottom of [api.cljs](/src/datomic_cljs/api.cljs) for a list of planned functionality.

A new namespace `datomic-cljs.edn` and an accompanying `read-string` might be warranted to support tagged literals like `#db/id`.

Support for REST API-specific things like limiting results.
