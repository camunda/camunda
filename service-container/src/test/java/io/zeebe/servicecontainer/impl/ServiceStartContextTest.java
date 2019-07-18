/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.servicecontainer.impl;

import static io.zeebe.servicecontainer.impl.ActorFutureAssertions.assertCompleted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

@SuppressWarnings("unchecked")
public class ServiceStartContextTest {
  @Rule public ControlledActorSchedulerRule actorSchedulerRule = new ControlledActorSchedulerRule();
  @Rule public ExpectedException thrown = ExpectedException.none();
  private ServiceContainer serviceContainer;
  private ServiceName<Object> service1;
  private ServiceName<Object> service2;

  @Before
  public void setup() {
    serviceContainer = new ServiceContainerImpl(actorSchedulerRule.get());
    serviceContainer.start();

    service1 = ServiceName.newServiceName("service1", Object.class);
    service2 = ServiceName.newServiceName("service2", Object.class);
  }

  @Test
  public void shouldCreateService() {
    final MockService mockService1 = new MockService();
    final Service<Object> mockService2 = mock(Service.class);

    final ActorFuture<Object> service1Future =
        serviceContainer.createService(service1, mockService1).install();

    actorSchedulerRule.workUntilDone();
    assertCompleted(service1Future);

    final ActorFuture<Object> service2Future =
        mockService1.startContext.createService(service2, mockService2).install();

    actorSchedulerRule.workUntilDone();
    assertCompleted(service2Future);

    verify(mockService2, times(1)).start(any(ServiceStartContext.class));
  }

  @Test
  public void shouldRemoveService() {
    final MockService mockService1 = new MockService();
    final Service<Object> mockService2 = mock(Service.class);

    serviceContainer.createService(service1, mockService1).install();
    actorSchedulerRule.workUntilDone();

    mockService1.startContext.createService(service2, mockService2).install();
    actorSchedulerRule.workUntilDone();

    final ActorFuture<Void> service2Future = mockService1.startContext.removeService(service2);

    actorSchedulerRule.workUntilDone();
    assertCompleted(service2Future);

    verify(mockService2, times(1)).stop(any(ServiceStopContext.class));
  }

  @Test
  public void shouldReinstallService() {
    final MockService mockService1 = new MockService();
    final Service<Object> mockService2 = mock(Service.class);

    serviceContainer.createService(service1, mockService1).install();
    actorSchedulerRule.workUntilDone();
    mockService1.startContext.createService(service2, mockService2).install();
    actorSchedulerRule.workUntilDone();
    mockService1.startContext.removeService(service2);
    actorSchedulerRule.workUntilDone();

    mockService1.startContext.createService(service2, mockService2).install();
    actorSchedulerRule.workUntilDone();
  }

  @Test
  public void shouldNotFailOnRemoveServiceIfNotExist() {
    final MockService mockService1 = new MockService();

    serviceContainer.createService(service1, mockService1).install();
    actorSchedulerRule.workUntilDone();

    final ActorFuture<Void> removeService = mockService1.startContext.removeService(service2);
    actorSchedulerRule.workUntilDone();

    removeService.join();
  }

  /**
   * Allows services to schedule their own tasks on the same scheduler. This is a convenience method
   * to avoid carrying the actor scheduler around outside of the service container.
   */
  @Test
  public void shouldProvideActorSchedulerInStartContext() {
    // given
    final MockService service = new MockService();

    // when
    serviceContainer.createService(service1, service).install();
    actorSchedulerRule.workUntilDone();

    // then
    final ActorScheduler providedScheduler = service.startContext.getScheduler();
    assertThat(providedScheduler).isSameAs(actorSchedulerRule.get());
  }

  @Test
  public void shouldProvideServiceName() {
    final MockService service = new MockService();

    serviceContainer.createService(service1, service).install();
    actorSchedulerRule.workUntilDone();

    assertThat(service1).isEqualTo(service.startContext.getServiceName());
  }

  class MockService implements Service<Object> {
    ServiceStartContext startContext;

    @Override
    public void start(ServiceStartContext startContext) {
      this.startContext = startContext;
    }

    @Override
    public void stop(ServiceStopContext stopContext) {}

    @Override
    public Object get() {
      return "mock service";
    }
  }
}
