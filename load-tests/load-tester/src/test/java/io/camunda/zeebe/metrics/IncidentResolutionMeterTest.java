/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.zeebe.metrics.IncidentResolutionMeter.IncidentSnapshot;
import io.camunda.zeebe.metrics.IncidentResolutionMeter.IncidentSource;
import io.camunda.zeebe.metrics.StarterMetricsDoc.StarterMetricKeyNames;
import io.camunda.zeebe.protocol.Protocol;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IncidentResolutionMeterTest {

  private static final int PARTITION = 3;
  private static final long PROCESS_INSTANCE_KEY = 1234L;
  // The meter seeds its discovery cursor from the wall clock at start(); tests therefore start the
  // meter at T0, advance the clock to an observation time, then make incidents discoverable with a
  // creation time in (T0, observation] — mirroring "incident created after the meter started".
  private static final Instant T0 = Instant.parse("2026-06-22T10:00:00Z");

  private SimpleMeterRegistry registry;
  private StubIncidentSource source;
  private AtomicReference<Instant> now;
  private IncidentResolutionMeter meter;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    source = new StubIncidentSource();
    now = new AtomicReference<>(T0);
    meter = newMeter(Duration.ofMinutes(20));
  }

  @AfterEach
  void tearDown() {
    meter.stop();
    registry.close();
  }

  private IncidentResolutionMeter newMeter(final Duration watchCap) {
    return new IncidentResolutionMeter(
        System::nanoTime,
        now::get,
        registry,
        Executors.newScheduledThreadPool(1),
        Duration.ofMillis(1),
        watchCap,
        1000,
        source);
  }

  @Test
  void shouldRecordLatencyWhenIncidentDiscoveredActive() {
    // given - the meter is running and the wall clock has advanced 3s past the incident's creation
    final long incidentKey = Protocol.encodePartitionId(PARTITION, 1);
    meter.start();
    now.set(T0.plusSeconds(3));

    // when - an incident born and promoted within one tick is discovered already ACTIVE
    source.discoverable(active(incidentKey, T0));

    // then - the latency is recorded against the decoded partition tag
    awaitLatencyCount(1);
    assertThat(latencyTimer().totalTime(TimeUnit.MILLISECONDS)).isBetween(2999.0, 3001.0);
  }

  @Test
  void shouldRecordLatencyWhenWatchedPendingIncidentPromotes() {
    // given - a pending incident is discovered and watched
    final long incidentKey = Protocol.encodePartitionId(PARTITION, 2);
    meter.start();
    now.set(T0.plusSeconds(10));
    source.discoverable(pending(incidentKey, T0));
    awaitBacklog(1);

    // when - the incident is promoted (now visible as ACTIVE via lookup)
    source.lookupState(active(incidentKey, T0));

    // then - the latency is recorded and the backlog drains
    awaitLatencyCount(1);
    awaitBacklog(0);
    assertThat(latencyTimer().totalTime(TimeUnit.MILLISECONDS)).isBetween(9999.0, 10001.0);
  }

  @Test
  void shouldMeasureLatencyWithWallClockNotNanoTime() {
    // given - the wall clock has advanced 7s past the incident's creation time
    final long incidentKey = Protocol.encodePartitionId(PARTITION, 3);
    meter.start();
    now.set(T0.plusSeconds(7));

    // when
    source.discoverable(active(incidentKey, T0));

    // then - the recorded latency equals the wall-clock interval (creation -> observed), proving
    // the
    // end timestamp comes from the wall clock and not from System.nanoTime()
    awaitLatencyCount(1);
    assertThat(latencyTimer().totalTime(TimeUnit.MILLISECONDS)).isBetween(6999.0, 7001.0);
  }

  @Test
  void shouldClampNegativeLatencyToZero() {
    // given - a creation time in the future relative to the wall clock (clock skew)
    final long incidentKey = Protocol.encodePartitionId(PARTITION, 4);
    meter.start();
    now.set(T0.plusSeconds(5));

    // when - the incident's creation time is after the current wall clock
    source.discoverable(active(incidentKey, T0.plusSeconds(20)));

    // then - the latency is clamped to zero rather than recorded as negative
    awaitLatencyCount(1);
    assertThat(latencyTimer().totalTime(TimeUnit.MILLISECONDS)).isZero();
  }

  @Test
  void shouldRecordTimeoutWhenWatchCapExceeded() {
    // given - a meter with a 5s watch cap and a pending incident already older than the cap
    meter = newMeter(Duration.ofSeconds(5));
    final long incidentKey = Protocol.encodePartitionId(PARTITION, 5);
    meter.start();
    now.set(T0.plusSeconds(8));
    source.discoverable(pending(incidentKey, T0));

    // when - the incident never promotes (lookup keeps returning PENDING)

    // then - the capped elapsed is recorded into the timer and the timeout counter is incremented
    awaitLatencyCount(1);
    await()
        .untilAsserted(
            () ->
                assertThat(
                        registry
                            .get(IncidentResolutionMetricsDoc.RESOLUTION_TIMEOUT.getName())
                            .counter()
                            .count())
                    .isEqualTo(1.0));
    assertThat(latencyTimer().totalTime(TimeUnit.MILLISECONDS))
        .describedAs("the capped watch-cap value is recorded, not the true (larger) elapsed")
        .isBetween(4999.0, 5001.0);
    awaitBacklog(0);
  }

  @Test
  void shouldNotRecordSameIncidentTwice() {
    // given - an ACTIVE incident that keeps being re-discovered (its creation time stays >= cursor)
    final long incidentKey = Protocol.encodePartitionId(PARTITION, 6);
    meter.start();
    now.set(T0.plusSeconds(2));
    source.discoverable(active(incidentKey, T0));

    // when - the meter runs many ticks
    awaitLatencyCount(1);

    // then - the measured set prevents the rediscovered incident from being counted again
    await()
        .during(Duration.ofMillis(200))
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(latencyTimer().count()).isEqualTo(1L));
  }

  @Test
  void shouldAdvanceDiscoveryCursorPastObservedIncidents() {
    // given - an incident created 30s after the initial (start-time) cursor
    final long incidentKey = Protocol.encodePartitionId(PARTITION, 7);
    final OffsetDateTime creationTime = offset(T0.plusSeconds(30));
    meter.start();
    now.set(T0.plusSeconds(60));
    source.discoverable(active(incidentKey, T0.plusSeconds(30)));

    // when
    awaitLatencyCount(1);

    // then - subsequent discovery queries start from the latest observed creation time
    await().untilAsserted(() -> assertThat(source.lastDiscoverFrom()).isEqualTo(creationTime));
  }

  @Test
  void shouldCancelProcessInstanceAfterMeasurement() {
    // given - an incident observed ACTIVE
    final long incidentKey = Protocol.encodePartitionId(PARTITION, 8);
    meter.start();
    now.set(T0.plusSeconds(1));
    source.discoverable(active(incidentKey, T0));

    // when
    awaitLatencyCount(1);

    // then - the stuck instance is cancelled only after its latency has been recorded
    await().untilAsserted(() -> assertThat(source.cancelled()).contains(PROCESS_INSTANCE_KEY));
  }

  @Test
  void shouldTrackPendingBacklogPerPartition() {
    // given - two pending incidents on the same partition
    meter.start();
    now.set(T0.plusSeconds(5));
    source.discoverable(
        pending(Protocol.encodePartitionId(PARTITION, 9), T0),
        pending(Protocol.encodePartitionId(PARTITION, 10), T0));

    // when / then - the backlog gauge reflects the watch-map size for that partition
    awaitBacklog(2);
  }

  private static IncidentSnapshot active(final long incidentKey, final Instant creationTime) {
    return new IncidentSnapshot(
        incidentKey, PROCESS_INSTANCE_KEY, offset(creationTime), IncidentState.ACTIVE);
  }

  private static IncidentSnapshot pending(final long incidentKey, final Instant creationTime) {
    return new IncidentSnapshot(
        incidentKey, PROCESS_INSTANCE_KEY, offset(creationTime), IncidentState.PENDING);
  }

  private static OffsetDateTime offset(final Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private Timer latencyTimer() {
    return registry
        .get(IncidentResolutionMetricsDoc.PENDING_TO_ACTIVE_LATENCY.getName())
        .tag(StarterMetricKeyNames.PARTITION.asString(), Integer.toString(PARTITION))
        .timer();
  }

  private void awaitLatencyCount(final long expected) {
    await().untilAsserted(() -> assertThat(latencyTimer().count()).isEqualTo(expected));
  }

  private void awaitBacklog(final double expected) {
    await()
        .untilAsserted(
            () ->
                assertThat(
                        registry
                            .get(IncidentResolutionMetricsDoc.PENDING_BACKLOG.getName())
                            .tag(
                                StarterMetricKeyNames.PARTITION.asString(),
                                Integer.toString(PARTITION))
                            .gauge()
                            .value())
                    .isEqualTo(expected));
  }

  /** A configurable in-memory {@link IncidentSource} for driving the meter deterministically. */
  private static final class StubIncidentSource implements IncidentSource {

    private final List<IncidentSnapshot> discoverable = new CopyOnWriteArrayList<>();
    private final List<IncidentSnapshot> lookupStates = new CopyOnWriteArrayList<>();
    private final List<Long> cancelled = new CopyOnWriteArrayList<>();
    private final AtomicReference<OffsetDateTime> lastDiscoverFrom = new AtomicReference<>();

    void discoverable(final IncidentSnapshot... snapshots) {
      discoverable.clear();
      discoverable.addAll(List.of(snapshots));
    }

    void lookupState(final IncidentSnapshot snapshot) {
      lookupStates.removeIf(s -> s.incidentKey() == snapshot.incidentKey());
      lookupStates.add(snapshot);
    }

    List<Long> cancelled() {
      return cancelled;
    }

    OffsetDateTime lastDiscoverFrom() {
      return lastDiscoverFrom.get();
    }

    @Override
    public CompletionStage<List<IncidentSnapshot>> discover(
        final OffsetDateTime createdAtOrAfter, final int from, final int limit) {
      lastDiscoverFrom.set(createdAtOrAfter);
      if (from > 0) {
        // single page only: signal no further pages
        return CompletableFuture.completedFuture(List.of());
      }
      final List<IncidentSnapshot> page = new ArrayList<>();
      for (final IncidentSnapshot snapshot : discoverable) {
        if (!snapshot.creationTime().isBefore(createdAtOrAfter)) {
          page.add(snapshot);
        }
      }
      return CompletableFuture.completedFuture(page);
    }

    @Override
    public CompletionStage<List<IncidentSnapshot>> lookup(final List<Long> incidentKeys) {
      final List<IncidentSnapshot> result =
          lookupStates.stream().filter(s -> incidentKeys.contains(s.incidentKey())).toList();
      return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletionStage<Void> cancel(final long processInstanceKey) {
      cancelled.add(processInstanceKey);
      return CompletableFuture.completedFuture(null);
    }
  }
}
