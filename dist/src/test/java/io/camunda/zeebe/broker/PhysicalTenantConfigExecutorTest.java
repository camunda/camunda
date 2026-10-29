/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.DefaultPhysicalTenantRegistry;
import io.camunda.cluster.PhysicalTenant;
import io.camunda.cluster.PhysicalTenantHealth.State;
import io.camunda.zeebe.broker.PhysicalTenantChangeException.Reason;
import io.camunda.zeebe.broker.PhysicalTenantRolloutReadiness.NotReadyException;
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference;
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference.Owner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class PhysicalTenantConfigExecutorTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final PhysicalTenantLifecycleService lifecycle =
      mock(PhysicalTenantLifecycleService.class);
  private final PhysicalTenantRolloutReadiness readiness =
      mock(PhysicalTenantRolloutReadiness.class);
  private final DefaultPhysicalTenantRegistry registry =
      new DefaultPhysicalTenantRegistry(
          List.of(
              new PhysicalTenant("slow", null, UUID.randomUUID()),
              new PhysicalTenant("fast", null, UUID.randomUUID())));
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final PhysicalTenantConfigExecutor executor =
      new PhysicalTenantConfigExecutor(lifecycle, registry, meterRegistry, readiness);

  @AfterEach
  void tearDown() {
    executor.close();
    meterRegistry.close();
  }

  @Test
  void shouldWaitAgainWithoutRestartingATenantThatDidNotBecomeReady() {
    // given
    executor.runsFromStaticConfiguration("slow", Map.of("a", "1"));
    doThrow(new NotReadyException("partition 1 is CANDIDATE", null))
        .doNothing()
        .when(readiness)
        .awaitReady(eq("slow"), any());
    final var first = executor.apply("slow", reference(2, "a", "2"), encode("a", "2"));
    assertThat(first).failsWithin(TIMEOUT);
    assertThat(registry.health("slow").orElseThrow().state()).isEqualTo(State.DEGRADED);

    // when
    final var retry = executor.apply("slow", reference(2, "a", "2"), encode("a", "2"));

    // then
    assertThat(retry).succeedsWithin(TIMEOUT);
    verify(lifecycle, times(1)).replace(eq("slow"), anyMap(), any());
    verify(readiness, times(2)).awaitReady(eq("slow"), any());
    assertThat(executor.applied("slow").orElseThrow().version()).isEqualTo(2);
    assertThat(registry.health("slow").orElseThrow().state()).isEqualTo(State.HEALTHY);
  }

  @Test
  void shouldApplyAChangeOfAnotherTenantWhileOneWaitsForItsReadiness() {
    // given
    executor.runsFromStaticConfiguration("slow", Map.of("a", "1"));
    executor.runsFromStaticConfiguration("fast", Map.of("b", "1"));
    final var ready = new CompletableFuture<Void>();
    doAnswer(
            invocation -> {
              ready.join();
              return null;
            })
        .when(readiness)
        .awaitReady(eq("slow"), any());
    final var slow = executor.apply("slow", reference(2, "a", "2"), encode("a", "2"));

    // when
    final var fast = executor.apply("fast", reference(2, "b", "2"), encode("b", "2"));

    // then
    assertThat(fast).succeedsWithin(TIMEOUT);
    assertThat(slow).isNotDone();
    ready.complete(null);
    assertThat(slow).succeedsWithin(TIMEOUT);
  }

  @Test
  void shouldRestartNothingAndRecoverWhenARejectedChangeIsRolledBack() {
    // given
    executor.runsFromStaticConfiguration("slow", Map.of("a", "1"));
    doThrow(new PhysicalTenantChangeException(Reason.INVALID, "invalid", null))
        .when(lifecycle)
        .replace(eq("slow"), anyMap(), any());
    assertThat(executor.apply("slow", reference(2, "a", "2"), encode("a", "2")))
        .failsWithin(TIMEOUT);

    // when
    final var rolledBack = executor.apply("slow", reference(3, "a", "1"), encode("a", "1"));

    // then
    assertThat(rolledBack).succeedsWithin(TIMEOUT);
    verify(lifecycle, times(1)).replace(eq("slow"), anyMap(), any());
    verify(readiness, never()).awaitReady(eq("slow"), any());
    assertThat(executor.applied("slow").orElseThrow().version()).isEqualTo(3);
    assertThat(registry.health("slow").orElseThrow().state()).isEqualTo(State.HEALTHY);
  }

  @Test
  void shouldPreferMembersThatAppliedTheVersionThenTheOnesThatRestartLast() {
    // given
    final var applied = new TreeMap<MemberId, Long>(MemberId.ID_COMPARATOR);
    applied.put(member(1), 3L);
    applied.put(member(3), 3L);
    applied.put(member(4), 2L);
    final var reference =
        new PhysicalTenantConfigReference(
            3, "sha", Owner.API, UUID.randomUUID().toString(), applied);

    // when
    final var preferred =
        Stream.of(member(0), member(1), member(3), member(4), member(5))
            .sorted(PhysicalTenantConfigExecutor.handoverPreference(reference))
            .toList();

    // then
    assertThat(preferred).isEqualTo(List.of(member(1), member(3), member(5), member(4), member(0)));
  }

  private static PhysicalTenantConfigReference reference(
      final long version, final String key, final String value) {
    return new PhysicalTenantConfigReference(
        version,
        PhysicalTenantConfigCodec.sha256(encode(key, value)),
        Owner.API,
        UUID.randomUUID().toString(),
        new TreeMap<>());
  }

  private static byte[] encode(final String key, final String value) {
    return PhysicalTenantConfigCodec.encode(Map.of(key, value));
  }

  private static MemberId member(final int id) {
    return MemberId.from(String.valueOf(id));
  }
}
