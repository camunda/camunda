/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class LivenessTest {

  private static final long SECOND = Duration.ofSeconds(1).toNanos();

  private final Liveness liveness = new Liveness(Duration.ofSeconds(30));

  @Test
  void shouldCountAWorkerAsLiveFromTheFirstTimeItIsSeen() {
    // when
    liveness.observe(Set.of("w-1"), Map.of("w-1", 42L), 0);

    // then
    assertThat(liveness.isLive("w-1", 0)).isTrue();
    assertThat(liveness.isLive("w-2", 0)).isFalse();
  }

  @Test
  void shouldCountAWorkerAsDeadOnceItsBeatHasNotChangedForALease() {
    // given
    liveness.observe(Set.of("w-1"), Map.of("w-1", 1L), 0);

    // when
    liveness.observe(Set.of("w-1"), Map.of("w-1", 1L), 29 * SECOND);
    final boolean beforeLease = liveness.isLive("w-1", 29 * SECOND);
    liveness.observe(Set.of("w-1"), Map.of("w-1", 1L), 30 * SECOND);

    // then
    assertThat(beforeLease).isTrue();
    assertThat(liveness.isLive("w-1", 30 * SECOND)).isFalse();
    assertThat(liveness.live(30 * SECOND)).isEmpty();
  }

  @Test
  void shouldKeepAWorkerLiveWhileItsBeatChanges() {
    // given
    liveness.observe(Set.of("w-1"), Map.of("w-1", 1L), 0);

    // when
    liveness.observe(Set.of("w-1"), Map.of("w-1", 2L), 20 * SECOND);

    // then
    assertThat(liveness.isLive("w-1", 49 * SECOND)).isTrue();
    assertThat(liveness.isLive("w-1", 50 * SECOND)).isFalse();
  }

  @Test
  void shouldKeepTheLastBeatOfAWorkerWhoseHeartbeatCouldNotBeRead() {
    // given
    liveness.observe(Set.of("w-1"), Map.of("w-1", 1L), 0);

    // when
    liveness.observe(Set.of("w-1"), Map.of(), 10 * SECOND);
    liveness.observe(Set.of("w-1"), Map.of("w-1", 1L), 20 * SECOND);

    // then
    assertThat(liveness.isLive("w-1", 29 * SECOND)).isTrue();
    assertThat(liveness.isLive("w-1", 30 * SECOND)).isFalse();
  }

  @Test
  void shouldForgetAWorkerWhoseHeartbeatIsGone() {
    // given
    liveness.observe(Set.of("w-1", "w-2"), Map.of("w-1", 1L, "w-2", 1L), 0);

    // when
    liveness.observe(Set.of("w-2"), Map.of("w-2", 2L), SECOND);

    // then
    assertThat(liveness.live(SECOND)).containsExactly("w-2");
  }
}
