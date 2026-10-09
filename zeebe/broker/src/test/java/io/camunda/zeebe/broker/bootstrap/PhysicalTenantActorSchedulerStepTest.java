/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class PhysicalTenantActorSchedulerStepTest {

  private static final String TENANT_ID = "tenant-a";
  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();

  private final PhysicalTenantActorSchedulerStep sut =
      new PhysicalTenantActorSchedulerStep(TENANT_ID);
  private final BrokerCfg brokerCfg = new BrokerCfg();
  private final MockBrokerStartupContext context = new MockBrokerStartupContext();
  private ActorScheduler brokerScheduler;

  @BeforeEach
  void setUp() {
    brokerScheduler =
        ActorScheduler.newActorScheduler()
            .setSchedulerName("broker")
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(1)
            .build();
    brokerScheduler.start();
    context.setActorSchedulingService(brokerScheduler);
    context.setBrokerConfiguration(brokerCfg);
  }

  @AfterEach
  void tearDown() throws Exception {
    brokerScheduler.close();
  }

  @Test
  void shouldRunPartitionActorsOnBrokerSchedulerWhenDisabled() {
    // when
    startup();

    // then
    assertThat(context.getPartitionActorSchedulingService(TENANT_ID)).isSameAs(brokerScheduler);
  }

  @Test
  void shouldRunPartitionActorsOnDedicatedThreadsWhenEnabled() throws Exception {
    // given
    brokerCfg.getThreads().setPhysicalTenantActorPoolEnabled(true);
    brokerCfg.getThreads().setPhysicalTenantCpuThreadCount(1);
    brokerCfg.getThreads().setPhysicalTenantIoThreadCount(1);

    // when
    startup();

    // then
    final var scheduler = context.getPartitionActorSchedulingService(TENANT_ID);
    assertThat(scheduler).isNotSameAs(brokerScheduler);
    assertThat(threadNameOf(scheduler, SchedulingHints.cpuBound()))
        .isEqualTo("tenant-a-zb-actors-0");
    assertThat(threadNameOf(scheduler, SchedulingHints.ioBound()))
        .isEqualTo("tenant-a-zb-fs-workers-0");
    assertThat(threadNameOf(brokerScheduler, SchedulingHints.cpuBound())).isEqualTo("zb-actors-0");
  }

  @Test
  void shouldStopDedicatedSchedulerOnShutdown() {
    // given
    brokerCfg.getThreads().setPhysicalTenantActorPoolEnabled(true);
    startup();
    final var scheduler = (ActorScheduler) context.getPartitionActorSchedulingService(TENANT_ID);

    // when
    final ActorFuture<BrokerStartupContext> shutdownFuture = CONCURRENCY_CONTROL.createFuture();
    sut.shutdownInternal(context, CONCURRENCY_CONTROL, shutdownFuture);

    // then
    assertThat(shutdownFuture).succeedsWithin(Duration.ofSeconds(10));
    assertThat(context.getPartitionActorSchedulingService(TENANT_ID)).isSameAs(brokerScheduler);
    assertThatThrownBy(() -> scheduler.submitActor(new Actor() {}))
        .isInstanceOf(IllegalStateException.class);
  }

  private void startup() {
    final ActorFuture<BrokerStartupContext> startupFuture = CONCURRENCY_CONTROL.createFuture();
    sut.startupInternal(context, CONCURRENCY_CONTROL, startupFuture);
    assertThat(startupFuture).succeedsWithin(Duration.ofSeconds(10));
  }

  private static String threadNameOf(
      final io.camunda.zeebe.scheduler.ActorSchedulingService scheduler,
      final SchedulingHints hints)
      throws Exception {
    final var name = new CompletableFuture<String>();
    scheduler.submitActor(
        new Actor() {
          @Override
          protected void onActorStarted() {
            name.complete(Thread.currentThread().getName());
          }
        },
        hints);
    return name.get(10, TimeUnit.SECONDS);
  }
}
