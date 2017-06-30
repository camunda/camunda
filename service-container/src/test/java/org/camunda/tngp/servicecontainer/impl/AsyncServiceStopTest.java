package org.camunda.tngp.servicecontainer.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class AsyncServiceStopTest
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
    public void shouldWaitForAsyncStop()
    {
        // given
        final AsyncStopService service = new AsyncStopService();

        serviceContainer.createService(service1Name, service)
            .install();
        serviceContainer.doWorkUntilDone();

        // when
        final CompletableFuture<Void> removeFuture = serviceContainer.removeService(service1Name);
        serviceContainer.doWorkUntilDone();

        // then
        assertNotCompleted(removeFuture);
    }

    @Test
    public void shouldContinueOnAsyncStopComplete()
    {
        // given
        final AsyncStopService service = new AsyncStopService();

        serviceContainer.createService(service1Name, service)
            .install();
        serviceContainer.doWorkUntilDone();

        final CompletableFuture<Void> removeFuture = serviceContainer.removeService(service1Name);
        serviceContainer.doWorkUntilDone();

        // when
        service.future.complete(null);
        serviceContainer.doWorkUntilDone();

        // then
        assertCompleted(removeFuture);
    }

    @Test
    public void shouldWaitOnSuppliedFuture()
    {
        // given
        final AsyncStopService service = new AsyncStopService();
        service.future = new CompletableFuture<>();

        serviceContainer.createService(service1Name, service)
            .install();
        serviceContainer.doWorkUntilDone();

        // when
        final CompletableFuture<Void> removeFuture = serviceContainer.removeService(service1Name);
        serviceContainer.doWorkUntilDone();

        // then
        assertNotCompleted(removeFuture);
    }

    @Test
    public void shouldContineOnSuppliedFuture()
    {
        // given
        final AsyncStopService service = new AsyncStopService();
        service.future = new CompletableFuture<>();

        serviceContainer.createService(service1Name, service)
            .install();
        serviceContainer.doWorkUntilDone();

        final CompletableFuture<Void> removeFuture = serviceContainer.removeService(service1Name);
        serviceContainer.doWorkUntilDone();

        // when
        service.future.complete(null);
        serviceContainer.doWorkUntilDone();

        // then
        assertCompleted(removeFuture);
    }

    @Test
    public void shouldContineOnSuppliedFutureCompletedExceptionally()
    {
        // given
        final AsyncStopService service = new AsyncStopService();
        service.future = new CompletableFuture<>();

        serviceContainer.createService(service1Name, service)
            .install();
        serviceContainer.doWorkUntilDone();

        final CompletableFuture<Void> removeFuture = serviceContainer.removeService(service1Name);
        serviceContainer.doWorkUntilDone();

        // when
        service.future.completeExceptionally(new RuntimeException());
        serviceContainer.doWorkUntilDone();

        // then
        assertCompleted(removeFuture);
    }


    @Test
    public void shouldWaitForAction()
    {
        // given
        final AsyncStopService service = new AsyncStopService();
        final Runnable mockAction = mock(Runnable.class);
        service.action = mockAction;

        serviceContainer.createService(service1Name, service)
            .install();
        serviceContainer.doWorkUntilDone();

        // when
        final CompletableFuture<Void> removeFuture = serviceContainer.removeService(service1Name);
        serviceContainer.doWorkUntilDone();

        // then
        assertNotCompleted(removeFuture);
    }

    @Test
    public void shouldContinueOnAction()
    {
        // given
        final AsyncStopService service = new AsyncStopService();
        final Runnable mockAction = mock(Runnable.class);
        service.action = mockAction;

        serviceContainer.createService(service1Name, service)
            .install();
        serviceContainer.doWorkUntilDone();

        // when
        final CompletableFuture<Void> removeFuture = serviceContainer.removeService(service1Name);
        serviceContainer.doWorkUntilDone();

        // when
        serviceContainer.executeAsyncActions();
        verify(mockAction).run();
        serviceContainer.doWorkUntilDone();

        // then
        assertCompleted(removeFuture);
    }

    @Test
    public void shouldContinueOnExceptionFromAction()
    {
        // given
        final AsyncStopService service = new AsyncStopService();
        final Runnable mockAction = mock(Runnable.class);
        service.action = mockAction;

        doThrow(new RuntimeException()).when(mockAction).run();

        serviceContainer.createService(service1Name, service)
            .install();
        serviceContainer.doWorkUntilDone();

        // when
        final CompletableFuture<Void> removeFuture = serviceContainer.removeService(service1Name);
        serviceContainer.doWorkUntilDone();

        // when
        serviceContainer.executeAsyncActions();
        verify(mockAction).run();
        serviceContainer.doWorkUntilDone();

        // then
        assertCompleted(removeFuture);
    }

    @Test
    public void shouldWaitOnConcurrentStop()
    {
        // given
        final AsyncStopService service = new AsyncStopService();

        serviceContainer.createService(service1Name, service)
                .dependency(service2Name)
                .install();
        serviceContainer.createService(service2Name, mock(Service.class))
                .install();
        serviceContainer.doWorkUntilDone();

        // when
        final CompletableFuture<Void> service1RemoveFuture = serviceContainer.removeService(service1Name);
        final CompletableFuture<Void> service2RemoveFuture = serviceContainer.removeService(service2Name);
        serviceContainer.doWorkUntilDone();

        // then
        assertNotCompleted(service1RemoveFuture);
        assertNotCompleted(service2RemoveFuture);
    }

    @Test
    public void shouldContinueConcurrentStop()
    {
        // given
        final AsyncStopService service = new AsyncStopService();

        serviceContainer.createService(service1Name, service)
                .dependency(service2Name)
                .install();
        serviceContainer.createService(service2Name, mock(Service.class))
                .install();
        serviceContainer.doWorkUntilDone();

        final CompletableFuture<Void> service1RemoveFuture = serviceContainer.removeService(service1Name);
        final CompletableFuture<Void> service2RemoveFuture = serviceContainer.removeService(service2Name);
        serviceContainer.doWorkUntilDone();

        // when
        service.future.complete(null);
        serviceContainer.doWorkUntilDone();

        // then
        assertCompleted(service1RemoveFuture);
        assertCompleted(service2RemoveFuture);
    }


    static class AsyncStopService implements Service<Object>
    {
        CompletableFuture<Void> future;
        Object value = new Object();
        Runnable action;

        @Override
        public void start(ServiceStartContext startContext)
        {
        }

        @Override
        public void stop(ServiceStopContext stopContext)
        {
            if (action != null)
            {
                stopContext.run(action);
            }
            else if (future != null)
            {
                stopContext.async(future);
            }
            else
            {
                future = stopContext.async();
            }
        }

        @Override
        public Object get()
        {
            return value;
        }

    }

    protected void assertCompleted(CompletableFuture<Void> serviceFuture)
    {
        assertThat(serviceFuture).isCompleted();
    }

    protected void assertNotCompleted(CompletableFuture<Void> serviceFuture)
    {
        assertThat(serviceFuture).isNotCompleted();
    }
}
