package org.camunda.tngp.servicecontainer.impl;

import static org.mockito.Matchers.*;
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
import org.junit.Test;
import org.mockito.InOrder;

@SuppressWarnings({"unchecked"})
public class DependenciesTest
{
    ControlledServiceContainer serviceContainer;
    ServiceName<Object> service1;
    ServiceName<Object> service2;

    @Before
    public void setup()
    {
        serviceContainer = new ControlledServiceContainer();
        serviceContainer.start();
        service1 = ServiceName.newServiceName("service1", Object.class);
        service2 = ServiceName.newServiceName("service2", Object.class);
    }

    @Test
    public void shouldInstallServiceWithoutDependencies()
    {
        final Service<Object> mockService = mock(Service.class);

        final CompletableFuture<Void> serviceFuture = serviceContainer.createService(service1, mockService).install();

        serviceContainer.doWorkUntilDone();

        assertCompleted(serviceFuture);
        verify(mockService, times(1)).start(any(ServiceStartContext.class));
    }

    @Test
    public void shouldNotInstallServiceWithMissingDependencies()
    {
        final Service<Object> mockService = mock(Service.class);

        final CompletableFuture<Void> serviceFuture = serviceContainer.createService(service1, mockService)
            .dependency(service2)
            .install();

        serviceContainer.doWorkUntilDone();

        assertNotCompleted(serviceFuture);
        verify(mockService, times(0)).start(any(ServiceStartContext.class));
    }

    @Test
    public void shouldNotInstallServiceWithExistingName()
    {
        // given
        final Service<Object> mockService = mock(Service.class);
        serviceContainer.createService(service1, mockService)
            .dependency(service2)
            .install();
        serviceContainer.doWorkUntilDone();

        // when
        final CompletableFuture<Void> serviceFuture2 = serviceContainer.createService(service1, mockService)
            .dependency(service2)
            .install();
        serviceContainer.doWorkUntilDone();

        // then
        assertFailed(serviceFuture2);
    }

    @Test
    public void shouldInstallServiceWhenDependenciesAreAvailableAtStart()
    {
        final Service<Object> mockService1 = mock(Service.class);
        final Service<Object> mockService2 = mock(Service.class);

        final CompletableFuture<Void> service1Future = serviceContainer.createService(service1, mockService1)
            .install();

        serviceContainer.doWorkUntilDone();

        assertCompleted(service1Future);
        verify(mockService1, times(1)).start(any(ServiceStartContext.class));

        final CompletableFuture<Void> service2Future = serviceContainer.createService(service2, mockService2)
            .dependency(service1)
            .install();

        serviceContainer.doWorkUntilDone();

        assertCompleted(service2Future);
        verify(mockService2, times(1)).start(any(ServiceStartContext.class));
    }

    @Test
    public void shouldInstallServiceWhenDependenciesAreResolved()
    {
        final Service<Object> mockService1 = mock(Service.class);
        final Service<Object> mockService2 = mock(Service.class);

        serviceContainer.createService(service1, mockService1)
            .dependency(service2)
            .install();

        serviceContainer.doWorkUntilDone();

        verify(mockService1, times(0)).start(any(ServiceStartContext.class));

        serviceContainer.createService(service2, mockService2)
            .install();

        serviceContainer.doWorkUntilDone();

        final InOrder inOrder = inOrder(mockService1, mockService2);

        inOrder.verify(mockService2, times(1)).start(any(ServiceStartContext.class));
        inOrder.verify(mockService1, times(1)).start(any(ServiceStartContext.class));
    }

    @Test
    public void shouldRemoveServiceWithoutDependencies()
    {
        final Service<Object> mockService = mock(Service.class);

        // given
        serviceContainer.createService(service1, mockService).install();
        serviceContainer.doWorkUntilDone();

        // when
        final CompletableFuture<Void> removeFuture = serviceContainer.removeService(service1);
        serviceContainer.doWorkUntilDone();

        // then
        assertCompleted(removeFuture);
        verify(mockService, times(1)).stop(any(ServiceStopContext.class));
    }

    @Test
    public void shouldStopDependentServicesFirst()
    {
        final Service<Object> mockService1 = mock(Service.class);
        final Service<Object> mockService2 = mock(Service.class);

        // given
        serviceContainer.createService(service1, mockService1)
            .dependency(service2)
            .install();
        serviceContainer.createService(service2, mockService2)
            .install();
        serviceContainer.doWorkUntilDone();

        // when
        final CompletableFuture<Void> closeFuture = serviceContainer.removeService(service2);
        serviceContainer.doWorkUntilDone();

        // then
        assertCompleted(closeFuture);
        final InOrder inOrder = inOrder(mockService1, mockService2);
        inOrder.verify(mockService1, times(1)).stop(any(ServiceStopContext.class));
        inOrder.verify(mockService2, times(1)).stop(any(ServiceStopContext.class));
    }

    @Test
    public void shouldStopAllServicesWhenStoppingContainer()
    {
        final Service<Object> mockService1 = mock(Service.class);
        final Service<Object> mockService2 = mock(Service.class);

        // given
        serviceContainer.createService(service1, mockService1)
            .install();
        serviceContainer.createService(service2, mockService2)
            .dependency(service1)
            .install();
        serviceContainer.doWorkUntilDone();

        // when
        final CompletableFuture<Void> future = serviceContainer.closeAsync();
        serviceContainer.doWorkUntilDone();

        // then
        assertCompleted(future);
        final InOrder inOrder = inOrder(mockService1, mockService2);
        inOrder.verify(mockService2, times(1)).stop(any(ServiceStopContext.class));
        inOrder.verify(mockService1, times(1)).stop(any(ServiceStopContext.class));
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
        catch (Throwable t) {
            LangUtil.rethrowUnchecked(t);
        }
    }

    protected void assertFailed(CompletableFuture<Void> serviceFuture)
    {
        // NOTE: since the future is of type Void, we cannot use "isCompleted()" since that performed a null check -_-
        try
        {
            serviceFuture.get(1, TimeUnit.NANOSECONDS);
            Assert.fail("Exception expected");
        }
        catch (TimeoutException | InterruptedException t)
        {
            LangUtil.rethrowUnchecked(t);
        }
        catch (ExecutionException t) {
            // expected
        }
    }
}
