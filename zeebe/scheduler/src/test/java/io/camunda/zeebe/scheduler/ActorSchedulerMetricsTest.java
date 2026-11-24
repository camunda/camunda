/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class ActorSchedulerMetricsTest {

  @Test
  void shouldTagMetricsOfSchedulersWithTheirName() throws Exception {
    // given
    final var registry = new SimpleMeterRegistry();
    try (final var first = newScheduler("first", registry);
        final var second = newScheduler("second", registry)) {
      first.start();
      second.start();

      // when
      for (final var scheduler : new ActorScheduler[] {first, second}) {
        final var actor = new Actor() {};
        scheduler.submitActor(actor);
        assertThat(actor.actor.call(() -> 1)).succeedsWithin(Duration.ofSeconds(10));
      }

      // then
      for (final var name : new String[] {"first", "second"}) {
        assertThat(
                registry
                    .get(ActorMetricsDoc.SCHEDULING_LATENCY.getName())
                    .tags(Tags.of("scheduler", name))
                    .timers())
            .isNotEmpty();
      }
    } finally {
      registry.close();
    }
  }

  private static ActorScheduler newScheduler(
      final String name, final SimpleMeterRegistry registry) {
    return ActorScheduler.newActorScheduler()
        .setSchedulerName(name)
        .setCpuBoundActorThreadCount(1)
        .setIoBoundActorThreadCount(0)
        .setMeterRegistry(registry)
        .build();
  }
}
