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
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
  private final MeterRegistry registry = new SimpleMeterRegistry();
  private ActorScheduler brokerScheduler;

  @BeforeEach
  void setUp() {
    brokerScheduler =
        ActorScheduler.newActorScheduler()
            .setMeterRegistry(registry)
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(1)
            .build();
    brokerScheduler.start();
    context.setActorSchedulingService(brokerScheduler);
    context.setActorSchedulerBuilderFactory(
        () -> ActorScheduler.newActorScheduler().setMeterRegistry(registry));
    context.setBrokerConfiguration(brokerCfg);
  }

  @AfterEach
  void tearDown() throws Exception {
    brokerScheduler.close();
  }

  @Test
  void shouldRunPartitionActorsOnDedicatedThreads() throws Exception {
    // given
    brokerCfg.getThreads().getPhysicalTenantActorPool().setCpuThreadCount(1);
    brokerCfg.getThreads().getPhysicalTenantActorPool().setIoThreadCount(1);

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
  void shouldTagActorMetricsOfDedicatedSchedulerWithTenant() throws Exception {
    // given
    startup();

    // when
    threadNameOf(context.getPartitionActorSchedulingService(TENANT_ID), SchedulingHints.cpuBound());

    // then
    assertThat(
            registry
                .find("zeebe.actor_task_execution_count")
                .tag("physicalTenant", TENANT_ID)
                .counters())
        .isNotEmpty();
  }

  @Test
  void shouldRunExpressionEvaluationsOnTenantIoThreads() throws Exception {
    // given
    startup();

    // when
    final var executor = context.getExpressionEvaluationExecutor(TENANT_ID);
    final var thread = executor.submit(Thread::currentThread).get(10, TimeUnit.SECONDS);

    // then
    assertThat(thread.getName()).startsWith(TENANT_ID + "-zb-fs-workers-");
  }

  @Test
  void shouldEvaluateWhileCallerBlocksTheOnlyCpuThread() throws Exception {
    // given - one CPU thread, which the calling actor holds while it waits for the evaluation
    brokerCfg.getThreads().getPhysicalTenantActorPool().setCpuThreadCount(1);
    startup();
    final var executor = context.getExpressionEvaluationExecutor(TENANT_ID);
    final var result = new CompletableFuture<Integer>();

    // when
    context
        .getPartitionActorSchedulingService(TENANT_ID)
        .submitActor(
            new Actor() {
              @Override
              protected void onActorStarted() {
                try {
                  result.complete(executor.submit(() -> 42).get(10, TimeUnit.SECONDS));
                } catch (final Exception e) {
                  result.completeExceptionally(e);
                }
              }
            });

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(15)).isEqualTo(42);
  }

  @Test
  void shouldStopExpressionEvaluationExecutorOnShutdown() {
    // given
    startup();
    final var executor = context.getExpressionEvaluationExecutor(TENANT_ID);

    // when
    final ActorFuture<BrokerStartupContext> shutdownFuture = CONCURRENCY_CONTROL.createFuture();
    sut.shutdownInternal(context, CONCURRENCY_CONTROL, shutdownFuture);

    // then
    assertThat(shutdownFuture).succeedsWithin(Duration.ofSeconds(10));
    assertThat(executor.isShutdown()).isTrue();
    assertThat(context.getExpressionEvaluationExecutor(TENANT_ID)).isNotSameAs(executor);
  }

  @Test
  void shouldStopDedicatedSchedulerOnShutdown() {
    // given
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

  @Test
  void shouldRunAndStopDedicatedSchedulerWithCpuBudget() throws Exception {
    // given
    brokerCfg.getThreads().getPhysicalTenantActorPool().setCpuBudgetCores(0.5);
    startup();

    // when
    final var threadName =
        threadNameOf(
            context.getPartitionActorSchedulingService(TENANT_ID), SchedulingHints.cpuBound());
    final ActorFuture<BrokerStartupContext> shutdownFuture = CONCURRENCY_CONTROL.createFuture();
    sut.shutdownInternal(context, CONCURRENCY_CONTROL, shutdownFuture);

    // then
    assertThat(threadName).startsWith("tenant-a-zb-actors-");
    assertThat(shutdownFuture).succeedsWithin(Duration.ofSeconds(10));
  }

  @Test
  void shouldLimitCpuTimeOfExpressionEvaluationsToBudget() throws Exception {
    // given - a budget of 0.3 core, and many short evaluations that would use 2 IO threads fully
    final var pool = brokerCfg.getThreads().getPhysicalTenantActorPool();
    pool.setIoThreadCount(2);
    pool.setCpuBudgetCores(0.3);
    startup();
    final var executor = context.getExpressionEvaluationExecutor(TENANT_ID);
    final var scheduler = (ActorScheduler) context.getPartitionActorSchedulingService(TENANT_ID);
    final var stop = new AtomicBoolean();
    for (int i = 0; i < 20_000; i++) {
      executor.submit(
          () -> {
            final long end = System.nanoTime() + 1_000_000;
            while (!stop.get() && System.nanoTime() < end) {
              Thread.onSpinWait();
            }
            return null;
          });
    }

    // when
    final var bean = ManagementFactory.getThreadMXBean();
    final long before =
        scheduler.getThreads().stream().mapToLong(t -> bean.getThreadCpuTime(t.threadId())).sum();
    Thread.sleep(3_000);
    final long used =
        scheduler.getThreads().stream().mapToLong(t -> bean.getThreadCpuTime(t.threadId())).sum()
            - before;
    stop.set(true);

    // then
    final double cores = used / 3_000_000_000.0;
    System.out.printf("RESULT feel cores=%.3f%n", cores);
    assertThat(cores).isBetween(0.2, 0.4);
  }

  private void startup() {
    final ActorFuture<BrokerStartupContext> startupFuture = CONCURRENCY_CONTROL.createFuture();
    sut.startupInternal(context, CONCURRENCY_CONTROL, startupFuture);
    assertThat(startupFuture).succeedsWithin(Duration.ofSeconds(10));
  }

  private static String threadNameOf(
      final ActorSchedulingService scheduler, final SchedulingHints hints) throws Exception {
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
