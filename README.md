[![CircleCI](https://circleci.com/gh/caioaao/tank/tree/master.svg?style=svg)](https://circleci.com/gh/caioaao/tank/tree/master) [![Clojars Project](https://img.shields.io/clojars/v/tank.svg)](https://clojars.org/tank)

# tank

Fault tolerant idioms for distributed systems.

## Usage

### Retry

The retry API is available in `tank.retry`. Currently there are two types of retry strategies:

- Simple sleep: after each failed attempt, it will sleep for the specified amount of milliseconds
- Exponential backoff: uses the [exponential backoff algorithm](https://en.wikipedia.org/wiki/Exponential_backoff) for determining how many seconds it should sleep between failed attempts.

The usage is pretty simple. You pass the parameters for the strategy, a `catch-fn` to select which exceptions are retriable and the body that should run. Here's an example:

```clojure
(require '[tank.retry])

(tank.retry/with-exponential-backoff 10 5 (constantly true)
  ...)
```

If it fails to successfully run in the amount of attempts (in this case 5), it will throw an exception.


### Circuit breaker

The [circuit breaker](https://martinfowler.com/bliki/CircuitBreaker.html) pattern is really useful for avoiding cascading failures in distributed systems. It behaves like an electrical circuit breaker, meaning that after some failed attempts it will fail immediately. In case of a immediate fail, it throws an exception.

The way it is implemented is by using a [leaky bucket algorithm](https://en.wikipedia.org/wiki/Leaky_bucket).

Here's an example:

```clojure
(require '[tank.circuit-breaker])
(defn http-request! [] ...)

(let [circuit-breaker (tank.circuit-breaker/circuit-breaker 10 100)]
  (tank.circuit-breaker/call! circuit-breaker http-request!))
```

This circuit breaker object can be shared between several calls (eg: calls to multiple endpoints from a single service can share a single circuit breaker).

## License

Copyright © 2018 Caio Oliveira

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
