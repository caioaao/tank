[![CircleCI](https://circleci.com/gh/caioaao/tank/tree/master.svg?style=svg)](https://circleci.com/gh/caioaao/tank/tree/master) [![Clojars Project](https://img.shields.io/clojars/v/com.caioaao/tank.svg)](https://clojars.org/com.caioaao/tank)

# tank

Fault tolerant idioms for distributed systems.

## Usage

### Retry

The retry API is available in `com.caioaao.tank.retry`. Currently there are two types of retry strategies:

- Simple sleep: after each failed attempt, it will sleep for the specified amount of milliseconds
- Exponential backoff: uses the [exponential backoff algorithm](https://en.wikipedia.org/wiki/Exponential_backoff) for determining how many seconds it should sleep between failed attempts.

The usage is pretty simple. You build a config for your strategy and pass it to the macro that will run it for you:

```clojure
(require '[com.caioaao.tank.retry :as tank.retry])

(tank.retry/with (tank.retry/exponential-backoff-config 5 10 :catch? (constantly true))
  ...)
```

If it fails to successfully run in the amount of attempts (in this case 5), it will throw an exception.

As you may want to treat non-exceptions as errors, you can also pass a `:failed?` function that will check the evaluated body and, if returns `true`, will treat it as a retriable error:

```clojure
(require '[com.caioaao.tank.retry :as tank.retry])

(tank.retry/with (tank.retry/exponential-backoff-config 5 10 :failed? (partial contains? :error))
  ...)
```

__Important:__ By default it will treat every exception as unexpected and will re-throw it.


### Circuit breaker

The [circuit breaker](https://martinfowler.com/bliki/CircuitBreaker.html) pattern is really useful for avoiding cascading failures in distributed systems. It behaves like an electrical circuit breaker, meaning that after some failed attempts it will fail immediately. In case of a immediate fail, it throws an exception.

The way it is implemented is by using a [leaky bucket algorithm](https://en.wikipedia.org/wiki/Leaky_bucket).

Here's an example:

```clojure
(require '[com.caioaao.tank.circuit-breaker :as tank.circuit-breaker])
(defn http-request! [] ...)

(let [circuit-breaker (tank.circuit-breaker/circuit-breaker 10 100)]
  (tank.circuit-breaker/call! circuit-breaker http-request!))
```

This circuit breaker object can be shared between several calls (eg: calls to multiple endpoints from a single service can share a single circuit breaker).

Just like `retry`, it can receive the optional keyword argument `failed?` to know when a successful execution should actually be treated as an error.

## License

Copyright © 2018 Caio Oliveira

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
