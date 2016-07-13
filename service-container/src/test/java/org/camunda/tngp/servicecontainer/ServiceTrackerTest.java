package org.camunda.tngp.servicecontainer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import org.camunda.tngp.servicecontainer.impl.ServiceContainerImpl;
import org.junit.Before;
import org.junit.Test;

public class ServiceTrackerTest
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
    public void shouldInvokeTrackerOnRegistration()
    {
        // given
        final Service<Object> mockService = mock(Service.class);
        serviceContainer.createService(service1, mockService).install();

        final ServiceTracker serviceTracker = mock(ServiceTracker.class);

        // when
        serviceContainer.registerTracker(serviceTracker);

        // then
        verify(serviceTracker).onTrackerRegistration(service1, mockService);
        verifyNoMoreInteractions(serviceTracker);
    }

    @Test
    public void shouldInvokeTrackerOnServiceStart()
    {
        // given
        final ServiceTracker serviceTracker = mock(ServiceTracker.class);
        serviceContainer.registerTracker(serviceTracker);

        final Service<Object> mockService = mock(Service.class);

        // when
        serviceContainer.createService(service1, mockService).install();

        // then
        verify(serviceTracker).onServiceStarted(service1, mockService);
        verifyNoMoreInteractions(serviceTracker);
    }

    @Test
    public void shouldNotInvokeTrackerForStoppedService()
    {
        // given
        final Service<Object> mockService = mock(Service.class);
        serviceContainer.createService(service1, mockService).install();

        serviceContainer.stop();

        final ServiceTracker serviceTracker = mock(ServiceTracker.class);

        // when
        serviceContainer.registerTracker(serviceTracker);

        // then
        verifyZeroInteractions(serviceTracker);
    }

    @Test
    public void shouldNotInvokeTrackerAfterRemoval()
    {
        // given
        final Service<Object> mockService = mock(Service.class);
        final ServiceTracker serviceTracker = mock(ServiceTracker.class);

        // when
        serviceContainer.removeTracker(serviceTracker);
        serviceContainer.createService(service1, mockService).install();

        // then
        verifyZeroInteractions(serviceTracker);
    }


}
