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
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceInterruptedException;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class InterruptibleServiceTest {
  @Rule public ControlledActorSchedulerRule actorSchedulerRule = new ControlledActorSchedulerRule();

  private ServiceContainer serviceContainer;

  private ServiceName<Object> serviceName;
  private ServiceName<Object> dependentName;

  @Before
  public void setup() {
    serviceContainer = new ServiceContainerImpl(actorSchedulerRule.get());
    serviceContainer.start();

    serviceName = ServiceName.newServiceName("service", Object.class);
    dependentName = ServiceName.newServiceName("dependent", Object.class);
  }

  @Test
  public void shouldBeInterruptedOnDependentsStopped() {
    // given
    final InterruptibleService service = new InterruptibleService(true);
    final ActorFuture<Object> startFuture =
        serviceContainer.createService(serviceName, service).dependency(dependentName).install();

    final InterruptibleService dependent = new InterruptibleService(false);
    serviceContainer.createService(dependentName, dependent).install();
    actorSchedulerRule.workUntilDone();

    // when
    dependent.finishStart();
    actorSchedulerRule.workUntilDone();

    serviceContainer.removeService(dependentName);
    actorSchedulerRule.workUntilDone();

    // then
    assertInterrupted(service, startFuture);
  }

  @Test
  public void shouldNotBeCompletedIfNotInterruptible() {
    // given
    final InterruptibleService service = new InterruptibleService(false);

    final ActorFuture<Object> startFuture =
        serviceContainer.createService(serviceName, service).install();
    actorSchedulerRule.workUntilDone();

    // when
    serviceContainer.removeService(serviceName);
    actorSchedulerRule.workUntilDone();

    // then
    assertNotCompleted(startFuture);
  }

  @Test
  public void shouldNotBeMarkedAsInterruptedWhenStoppedNormally() {
    // given
    final InterruptibleService service = new InterruptibleService(false);

    final ActorFuture<Object> startFuture =
        serviceContainer.createService(serviceName, service).install();
    actorSchedulerRule.workUntilDone();

    // when
    service.finishStart();
    actorSchedulerRule.workUntilDone();

    serviceContainer.removeService(serviceName);
    actorSchedulerRule.workUntilDone();

    // then
    assertCompleted(startFuture);
    assertThat(service.wasInterrupted).isFalse();
    assertThat(service.wasStopped).isTrue();
  }

  @Test
  public void shouldCompleteExceptionallyIfInterrupted() {
    // given
    final InterruptibleService service = new InterruptibleService(true);

    final ActorFuture<Object> startFuture =
        serviceContainer.createService(serviceName, service).install();
    actorSchedulerRule.workUntilDone();

    // when
    serviceContainer.removeService(serviceName);
    actorSchedulerRule.workUntilDone();

    // then
    assertInterrupted(service, startFuture);
  }

  private void assertInterrupted(InterruptibleService service, ActorFuture<?> future) {
    assertCompleted(future);
    assertThat(future.getException()).isInstanceOf(ServiceInterruptedException.class);
    assertThat(service.wasInterrupted).isTrue();
    assertThat(service.wasStopped).isTrue();
  }

  static class InterruptibleService implements Service<Object> {
    CompletableActorFuture<Void> startFuture = new CompletableActorFuture<>();
    Object value = new Object();
    boolean isInterruptible;
    volatile boolean wasInterrupted;
    volatile boolean wasStopped;

    InterruptibleService(boolean isInterruptible) {
      this.isInterruptible = isInterruptible;
      wasInterrupted = false;
      wasStopped = false;
    }

    @Override
    public void start(ServiceStartContext startContext) {
      startContext.async(startFuture, isInterruptible);
    }

    @Override
    public void stop(ServiceStopContext stopContext) {
      if (stopContext.wasInterrupted()) {
        wasInterrupted = true;
      }

      wasStopped = true;
    }

    @Override
    public Object get() {
      return value;
    }

    public void finishStart() {
      startFuture.complete(null);
    }
  }
}
