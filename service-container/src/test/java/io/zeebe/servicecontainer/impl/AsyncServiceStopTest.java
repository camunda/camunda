/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.servicecontainer.impl;

import static io.zeebe.servicecontainer.impl.ActorFutureAssertions.assertCompleted;
import static io.zeebe.servicecontainer.impl.ActorFutureAssertions.assertNotCompleted;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

@SuppressWarnings("unchecked")
public class AsyncServiceStopTest {
  public ControlledActorSchedulerRule actorSchedulerRule = new ControlledActorSchedulerRule();
  public ServiceContainerRule serviceContainerRule = new ServiceContainerRule(actorSchedulerRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(actorSchedulerRule).around(serviceContainerRule);

  ServiceName<Object> service1Name = ServiceName.newServiceName("service1", Object.class);
  ServiceName<Object> service2Name = ServiceName.newServiceName("service2", Object.class);

  @Test
  public void shouldWaitForAsyncStop() {
    final ServiceContainer serviceContainer = serviceContainerRule.get();

    // given
    final AsyncStopService service = new AsyncStopService();
    service.future = new CompletableActorFuture<Void>();

    serviceContainer.createService(service1Name, service).install();
    actorSchedulerRule.workUntilDone();

    // when
    final ActorFuture<Void> removeFuture = serviceContainer.removeService(service1Name);
    actorSchedulerRule.workUntilDone();

    // then
    assertNotCompleted(removeFuture);
  }

  @Test
  public void shouldContinueOnAsyncStopComplete() {
    final ServiceContainer serviceContainer = serviceContainerRule.get();

    // given
    final AsyncStopService service = new AsyncStopService();
    service.future = new CompletableActorFuture<Void>();

    serviceContainer.createService(service1Name, service).install();
    actorSchedulerRule.workUntilDone();

    final ActorFuture<Void> removeFuture = serviceContainer.removeService(service1Name);
    actorSchedulerRule.workUntilDone();

    // when
    service.future.complete(null);
    actorSchedulerRule.workUntilDone();

    // then
    assertCompleted(removeFuture);
  }

  @Test
  public void shouldContineOnSuppliedFutureCompletedExceptionally() {
    final ServiceContainer serviceContainer = serviceContainerRule.get();

    // given
    final AsyncStopService service = new AsyncStopService();
    service.future = new CompletableActorFuture<>();

    serviceContainer.createService(service1Name, service).install();
    actorSchedulerRule.workUntilDone();

    final ActorFuture<Void> removeFuture = serviceContainer.removeService(service1Name);
    actorSchedulerRule.workUntilDone();

    // when
    service.future.completeExceptionally(new RuntimeException());
    actorSchedulerRule.workUntilDone();

    // then
    assertCompleted(removeFuture);
  }

  @Test
  public void shouldWaitForAction() {
    final ServiceContainer serviceContainer = serviceContainerRule.get();

    // given
    final AsyncStopService service = new AsyncStopService();
    final Runnable mockAction = mock(Runnable.class);
    service.action = mockAction;

    serviceContainer.createService(service1Name, service).install();
    actorSchedulerRule.workUntilDone();

    // when
    final ActorFuture<Void> removeFuture = serviceContainer.removeService(service1Name);
    actorSchedulerRule.workUntilDone();

    // then
    assertNotCompleted(removeFuture);
  }

  @Test
  public void shouldContinueOnAction() {
    final ServiceContainer serviceContainer = serviceContainerRule.get();

    // given
    final AsyncStopService service = new AsyncStopService();
    final Runnable mockAction = mock(Runnable.class);
    service.action = mockAction;

    serviceContainer.createService(service1Name, service).install();
    actorSchedulerRule.workUntilDone();

    // when
    final ActorFuture<Void> removeFuture = serviceContainer.removeService(service1Name);
    actorSchedulerRule.workUntilDone();

    // when
    actorSchedulerRule.awaitBlockingTasksCompleted(1);
    verify(mockAction).run();
    actorSchedulerRule.workUntilDone();

    // then
    assertCompleted(removeFuture);
  }

  @Test
  public void shouldContinueOnExceptionFromAction() {
    final ServiceContainer serviceContainer = serviceContainerRule.get();

    // given
    final AsyncStopService service = new AsyncStopService();
    final Runnable mockAction = mock(Runnable.class);
    service.action = mockAction;

    doThrow(new RuntimeException()).when(mockAction).run();

    serviceContainer.createService(service1Name, service).install();
    actorSchedulerRule.workUntilDone();

    // when
    final ActorFuture<Void> removeFuture = serviceContainer.removeService(service1Name);
    actorSchedulerRule.workUntilDone();

    // when
    actorSchedulerRule.awaitBlockingTasksCompleted(1);
    verify(mockAction).run();
    actorSchedulerRule.workUntilDone();

    // then
    assertCompleted(removeFuture);
  }

  @Test
  public void shouldWaitOnConcurrentStop() {
    final ServiceContainer serviceContainer = serviceContainerRule.get();

    // given
    final AsyncStopService service = new AsyncStopService();
    service.future = new CompletableActorFuture<Void>();

    serviceContainer.createService(service1Name, service).dependency(service2Name).install();
    serviceContainer.createService(service2Name, mock(Service.class)).install();
    actorSchedulerRule.workUntilDone();

    // when
    final ActorFuture<Void> service1RemoveFuture = serviceContainer.removeService(service1Name);
    final ActorFuture<Void> service2RemoveFuture = serviceContainer.removeService(service2Name);
    actorSchedulerRule.workUntilDone();

    // then
    assertNotCompleted(service1RemoveFuture);
    assertNotCompleted(service2RemoveFuture);
  }

  @Test
  public void shouldContinueConcurrentStop() {
    final ServiceContainer serviceContainer = serviceContainerRule.get();

    // given
    final AsyncStopService service = new AsyncStopService();
    service.future = new CompletableActorFuture<Void>();

    serviceContainer.createService(service1Name, service).dependency(service2Name).install();
    serviceContainer.createService(service2Name, mock(Service.class)).install();
    actorSchedulerRule.workUntilDone();

    final ActorFuture<Void> service1RemoveFuture = serviceContainer.removeService(service1Name);
    final ActorFuture<Void> service2RemoveFuture = serviceContainer.removeService(service2Name);
    actorSchedulerRule.workUntilDone();

    // when
    service.future.complete(null);
    actorSchedulerRule.workUntilDone();

    // then
    assertCompleted(service1RemoveFuture);
    assertCompleted(service2RemoveFuture);
  }

  static class AsyncStopService implements Service<Object> {
    CompletableActorFuture<Void> future;
    Object value = new Object();
    Runnable action;

    @Override
    public void start(ServiceStartContext startContext) {}

    @Override
    public void stop(ServiceStopContext stopContext) {
      if (action != null) {
        stopContext.run(action);
      } else if (future != null) {
        stopContext.async(future);
      }
    }

    @Override
    public Object get() {
      return value;
    }
  }
}
