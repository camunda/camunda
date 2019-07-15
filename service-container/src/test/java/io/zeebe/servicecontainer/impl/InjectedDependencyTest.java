/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.servicecontainer.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;

@SuppressWarnings("unchecked")
public class InjectedDependencyTest {
  @Rule public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();

  ServiceContainer serviceContainer;

  ServiceName<Object> service1Name;
  ServiceName<Object> service2Name;

  Service<Object> mockService1;
  Service<Object> mockService2;

  Object mockService2Value;
  Object mockService1Value;

  @Before
  public void setup() {
    serviceContainer = new ServiceContainerImpl(actorSchedulerRule.get());
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
  public void shouldInjectIfExistsBefore() {
    // given
    serviceContainer.createService(service2Name, mockService2).install();

    // when
    final Injector<Object> injector = new Injector<>();
    serviceContainer
        .createService(service1Name, mockService1)
        .dependency(service2Name, injector)
        .install()
        .join();

    // then
    assertThat(injector.getValue()).isEqualTo(mockService2Value);
  }

  @Test
  public void shouldInjectIfStartedConcurrently() {
    // when
    final Injector<Object> injector = new Injector<>();
    final ActorFuture<Object> install =
        serviceContainer
            .createService(service1Name, mockService1)
            .dependency(service2Name, injector)
            .install();
    serviceContainer.createService(service2Name, mockService2).install().join();
    install.join();

    // then
    assertThat(injector.getValue()).isEqualTo(mockService2Value);
  }

  @Test
  public void shouldInjectBeforeCallingStart() {
    // when
    final Injector<Object> injector = mock(Injector.class);
    final ActorFuture<Object> install =
        serviceContainer
            .createService(service1Name, mockService1)
            .dependency(service2Name, injector)
            .install();
    serviceContainer.createService(service2Name, mockService2).install().join();
    install.join();

    // then
    final InOrder inOrder = inOrder(mockService1, injector);
    inOrder.verify(injector).inject(mockService2Value);
    inOrder.verify(mockService1).start(any(ServiceStartContext.class));
  }

  @Test
  public void shouldInjectServiceTwice() {
    // given
    serviceContainer.createService(service2Name, mockService2).install().join();

    // when
    final Injector<Object> injector = new Injector<>();
    final Injector<Object> anotherInjector = new Injector<>();
    serviceContainer
        .createService(service1Name, mockService1)
        .dependency(service2Name, injector)
        .dependency(service2Name, anotherInjector)
        .install()
        .join();

    // then
    assertThat(injector.getValue()).isEqualTo(mockService2Value);
    assertThat(anotherInjector.getValue()).isEqualTo(mockService2Value);
  }

  @Test
  public void shouldUninject() {
    // given
    final Injector<Object> injector = new Injector<>();
    serviceContainer.createService(service2Name, mockService2).install();
    serviceContainer
        .createService(service1Name, mockService1)
        .dependency(service2Name, injector)
        .install();

    // when
    serviceContainer.removeService(service1Name);

    // then
    assertThat(injector.getValue()).isNull();
  }

  @Test
  public void shouldUninjectAfterStop() {
    // given
    final Injector<Object> injector = mock(Injector.class);
    serviceContainer
        .createService(service1Name, mockService1)
        .dependency(service2Name, injector)
        .install();
    serviceContainer.createService(service2Name, mockService2).install().join();

    // when
    serviceContainer.removeService(service1Name).join();

    // then
    final InOrder inOrder = inOrder(mockService1, injector);
    inOrder.verify(mockService1).stop(any(ServiceStopContext.class));
    inOrder.verify(injector).uninject();
  }

  @Test
  public void shouldUninjectServiceTwice() {
    // given
    final Injector<Object> injector = new Injector<>();
    final Injector<Object> anotherInjector = new Injector<>();
    serviceContainer
        .createService(service1Name, mockService1)
        .dependency(service2Name, injector)
        .dependency(service2Name, anotherInjector)
        .install();
    serviceContainer.createService(service2Name, mockService2).install();

    // when
    serviceContainer.removeService(service1Name);

    // then
    assertThat(injector.getValue()).isNull();
    assertThat(anotherInjector.getValue()).isNull();
  }

  @Test
  public void shouldHaveService() {
    // given
    final Injector<Object> injector = new Injector<>();
    final Injector<Object> anotherInjector = new Injector<>();
    serviceContainer
        .createService(service1Name, mockService1)
        .dependency(service2Name, injector)
        .dependency(service2Name, anotherInjector)
        .install();
    serviceContainer.createService(service2Name, mockService2).install().join();

    // when + then
    assertThat(serviceContainer.hasService(service1Name).join()).isTrue();
    assertThat(serviceContainer.hasService(service2Name).join()).isTrue();
  }

  @Test
  public void shouldNotHaveService() {
    // when + then
    assertThat(serviceContainer.hasService(service1Name).join()).isFalse();
    assertThat(serviceContainer.hasService(service2Name).join()).isFalse();
  }
}
