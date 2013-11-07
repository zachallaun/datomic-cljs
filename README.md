# Datomic on ClojureScript

The goal of datomic-cljs is to provide an interface to Datomic that is as close as possible to the native Clojure [peer API](http://docs.datomic.com/clojure/index.html).
This is accomplished by approximating Clojure's blocking API with [core.async](https://github.com/clojure/core.async).
datomic-cljs currently targets Node.js, though browser support is a possible future feature.

_**Warning:** This is incomplete, alpha software. Anything is subject to change._

[Install](#install) |
[Minimum Viable Snippet](#minimum-viable-snippet) |
[Using Datomic REST](#using-datomic-rest) |
[API](#api) |
[To Do](#to-do)

## Install

Add the following dependency your `project.clj`:

```clj
[com.zachallaun/datomic-cljs "TODO"]
```

This will transitively include core.async, but you'll likely want to specify your own version; it's a fast-moving target.

## Minimum Viable Snippet

```clj
(ns example
  (:require [datomic-cljs.api :as d]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(go
  (let [conn (d/connect "localhost" 9898 "db" "friends")
        eid (ffirst (<! (d/q '[:find ?e :where [?e :person/name "Frank"]]
                             (d/db conn))))]
    (<! (d/transact conn [[:db/add eid :person/name "Franky"]]))))
```

## Using Datomic REST

datomic-cljs talks to a Datomic database by way of the Datomic REST service.
Datomic REST is a standalone server that sits in front of a transactor and exposes Datomic via HTTP.
For the sake of these examples, I'll assume that you're using Datomic Free and that you have a `DATOMIC_HOME` environment variable set, pointing to the directory of your peer library.

First, start a transactor.

```sh
$DATOMIC_HOME/bin/transactor $DATOMIC_HOME/config/samples/free-transactor-template.properties
```

By default, this will start a Datomic Free transactor at `datomic:free://localhost:4334/`.
With a transactor in place, you can start the Datomic REST service.

```sh
$DATOMIC_HOME/bin/rest local datomic:free://localhost:4334/
```

The `rest` script accepts repeated alias/transactor pairs; you'll refer to the alias (in this case, `local`) to specify a transactor when interacting with the REST API.

## API

In general, datomic-cljs exposes the same API as its Clojure counterpart, except that any operation that requires hitting the REST API returns a core.async channel.

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
    (let [conn (d/connect "localhost" 9898 "db" "friends")
          eid (ffirst (<? (d/q '[:find ?e :where [?e :person/name "Frank"]]
                               (d/db conn))))]
      (<? (d/transact conn [[:db/add eid :person/name "Franky"]])))
    (catch js/Error e
      (println "Something went wrong!"))))
```

## To Do

There is a _lot_ left to be done.
What I have now are the bones of the API.
Not everything can be done through the Datomic REST api, but see the bottom of [api.cljs](/src/datomic_cljs/api.cljs) for a list of planned functionality.
