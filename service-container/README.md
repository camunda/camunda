# Zeebe Service Container

Simple service container for managing dynamic services in an infrastructure system.
Attempts to solve the problem of managing a potentially large number of components that depend on each other
and are started / stopped dynamically in the simplest possible way.

Features:

* Start / stop Services
* Dependency Tracking and Injection

Heavily inspired by [jboss-msc](https://github.com/jboss-msc).

**Note**: This API is not designed to be used in a business application. You may find the API to be somewhat verbose to program to.
This is the result of a trade off. This micro library targets infrastructure systems like the camunda broker and is designed to have
a very low buy-in.
In infrastructure systems, we accept a certain level of verbosity to keep things simple and to stay in control. We cannot use
black magic like classpath scanning, proxies, AOP, byte code generation and other things that we tend to find useful
in business applications. These things usually introduce a prohibitive amount of complexity that make them unusable
for our purposes.

# Implementing a Service

A service is a POJO implementing the `Service` interface.
The `Service` interface provides the following methods:

* `start(ServiceStartContext context)`: invoked when the service is started. At this point all the
service's dependencies are guaranteed to be fulfilled.
* `stop(ServiceStopContext context)`: invoked when the service is stopped
* `get()`: returns the service object

The following is an example implementation of a thread safe count service:

```java
public class CountServiceImpl implements Service<Counter>, Counter
{
    protected final AtomicLong counter = new AtomicLong();

    @Override
    public void start(ServiceContext serviceContext)
    {
        counter.set(0);
    }

    @Override
    public void stop()
    {
        // nothing to do
    }

    @Override
    public Counter get()
    {
        return this;
    }

    public long increment()
    {
        return counter.incrementAndGet();
    }
}
```

# Installing a Service

The follwoing example shows how to install a service into the container:

```java
final ServiceContainer serviceContainer = new ServiceContainerImpl();
final CountServiceImpl requestCounter = new CountServiceImpl();
serviceContainer.createService(newServiceName("requestCounter", Counter.class), requestCounter)
  .install();
```

# Dependency Injection

The follwoing is an example of a request handling service using the count service to track the number
of handled requests:

```java
public class RequestHandlerImpl implements Service<RequestHandler>, RequestHandler
{
    protected final Injector<Counter> requestCounterInjector = new Injector<Counter>();

    protected Counter requestCounter;

    @Override
    public void start(ServiceContext serviceContext)
    {
        requestCounter = requestCounterInjector.getValue();
    }

    @Override
    public void stop()
    {
        requestCounter = null;
    }

    @Override
    public CountService get()
    {
        return this;
    }

    public void onRequest(Requset req)
    {
        requestCounter.increment();
        // ... handle the request ...
    }

    public Injector<Counter> getRequestCounterInjector()
    {
        return requestCounterInjector;
    }
}
```

When installing this service into the container, the dependency needs to be defined explicitly:

```java
final RequestHandlerImpl requestHandler = new RequestHandlerImpl();
serviceContainer.createService(newServiceName("requestHandler", RequestHandler.class), requestHandler)
  .dependency(newServiceName("requestCounter", Counter.class), requestHandler.getRequestCounterInjector())
  .install();
```

# Removing a Service

A service is removed by calling the `remove(...)` method on the service container:

```java
serviceContainer.remove(newServiceName("requestCounter", Counter.class));
```

When remoing a service, all services depending on the service, either directly or transitively, are stopped before the method
returns.

# Best Practice

In order to keep business logic testable, it is discouraged to put the actual business logic into the
class implementing the `Service` interface. Rather, you are encouraged to seperate it out into a seperate class:

```java
public class RequestHandlerService implements Service<RequestHandler>
{
    protected final Injector<Counter> requestCounterInjector = new Injector<Counter>();

    protected RequestHandler requestHandler;

    @Override
    public void start(ServiceContext serviceContext)
    {
        requestHandler = new RequestHandlerImpl(requestCounterInjector.getValue());
    }

    @Override
    public void stop()
    {
        requestHandler = null;
    }

    @Override
    public RequestHandler get()
    {
        return requesthandler
    }

    public Injector<Counter> getRequestCounterInjector()
    {
        return requestCounterInjector;
    }
}
```

In the above example, `RequestHandlerService` is just a thin wrapper around `RequestHandlerImpl` allowing it to be managed
by the service container.

RequestHandlerImpl can be unit tested without having to deal with the service container at all:

```java
public void shouldIncrementCountOnRequest()
{
    Counter counterMock = mock(Counter.class);
    RequestHandler requestHandler = new RequestHandlerIml(counterMock);

    requestHandler.handleRequest(new Request());

    verify(counterMock, times(1)).increment();
}
```

# Threading Model

The service container uses a single thread to do all it's work (like starting / stopping services), called "The Service Container Thread". When a service is started or stopped the corresponding lifecycle methods are invoked by the service container thread.

Implementations of a serice's start(), stop() methods must be non-blocking. If a start/stop method blocks on an external resource or I/O, it must do this in another thread and signal completion.

If a service needs to perform long running background work, it must schedule this work in another thread.


## Execute asynchronous Actions on start / stop

This is the simplest way of executing an asynchronous action on start / stop. The service container maintains a pool of worker threads to which actions (implementations of the `java.lang.Runnable` interface can be submitted).

A service implementation can submit an action to this thread pool by invoking the `run(...)` method from the start or stop method:

```java
public class ConfigurationService implements Service<Configuration>
{
    protected Configuration configuration;

    @Override
    public void start(ServiceStartContext ctx)
    {
        ctx.run(() ->
        {
            // read & parse XML configuration from file
            configuration = ...;

            // throwing exception keeps service from starting.
        });
    }

    ...
}
```

The service does not complete it's start phase until the provided runnable is completed. If the runnable throws an
exception, starting of the service fails.

## Controlling Completion manually

In oreder to gain complete control over the asynchronous start / stop of a service,
a CompletableFuture can be obtained from the context, by invoking the `.async()` method
on the context object. The service does not complete it's start phase until the obtained future is completed:

Note that if the obtained future is not completed (either regularly or exceptionally), the service never starts up.

```java
public class ConfigurationService implements Service<Configuration>
{
    protected Configuration configuration;

    @Override
    public void start(ServiceStartContext ctx)
    {
        final CompletableFuture<Void> startFuture = ctx.async();

        try
        {
            new Thread(() ->
            {
                try
                {
                    // read & parse XML configuration from file
                    configuration = ...;
                    startFuture.complete(null);
                }
                catch (Throwable t)
                {
                    startFuture.completeExceptionally(t);
                }
            })
            .start();
        }
        catch (Throwable t)
        {
            startFuture.completeExceptionally(t);
        }

    }
    ...
}
```

## Using asynchronous APIs

Some asynchronous APIs may return a CompletableFuture. In that case, the future can be directly supplied to the
context object. The service does not complete it's start phase until the provided future is completed.

```java
public class ConfigurationService implements Service<Configuration>
{
    protected Configuration configuration;

    @Override
    public void start(ServiceStartContext ctx)
    {
        ctx.async(asyncApi.doAsync());
    }

    ...
}
```
