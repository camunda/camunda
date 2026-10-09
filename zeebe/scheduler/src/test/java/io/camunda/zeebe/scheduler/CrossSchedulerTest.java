/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that two independent schedulers, such as the broker-wide one and the one of a physical
 * tenant, can be used side by side: actors call into each other, timers fire on the thread of the
 * scheduler that owns the actor, and stopping one scheduler leaves the other usable.
 */
final class CrossSchedulerTest {

  private static final long TIMEOUT_SECONDS = 30;

  private ActorScheduler broker;
  private ActorScheduler tenant;

  @BeforeEach
  void setUp() {
    broker = newScheduler("");
    tenant = newScheduler("tenant-");
  }

  @AfterEach
  void tearDown() throws Exception {
    broker.close();
    if (tenant != null) {
      tenant.close();
    }
  }

  @Test
  void shouldRunActorsOfBothSchedulersOnTheirOwnThreads() throws Exception {
    // given
    final var brokerActor = new ThreadRecordingActor();
    final var tenantActor = new ThreadRecordingActor();

    // when
    broker.submitActor(brokerActor).join();
    tenant.submitActor(tenantActor).join();

    // then
    assertThat(brokerActor.startThread()).startsWith("zb-actors-");
    assertThat(tenantActor.startThread()).startsWith("tenant-zb-actors-");
  }

  @Test
  void shouldFireTimersAndWakeActorsAcrossSchedulers() throws Exception {
    // given - a tenant actor that waits on a future completed by a broker actor, then sets a timer
    final var future = new CompletableActorFuture<Void>();
    final var tenantActor = new ThreadRecordingActor();
    tenantActor.onStart =
        () -> {
          tenantActor.actor.runOnCompletion(
              future,
              (ok, error) ->
                  tenantActor.actor.schedule(
                      Duration.ofMillis(20),
                      () -> tenantActor.timerThread.complete(Thread.currentThread().getName())));
        };
    final var brokerActor = new ThreadRecordingActor();
    tenant.submitActor(tenantActor).join();
    broker.submitActor(brokerActor).join();

    // when
    brokerActor.actor.run(() -> future.complete(null));

    // then
    assertThat(tenantActor.timerThread.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        .startsWith("tenant-zb-actors-");
  }

  @Test
  void shouldKeepBrokerSchedulerUsableWhenTenantSchedulerIsStopped() throws Exception {
    // given
    tenant.close();
    tenant = null;
    final var brokerActor = new ThreadRecordingActor();

    // when
    broker.submitActor(brokerActor).join();

    // then
    assertThat(brokerActor.startThread()).startsWith("zb-actors-");
  }

  private static ActorScheduler newScheduler(final String threadNamePrefix) {
    final var scheduler =
        ActorScheduler.newActorScheduler()
            .setThreadNamePrefix(threadNamePrefix)
            .setCpuBoundActorThreadCount(2)
            .setIoBoundActorThreadCount(1)
            .build();
    scheduler.start();
    return scheduler;
  }

  private static final class ThreadRecordingActor extends Actor {
    private final CompletableFuture<String> startThread = new CompletableFuture<>();
    private final CompletableFuture<String> timerThread = new CompletableFuture<>();
    private Runnable onStart = () -> {};

    @Override
    protected void onActorStarted() {
      startThread.complete(Thread.currentThread().getName());
      onStart.run();
    }

    String startThread() throws Exception {
      return startThread.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
  }
}
