/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.servicecontainer.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import io.zeebe.servicecontainer.CompositeServiceBuilder;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.mockito.InOrder;

@SuppressWarnings("unchecked")
public class CompositeInstallOperationTest {
  public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();
  public ServiceContainerRule serviceContainerRule = new ServiceContainerRule(actorSchedulerRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(actorSchedulerRule).around(serviceContainerRule);

  ServiceName<Void> compositeName = ServiceName.newServiceName("composite", Void.class);
  ServiceName<Object> service1Name = ServiceName.newServiceName("service1", Object.class);
  ServiceName<Object> service2Name = ServiceName.newServiceName("service2", Object.class);

  private Service<Object> mockService1;
  private Service<Object> mockService2;

  @Before
  public void setup() {
    mockService1 = mock(Service.class);
    mockService2 = mock(Service.class);
  }

  @Test
  public void testEmptyOperation() {
    final ServiceContainer container = serviceContainerRule.get();

    // when
    final ActorFuture<Void> future = container.createComposite(compositeName).install();

    // then
    future.join();
  }

  @Test
  public void testCompositeInstall() {
    final ServiceContainer container = serviceContainerRule.get();

    // when
    final CompositeServiceBuilder composite = container.createComposite(compositeName);

    final ActorFuture<Object> service1Future =
        composite.createService(service1Name, mockService1).install();
    final ActorFuture<Object> service2Future =
        composite.createService(service1Name, mockService2).install();

    final ActorFuture<Void> compositeFuture = composite.install();

    // then
    service1Future.join();
    service2Future.join();
    compositeFuture.join();
  }

  @Test
  public void testCompositeInstallAsync() {
    final ServiceContainer container = serviceContainerRule.get();

    final AsyncStartService asyncService2 = new AsyncStartService();
    asyncService2.future = new CompletableActorFuture<>();

    // when
    final CompositeServiceBuilder composite = container.createComposite(compositeName);

    final ActorFuture<Object> service1Future =
        composite.createService(service1Name, mockService1).install();
    final ActorFuture<Object> service2Future =
        composite.createService(service1Name, asyncService2).install();

    final ActorFuture<Void> compositeFuture = composite.install();

    // then
    service1Future.join();

    // when
    asyncService2.future.complete(null);
    service2Future.join();
    compositeFuture.join();
  }

  @Test
  public void testCompositeInstallAsyncFails() {
    final ServiceContainer container = serviceContainerRule.get();

    final AsyncStartService asyncService2 = new AsyncStartService();
    asyncService2.future = new CompletableActorFuture<>();

    // when
    final CompositeServiceBuilder composite = container.createComposite(compositeName);

    final ActorFuture<Object> service1Future =
        composite.createService(service1Name, mockService1).install();
    final ActorFuture<Object> service2Future =
        composite.createService(service1Name, asyncService2).install();

    final ActorFuture<Void> compositeFuture = composite.install();

    // then
    service1Future.join();

    assertThat(container.hasService(service1Name).join()).isTrue();

    // when
    asyncService2.future.completeExceptionally(new RuntimeException());
    assertThatThrownBy(() -> service2Future.join());
    assertThatThrownBy(() -> compositeFuture.join());

    // both services have been removed
    assertThat(container.hasService(service1Name).join()).isFalse();
    assertThat(container.hasService(service2Name).join()).isFalse();

    // stop has been called in service 1
    final InOrder inOrder = inOrder(mockService1);
    inOrder.verify(mockService1, times(1)).start(any());
    inOrder.verify(mockService1, times(1)).stop(any());
  }

  static class AsyncStartService implements Service<Object> {
    CompletableActorFuture<Void> future;
    Object value = new Object();
    Runnable action;
    volatile boolean wasStopped = false;

    @Override
    public void start(ServiceStartContext startContext) {
      if (action != null) {
        startContext.run(action);
      } else if (future != null) {
        startContext.async(future);
      }
    }

    @Override
    public void stop(ServiceStopContext stopContext) {
      wasStopped = true;
    }

    @Override
    public Object get() {
      return value;
    }
  }
}
