/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.servicecontainer.impl;

import static io.zeebe.servicecontainer.impl.ActorFutureAssertions.assertCompleted;
import static io.zeebe.servicecontainer.impl.ActorFutureAssertions.assertFailed;
import static io.zeebe.servicecontainer.impl.ActorFutureAssertions.assertNotCompleted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class AsyncStartTest {
  @Rule public ControlledActorSchedulerRule actorSchedulerRule = new ControlledActorSchedulerRule();

  ServiceContainer serviceContainer;

  ServiceName<Object> service1Name;
  ServiceName<Object> service2Name;

  @Before
  public void setup() {
    serviceContainer = new ServiceContainerImpl(actorSchedulerRule.get());
    serviceContainer.start();

    service1Name = ServiceName.newServiceName("service1", Object.class);
    service2Name = ServiceName.newServiceName("service2", Object.class);
  }

  @Test
  public void shouldWaitForAsyncStart() {
    // when
    final AsyncStartService service = new AsyncStartService();
    service.future = new CompletableActorFuture<>();

    final ActorFuture<Object> startFuture =
        serviceContainer.createService(service1Name, service).install();
    actorSchedulerRule.workUntilDone();

    // then
    assertNotCompleted(startFuture);
  }

  @Test
  public void shouldContinueOnAsyncStartComplete() {
    // given
    final AsyncStartService service = new AsyncStartService();
    service.future = new CompletableActorFuture<Void>();

    final ActorFuture<Object> startFuture =
        serviceContainer.createService(service1Name, service).install();
    actorSchedulerRule.workUntilDone();
    assertNotCompleted(startFuture);

    // when
    service.future.complete(null);
    actorSchedulerRule.workUntilDone();

    // then
    assertCompleted(startFuture);
  }

  @Test
  public void shouldContinueOnAsyncStartCompleteAndReturn() {
    // given
    final AsyncStartService service = new AsyncStartService();
    service.future = new CompletableActorFuture<Void>();

    final ActorFuture<Object> startFuture =
        serviceContainer.createService(service1Name, service).install();

    actorSchedulerRule.workUntilDone();
    assertNotCompleted(startFuture);

    // when
    service.future.complete(null);
    actorSchedulerRule.workUntilDone();

    // then
    assertCompleted(startFuture);
    assertEquals(service.get(), startFuture.join());
  }

  @Test
  public void shouldContinueOnAsyncStartCompletedExceptionally() {
    // given
    final AsyncStartService service = new AsyncStartService();
    service.future = new CompletableActorFuture<Void>();

    final ActorFuture<Object> startFuture =
        serviceContainer.createService(service1Name, service).install();
    actorSchedulerRule.workUntilDone();

    // when
    service.future.completeExceptionally(new RuntimeException());
    actorSchedulerRule.workUntilDone();

    // then
    assertFailed(startFuture);
  }

  @Test
  public void shouldWaitOnSuppliedFuture() {
    // when
    final AsyncStartService service = new AsyncStartService();
    service.future = new CompletableActorFuture<>();

    final ActorFuture<Object> startFuture =
        serviceContainer.createService(service1Name, service).install();
    actorSchedulerRule.workUntilDone();

    // then
    assertNotCompleted(startFuture);
  }

  @Test
  public void shouldWaitForAction() {
    // when
    final AsyncStartService service = new AsyncStartService();
    service.action = new BlockingAction();

    final ActorFuture<Object> startFuture =
        serviceContainer.createService(service1Name, service).install();
    actorSchedulerRule.workUntilDone();

    // then
    assertNotCompleted(startFuture);
  }

  @Test
  public void shouldContinueOnAction() throws BrokenBarrierException, InterruptedException {
    // given
    final AsyncStartService service = new AsyncStartService();
    final BlockingAction action = new BlockingAction();
    service.action = action;

    final ActorFuture<Object> startFuture =
        serviceContainer.createService(service1Name, service).install();
    actorSchedulerRule.workUntilDone();

    // when
    action.signal();
    actorSchedulerRule.awaitBlockingTasksCompleted(1);
    actorSchedulerRule.workUntilDone();

    // then
    assertCompleted(startFuture);
  }

  @Test
  @Ignore
  public void shouldStopOnExceptionFromAction() {
    // given
    final AsyncStartService service = new AsyncStartService();
    final Runnable mockAction = mock(Runnable.class);

    doThrow(new RuntimeException()).when(mockAction).run();

    service.action = mockAction;

    final ActorFuture<Object> startFuture =
        serviceContainer.createService(service1Name, service).install();

    // when
    actorSchedulerRule.workUntilDone();

    // then
    assertFailed(startFuture);
    verify(mockAction).run();
  }

  @Test
  public void shouldContineOnSuppliedFuture() {
    // given
    final AsyncStartService service = new AsyncStartService();
    service.future = new CompletableActorFuture<>();

    final ActorFuture<Object> startFuture =
        serviceContainer.createService(service1Name, service).install();
    actorSchedulerRule.workUntilDone();

    // when
    service.future.complete(null);
    actorSchedulerRule.workUntilDone();

    // then
    assertCompleted(startFuture);
  }

  @Test
  @Ignore
  public void shouldFailOnSuppliedFutureCompletedExceptionally() {
    // given
    final AsyncStartService service = new AsyncStartService();
    service.future = new CompletableActorFuture<>();

    final ActorFuture<Object> startFuture =
        serviceContainer.createService(service1Name, service).install();
    actorSchedulerRule.workUntilDone();

    // when
    service.future.completeExceptionally(new Throwable());
    actorSchedulerRule.workUntilDone();

    // then
    assertFailed(startFuture);
  }

  @Test
  public void shouldWaitOnConcurrentStart() {
    // when
    final AsyncStartService service = new AsyncStartService();
    service.future = new CompletableActorFuture<Void>();

    final ActorFuture<Object> service1StartFuture =
        serviceContainer.createService(service1Name, service).install();
    final ActorFuture service2StartFuture =
        serviceContainer
            .createService(service2Name, mock(Service.class))
            .dependency(service1Name)
            .install();
    actorSchedulerRule.workUntilDone();

    // then
    assertNotCompleted(service1StartFuture);
    assertNotCompleted(service2StartFuture);
  }

  @Test
  public void shouldContinueConcurrentStart() {
    // given
    final AsyncStartService service = new AsyncStartService();
    service.future = new CompletableActorFuture<Void>();

    final ActorFuture<Object> service1StartFuture =
        serviceContainer.createService(service1Name, service).install();
    final ActorFuture service2StartFuture =
        serviceContainer
            .createService(service2Name, mock(Service.class))
            .dependency(service1Name)
            .install();
    actorSchedulerRule.workUntilDone();

    // when
    service.future.complete(null);
    actorSchedulerRule.workUntilDone();

    // then
    assertCompleted(service1StartFuture);
    assertCompleted(service2StartFuture);
  }

  @Test
  @Ignore
  public void shouldFailConcurrentStart() {
    // given
    final AsyncStartService service = new AsyncStartService();

    final ActorFuture<Object> service1StartFuture =
        serviceContainer.createService(service1Name, service).install();
    final ActorFuture service2StartFuture =
        serviceContainer
            .createService(service2Name, mock(Service.class))
            .dependency(service1Name)
            .install();
    actorSchedulerRule.workUntilDone();

    // when
    service.future.completeExceptionally(new Throwable());
    actorSchedulerRule.workUntilDone();

    // then
    assertFailed(service1StartFuture);
    assertFailed(service2StartFuture);
  }

  @Test
  public void shouldWaitForAsyncStartWhenRemovedConcurrently() {
    // given
    final AsyncStartService service = new AsyncStartService();
    service.future = new CompletableActorFuture<>();

    final ActorFuture<Object> service1StartFuture =
        serviceContainer.createService(service1Name, service).install();
    actorSchedulerRule.workUntilDone();

    // when
    final ActorFuture<Void> removeFuture = serviceContainer.removeService(service1Name);
    actorSchedulerRule.workUntilDone();

    // then
    assertNotCompleted(service1StartFuture);
    assertNotCompleted(removeFuture);

    // AND

    // when
    service.future.complete(null);
    actorSchedulerRule.workUntilDone();

    // then
    assertFailed(service1StartFuture);
    assertCompleted(removeFuture);
    assertThat(service.wasStopped).isTrue();
  }

  @Test
  public void shouldWaitForAsyncStartWhenRemovedConcurrentlyFailure() {
    // given
    final AsyncStartService service = new AsyncStartService();
    service.future = new CompletableActorFuture<Void>();

    final ActorFuture<Object> service1StartFuture =
        serviceContainer.createService(service1Name, service).install();

    actorSchedulerRule.workUntilDone();

    // when
    final ActorFuture<Void> removeFuture = serviceContainer.removeService(service1Name);
    actorSchedulerRule.workUntilDone();

    // then
    assertNotCompleted(service1StartFuture);
    assertNotCompleted(removeFuture);

    // AND

    // when
    service.future.completeExceptionally(new Throwable()); // async start completes exceptionally
    actorSchedulerRule.workUntilDone();

    // then
    assertFailed(service1StartFuture);
    assertCompleted(removeFuture);
    assertThat(service.wasStopped).isFalse();
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

  /** Runnable which blocks until signaled */
  static class BlockingAction implements Runnable {

    CyclicBarrier barrier = new CyclicBarrier(2);

    @Override
    public void run() {
      try {
        barrier.await();
      } catch (InterruptedException | BrokenBarrierException e) {
        e.printStackTrace();
      }
    }

    public void signal() throws BrokenBarrierException, InterruptedException {
      barrier.await();
    }
  }
}
