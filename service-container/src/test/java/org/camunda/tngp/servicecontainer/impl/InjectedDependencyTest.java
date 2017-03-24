package org.camunda.tngp.servicecontainer.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

@SuppressWarnings("unchecked")
public class InjectedDependencyTest
{
    ControlledServiceContainer serviceContainer;

    ServiceName<Object> service1Name;
    ServiceName<Object> service2Name;

    Service<Object> mockService1;
    Service<Object> mockService2;

    Object mockService2Value;
    Object mockService1Value;

    @Before
    public void setup()
    {
        serviceContainer = new ControlledServiceContainer();
        serviceContainer.start();

        service1Name = ServiceName.newServiceName("service1", Object.class);
        service2Name = ServiceName.newServiceName("service2", Object.class);

        mockService1 = mock(Service.class);
        mockService2 = mock(Service.class);

        mockService1Value = new Object();
        when(mockService1.get()).thenReturn(mockService1Value);

        mockService2Value = new Object();
        when(mockService2.get()).thenReturn(mockService2Value);
    }

    @Test
    public void shouldInjectIfExistsBefore()
    {
        // given
        serviceContainer.createService(service2Name, mockService2)
            .install();
        serviceContainer.doWorkUntilDone();

        // when
        final Injector<Object> injector = new Injector<>();
        serviceContainer.createService(service1Name, mockService1)
            .dependency(service2Name, injector)
            .install();
        serviceContainer.doWorkUntilDone();

        // then
        assertThat(injector.getValue()).isEqualTo(mockService2Value);
    }

    @Test
    public void shouldInjectIfStartedConcurrently()
    {
        // when
        final Injector<Object> injector = new Injector<>();
        serviceContainer.createService(service1Name, mockService1)
            .dependency(service2Name, injector)
            .install();
        serviceContainer.createService(service2Name, mockService2)
            .install();
        serviceContainer.doWorkUntilDone();


        // then
        assertThat(injector.getValue()).isEqualTo(mockService2Value);
    }

    @Test
    public void shouldInjectBeforeCallingStart()
    {
        // when
        final Injector<Object> injector = mock(Injector.class);
        serviceContainer.createService(service1Name, mockService1)
            .dependency(service2Name, injector)
            .install();
        serviceContainer.createService(service2Name, mockService2)
            .install();
        serviceContainer.doWorkUntilDone();

        // then
        final InOrder inOrder = inOrder(mockService1, injector);
        inOrder.verify(injector).inject(mockService2Value);
        inOrder.verify(mockService1).start(any(ServiceStartContext.class));
    }

    @Test
    public void shouldInjectServiceTwice()
    {
        // given
        serviceContainer.createService(service2Name, mockService2)
            .install();

        // when
        final Injector<Object> injector = new Injector<>();
        final Injector<Object> anotherInjector = new  Injector<>();
        serviceContainer.createService(service1Name, mockService1)
            .dependency(service2Name, injector)
            .dependency(service2Name, anotherInjector)
            .install();
        serviceContainer.doWorkUntilDone();

        // then
        assertThat(injector.getValue()).isEqualTo(mockService2Value);
        assertThat(anotherInjector.getValue()).isEqualTo(mockService2Value);
    }


    @Test
    public void shouldUninject()
    {
        // given
        final Injector<Object> injector = new Injector<>();
        serviceContainer.createService(service1Name, mockService1)
            .dependency(service2Name, injector)
            .install();
        serviceContainer.createService(service2Name, mockService2)
            .install();
        serviceContainer.doWorkUntilDone();

        // when
        serviceContainer.removeService(service2Name);
        serviceContainer.removeService(service1Name);
        serviceContainer.doWorkUntilDone();

        // then
        assertThat(injector.getValue()).isNull();
    }

    @Test
    public void shouldUninjectAfterStop()
    {
        // given
        final Injector<Object> injector = mock(Injector.class);
        serviceContainer.createService(service1Name, mockService1)
            .dependency(service2Name, injector)
            .install();
        serviceContainer.createService(service2Name, mockService2)
            .install();
        serviceContainer.doWorkUntilDone();

        // when
        serviceContainer.removeService(service2Name);
        serviceContainer.removeService(service1Name);
        serviceContainer.doWorkUntilDone();

        // then
        final InOrder inOrder = inOrder(mockService1, injector);
        inOrder.verify(mockService1).stop(any(ServiceStopContext.class));
        inOrder.verify(injector).uninject();
    }

    @Test
    public void shouldUninjectServiceTwice()
    {
        // given
        final Injector<Object> injector = new Injector<>();
        final Injector<Object> anotherInjector = new  Injector<>();
        serviceContainer.createService(service1Name, mockService1)
            .dependency(service2Name, injector)
            .dependency(service2Name, anotherInjector)
            .install();
        serviceContainer.createService(service2Name, mockService2)
            .install();
        serviceContainer.doWorkUntilDone();

        // when
        serviceContainer.removeService(service2Name);
        serviceContainer.removeService(service1Name);
        serviceContainer.doWorkUntilDone();

        // then
        assertThat(injector.getValue()).isNull();
        assertThat(anotherInjector.getValue()).isNull();
    }

    @Test
    public void shouldHaveService()
    {
        // given
        final Injector<Object> injector = new Injector<>();
        final Injector<Object> anotherInjector = new  Injector<>();
        serviceContainer.createService(service1Name, mockService1)
            .dependency(service2Name, injector)
            .dependency(service2Name, anotherInjector)
            .install();
        serviceContainer.createService(service2Name, mockService2)
            .install();
        serviceContainer.doWorkUntilDone();

        // when + then
        assertThat(serviceContainer.hasService(service1Name)).isTrue();
        assertThat(serviceContainer.hasService(service2Name)).isTrue();
    }

    @Test
    public void shouldNotHaveService()
    {
        // when + then
        assertThat(serviceContainer.hasService(service1Name)).isFalse();
        assertThat(serviceContainer.hasService(service2Name)).isFalse();
    }

    @Test
    public void shouldNotHaveServiceAfterRemovingService()
    {
        // given
        final Injector<Object> injector = new Injector<>();
        final Injector<Object> anotherInjector = new  Injector<>();
        serviceContainer.createService(service1Name, mockService1)
            .dependency(service2Name, injector)
            .dependency(service2Name, anotherInjector)
            .install();
        serviceContainer.createService(service2Name, mockService2)
            .install();
        serviceContainer.doWorkUntilDone();

        // when
        serviceContainer.removeService(service2Name);
        serviceContainer.doWorkUntilDone();

        // when + then
        assertThat(serviceContainer.hasService(service1Name)).isFalse();
        assertThat(serviceContainer.hasService(service2Name)).isFalse();
    }

}
