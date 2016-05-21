Simple service container for managing dynamic services in an infrastructure system.
Attempts to solve the problem of managing a potentially large number of components that depend on each other
and are started / stopped dynamically in the simplest possible way.

Features:

* Start / stop Services
* Dependency Tracking and Injection
* Service Lifecycle Listeners

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

* `start(ServiceContext context)`: invoked when the service is started. At this point all the
service's dependencies are guaranteed to be fulfilled.
* `stop()`: invoked when the service is stopped
* `get()`: retuns the service object

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






