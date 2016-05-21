package org.camunda.tngp.servicecontainer;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.*;

import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContainer;
import org.camunda.tngp.servicecontainer.ServiceContext;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.impl.ServiceContainerImpl;

import static org.assertj.core.api.Assertions.*;

@SuppressWarnings({"unchecked"})
public class ServiceContainerTest
{

    ServiceContainer serviceContainer;
    ServiceName<Object> service1;
    ServiceName<Object> service2;

    @Before
    public void setup()
    {
        serviceContainer = new ServiceContainerImpl();
        service1 = ServiceName.newServiceName("service1", Object.class);
        service2 = ServiceName.newServiceName("service2", Object.class);
    }

    @Test
    public void shouldInstallServiceWithoutDependencies()
    {
        final Service<Object> mockService = mock(Service.class);

        serviceContainer.createService(service1, mockService).install();

        verify(mockService, times(1)).start(any(ServiceContext.class));
    }

    @Test
    public void shouldNotInstallServiceWithMissingDependencies()
    {
        final Service<Object> mockService = mock(Service.class);

        serviceContainer.createService(service1, mockService)
            .dependency(service2)
            .install();

        verify(mockService, times(0)).start(any(ServiceContext.class));
    }

    @Test
    public void shouldInstallServiceWhenDependenciesAreAvailableAtStart()
    {
        final Service<Object> mockService1 = mock(Service.class);
        final Service<Object> mockService2 = mock(Service.class);

        serviceContainer.createService(service1, mockService1)
            .install();

        verify(mockService1, times(1)).start(any(ServiceContext.class));

        serviceContainer.createService(service2, mockService2)
            .dependency(service1)
            .install();

        verify(mockService2, times(1)).start(any(ServiceContext.class));
    }

    @Test
    public void shouldInstallServiceWhenDependenciesAreResolved()
    {
        final Service<Object> mockService1 = mock(Service.class);
        final Service<Object> mockService2 = mock(Service.class);

        serviceContainer.createService(service1, mockService1)
            .dependency(service2)
            .install();

        verify(mockService1, times(0)).start(any(ServiceContext.class));

        serviceContainer.createService(service2, mockService2)
            .install();

        final InOrder inOrder = inOrder(mockService1, mockService2);

        inOrder.verify(mockService2, times(1)).start(any(ServiceContext.class));
        inOrder.verify(mockService1, times(1)).start(any(ServiceContext.class));
    }

    @Test
    public void shouldRemoveServiceWithoutDependencies()
    {
        final Service<Object> mockService = mock(Service.class);

        serviceContainer.createService(service1, mockService).install();

        serviceContainer.remove(service1);

        verify(mockService, times(1)).stop();
    }

    @Test
    public void shouldStopDependentServicesFirst()
    {
        final Service<Object> mockService1 = mock(Service.class);
        final Service<Object> mockService2 = mock(Service.class);

        serviceContainer.createService(service1, mockService1)
            .dependency(service2)
            .install();

        serviceContainer.createService(service2, mockService2)
            .install();

        serviceContainer.remove(service2);

        final InOrder inOrder = inOrder(mockService1, mockService2);
        inOrder.verify(mockService1, times(1)).stop();
        inOrder.verify(mockService2, times(1)).stop();
    }

    @Test
    public void shouldRestartService()
    {
        final Service<Object> mockService1 = mock(Service.class);
        final Service<Object> mockService2 = mock(Service.class);

        serviceContainer.createService(service1, mockService1)
            .dependency(service2)
            .install();

        serviceContainer.createService(service2, mockService2)
            .install();

        serviceContainer.remove(service2);

        final InOrder inOrder = inOrder(mockService1, mockService2);
        inOrder.verify(mockService1, times(1)).stop();
        inOrder.verify(mockService2, times(1)).stop();

        serviceContainer.createService(service2, mockService2)
            .install();

        verify(mockService1, times(2)).start(any(ServiceContext.class));
        verify(mockService2, times(2)).start(any(ServiceContext.class));

    }

    @Test
    public void shouldDetectCircularDependencies()
    {
        final Service<Object> mockService1 = mock(Service.class);
        final Service<Object> mockService2 = mock(Service.class);

        serviceContainer.createService(service1, mockService1)
            .dependency(service2)
            .install();

        try
        {
            serviceContainer.createService(service2, mockService2)
                .dependency(service1)
                .install();
            fail("Exception expected");
        }
        catch(RuntimeException e)
        {
            assertThat(e).hasMessageContaining("Circular dependency");
        }
    }

    @Test
    public void shouldStopAllServicesWhenStoppingContainer()
    {
        final Service<Object> mockService1 = mock(Service.class);
        final Service<Object> mockService2 = mock(Service.class);

        serviceContainer.createService(service1, mockService1)
            .install();

        serviceContainer.createService(service2, mockService2)
            .dependency(service1)
            .install();

        serviceContainer.stop();

        final InOrder inOrder = inOrder(mockService1, mockService2);
        inOrder.verify(mockService2, times(1)).stop();
        inOrder.verify(mockService1, times(1)).stop();
    }
}
