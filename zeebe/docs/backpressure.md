# Backpressure

This is a description of Backpressure and how it works in Zeebe.

In Zeebe Commands get send to a broker. The broker handles these commands and sends a response
accordingly. It could occur that the broker will receive more commands than it can handle. This is
where backpressure comes into play.

In general there are two ways to deal with this:

1. **Buffering** - The broker could keep a buffer of the incoming requests and process them over
   time. Buffering comes with a risk. If the rate of incoming requests doesn't slow down the buffer
   will keep growing, taking up memory space.
2. **Dropping** - The broker could drop incoming requests. The downside of this is that the client
   will have to send the request a second time.

In Zeebe we have chosen to go with the second method of dropping requests when the broker is getting
swarmed with requests.

## Configuration

Zeebe provides a few configuration options that influence backpressure.

1. **enabled** - Used to enable/disable backpressure. By default, is set to `true`.
   Can be overridden with the environment variable `ZEEBE_BROKER_BACKPRESSURE_ENABLED`.
2. **useWindowed** - Calculate the request limit before backpressure occurs using a time window.
   By default, this is set to `true`. Can be overridden with the environment
   variable `ZEEBE_BROKER_BACKPRESSURE_USEWINDOWED`.
3. **algorithm** - Specifies the algorithm that is used to calculate the limit of requests Zeebe can
   handle. By default, is set to `vegas`.
   Can be overridden with the environment variable `ZEEBE_BROKER_BACKPRESSURE_ALGORITHM`
   Must be one of the following:

- vegas
- aimd
- fixed
- gradient
- gradient2

## How does it work?

When Zeebe is started one of the components that gets started is the `CommandApiService`. This
service will create a `PartitionAwareRequestLimiter`. This could either be a `NoopRequestLimiter`
when backpressure is disabled, or a `CommandRateLimiter` when backpressure is enabled.

This limiter is used each time a new request is received. The `CommandApiRequestHandler`(responsible
for writing the command to the stream) will receive the request. Before writing the command it will
check with the limiter if the limit has been reached. If this is the case a resource exhausted error
response is returned to the client.

## Limiter

We've seen that the limiter is used to determine whether a command gets written to the stream or
not. How does the limiter decide this? On a high-level this is fairly simple. Two pieces of
information are required here. The number of in-flight requests, and the limit.

### In-flight

The limiter keeps track of the amount of requests that are in-flight. An in-flight request is a
request for which no response has been sent. This number is incremented by using the `tryAcquire`
method on the limiter. This method returns a boolean. If the number of in-flight requests surpasses
the limit this method will return false (backpressure), else it will return true.

Of course this number of in-flight requests needs to be decremented as well. The `tryAcquire` method
does something else. It will register a response listener. When a response is sent, this listener
will make sure the number of in-flight requests gets decremented again.

### Limit

The limit refers to the maximum amount of in-flight requests allowed. When the number of in-flight
requests passes this limit backpressure is applied. This limit gets calculated by the configured
algorithm. When a response listener is triggered it will give a "sample" to the limit algorithm.
Using these samples the algorithm can calculate a sensible limit.

For more information about the different algorithms refer to the
[Camunda Platform 8 Docs](https://docs.camunda.io/docs/self-managed/zeebe-deployment/operations/backpressure/#backpressure-algorithms)
.

### White-listing

When a broker is backpressuring it will reject all commands. This could fully block a cluster.
Imagine a loop in a process instance that keeps spamming new commands. The number of requests will
not reduce, until this instance has been stopped. Cancelling a process instance is done using a
cancel command. This command wouldn't get process because of backpressure. To circumvent these
situations a whitelisting mechanism has been implemented.

The `CommandRateLimiter` contains a set of intents that are whitelisted. When a command is received
with one of these intents the command gets written, regardless of the number of in-flight requests
and the limit. This makes sure that the cluster can still make progress, and any loops can still get
cancelled. The intents that are whitelisted are:

- JobIntent.COMPLETE
- JobIntent.FAIL
- ProcessInstanceIntent.CANCEL
- DeploymentIntent.CREATE
- DeploymentIntent.DISTRIBUTE
- DeploymentDistributionIntent.COMPLETE

### NoopRequestLimiter

When backpressure is disabled Zeebe uses the `NoopRequestLimiter` instead of
the `CommandRateLimiter`. This limiter only pretends to be a limiter. In reality, it will allow all
commands. No limit is calculated and it will not keep track of any in-flight requests.
