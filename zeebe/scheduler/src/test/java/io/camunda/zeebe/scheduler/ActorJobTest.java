/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class ActorJobTest {

  @Test
  void shouldObserveSchedulingLatencyOfJobs() throws Exception {
    // given
    final var registry = new SimpleMeterRegistry();
    try (final var actorScheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(0)
            .setMeterRegistry(registry)
            .build()) {
      actorScheduler.start();
      final var actor = new Actor() {};

      // when
      actorScheduler.submitActor(actor);
      assertThat(actor.actor.call(() -> 1)).succeedsWithin(Duration.ofSeconds(10));

      // then
      assertThat(registry.get(ActorMetricsDoc.SCHEDULING_LATENCY.getName()).timers())
          .extracting(Timer::count)
          .anySatisfy(count -> assertThat(count).isPositive());
    } finally {
      registry.close();
    }
  }
}
