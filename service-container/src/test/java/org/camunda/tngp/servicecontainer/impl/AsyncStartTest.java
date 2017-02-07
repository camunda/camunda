package org.camunda.tngp.servicecontainer.impl;

import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.agrona.LangUtil;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class AsyncStartTest
{
    ControlledServiceContainer serviceContainer;

    ServiceName<Object> service1Name;
    ServiceName<Object> service2Name;

    @Before
    public void setup()
    {
        serviceContainer = new ControlledServiceContainer();
        serviceContainer.start();

        service1Name = ServiceName.newServiceName("service1", Object.class);
        service2Name = ServiceName.newServiceName("service2", Object.class);
    }

    @Test
    public void shouldWaitForAsyncStart()
    {
        // when
        final AsyncStartService service = new AsyncStartService();

        final CompletableFuture<Void> startFuture = serviceContainer.createService(service1Name, service)
            .install();
        serviceContainer.doWorkUntilDone();

        // then
        assertNotCompleted(startFuture);
    }

    @Test
    public void shouldContinueOnAsyncStartComplete()
    {
        // given
        final AsyncStartService service = new AsyncStartService();

        final CompletableFuture<Void> startFuture = serviceContainer.createService(service1Name, service)
            .install();
        serviceContainer.doWorkUntilDone();

        // when
        service.future.complete(null);
        serviceContainer.doWorkUntilDone();

        // then
        assertCompleted(startFuture);
    }

    @Test
    @Ignore
    public void shouldContinueOnAsyncStartCompletedExceptionally()
    {
        // given
        final AsyncStartService service = new AsyncStartService();

        final CompletableFuture<Void> startFuture = serviceContainer.createService(service1Name, service)
            .install();
        serviceContainer.doWorkUntilDone();

        // when
        service.future.completeExceptionally(new RuntimeException());
        serviceContainer.doWorkUntilDone();

        // then
        assertFailed(startFuture);
    }

    @Test
    public void shouldWaitOnSuppliedFuture()
    {
        // when
        final AsyncStartService service = new AsyncStartService();
        service.future = new CompletableFuture<>();

        final CompletableFuture<Void> startFuture = serviceContainer.createService(service1Name, service)
            .install();
        serviceContainer.doWorkUntilDone();

        // then
        assertNotCompleted(startFuture);
    }

    @Test
    public void shouldWaitForAction()
    {
        // when
        final AsyncStartService service = new AsyncStartService();
        final Runnable mockAction = mock(Runnable.class);
        service.action = mockAction;

        final CompletableFuture<Void> startFuture = serviceContainer.createService(service1Name, service)
            .install();
        serviceContainer.doWorkUntilDone();

        // then
        assertNotCompleted(startFuture);
    }

    @Test
    public void shouldContinueOnAction()
    {
        // given
        final AsyncStartService service = new AsyncStartService();
        final Runnable mockAction = mock(Runnable.class);
        service.action = mockAction;

        final CompletableFuture<Void> startFuture = serviceContainer.createService(service1Name, service)
            .install();
        serviceContainer.doWorkUntilDone();

        // when
        serviceContainer.executeAsyncActions();
        verify(mockAction).run();
        serviceContainer.doWorkUntilDone();

        // then
        assertCompleted(startFuture);
    }

    @Test
    @Ignore
    public void shouldStopOnExceptionFromAction()
    {
        // given
        final AsyncStartService service = new AsyncStartService();
        final Runnable mockAction = mock(Runnable.class);

        doThrow(new RuntimeException()).when(mockAction).run();

        service.action = mockAction;

        final CompletableFuture<Void> startFuture = serviceContainer.createService(service1Name, service)
            .install();
        serviceContainer.doWorkUntilDone();

        // when
        serviceContainer.executeAsyncActions();
        serviceContainer.doWorkUntilDone();

        // then
        assertFailed(startFuture);
        verify(mockAction).run();
    }

    @Test
    public void shouldContineOnSuppliedFuture()
    {
        // given
        final AsyncStartService service = new AsyncStartService();
        service.future = new CompletableFuture<>();

        final CompletableFuture<Void> startFuture = serviceContainer.createService(service1Name, service)
            .install();
        serviceContainer.doWorkUntilDone();

        // when
        service.future.complete(null);
        serviceContainer.doWorkUntilDone();

        // then
        assertCompleted(startFuture);
    }

    @Test
    @Ignore
    public void shouldFailOnSuppliedFutureCompletedExceptionally()
    {
        // given
        final AsyncStartService service = new AsyncStartService();
        service.future = new CompletableFuture<>();

        final CompletableFuture<Void> startFuture = serviceContainer.createService(service1Name, service)
            .install();
        serviceContainer.doWorkUntilDone();

        // when
        service.future.completeExceptionally(new Throwable());
        serviceContainer.doWorkUntilDone();

        // then
        assertFailed(startFuture);
    }

    @Test
    public void shouldWaitOnConcurrentStart()
    {
        // when
        final AsyncStartService service = new AsyncStartService();

        final CompletableFuture<Void> service1StartFuture = serviceContainer.createService(service1Name, service)
                .install();
        final CompletableFuture<Void> service2StartFuture = serviceContainer.createService(service2Name, mock(Service.class))
                .dependency(service1Name)
                .install();
        serviceContainer.doWorkUntilDone();

        // then
        assertNotCompleted(service1StartFuture);
        assertNotCompleted(service2StartFuture);
    }

    @Test
    public void shouldContinueConcurrentStart()
    {
        // given
        final AsyncStartService service = new AsyncStartService();

        final CompletableFuture<Void> service1StartFuture = serviceContainer.createService(service1Name, service)
                .install();
        final CompletableFuture<Void> service2StartFuture = serviceContainer.createService(service2Name, mock(Service.class))
                .dependency(service1Name)
                .install();
        serviceContainer.doWorkUntilDone();

        // when
        service.future.complete(null);
        serviceContainer.doWorkUntilDone();

        // then
        assertCompleted(service1StartFuture);
        assertCompleted(service2StartFuture);
    }

    @Test
    @Ignore
    public void shouldFailConcurrentStart()
    {
        // given
        final AsyncStartService service = new AsyncStartService();

        final CompletableFuture<Void> service1StartFuture = serviceContainer.createService(service1Name, service)
                .install();
        final CompletableFuture<Void> service2StartFuture = serviceContainer.createService(service2Name, mock(Service.class))
                .dependency(service1Name)
                .install();
        serviceContainer.doWorkUntilDone();

        // when
        service.future.completeExceptionally(new Throwable());
        serviceContainer.doWorkUntilDone();

        // then
        assertFailed(service1StartFuture);
        assertFailed(service2StartFuture);
    }


    static class AsyncStartService implements Service<Object>
    {
        CompletableFuture<Void> future;
        Object value = new Object();
        Runnable action;

        @Override
        public void start(ServiceStartContext startContext)
        {
            if (action != null)
            {
                startContext.run(action);
            }
            else if(future != null)
            {
                startContext.async(future);
            }
            else
            {
                future = startContext.async();
            }
        }

        @Override
        public void stop(ServiceStopContext stopContext)
        {

        }

        @Override
        public Object get()
        {
            return value;
        }
    }

    protected void assertCompleted(CompletableFuture<Void> serviceFuture)
    {
        // NOTE: since the future is of type Void, we cannot use "isCompleted()" since that performed a null check -_-
        try
        {
            serviceFuture.get(1, TimeUnit.NANOSECONDS);
        }
        catch (Throwable t)
        {
            LangUtil.rethrowUnchecked(t);
        }
    }

    protected void assertNotCompleted(CompletableFuture<Void> serviceFuture)
    {
        // NOTE: since the future is of type Void, we cannot use "isCompleted()" since that performed a null check -_-
        try
        {
            serviceFuture.get(1, TimeUnit.NANOSECONDS);
            Assert.fail("Exception expected");
        }
        catch (TimeoutException t)
        {
            // expected
        }
        catch (Throwable t)
        {
            LangUtil.rethrowUnchecked(t);
        }
    }

    protected void assertFailed(CompletableFuture<Void> serviceFuture)
    {
        try
        {
            serviceFuture.get(1, TimeUnit.NANOSECONDS);
            Assert.fail("Exception expected");
        }
        catch (ExecutionException e)
        {
            // expected
        }
        catch (Throwable t)
        {
            LangUtil.rethrowUnchecked(t);
        }
    }

}
