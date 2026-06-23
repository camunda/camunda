/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.zeebe.metrics.StarterMetricsDoc.StarterMetricKeyNames;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Measures the incident-resolution latency — the interval from an incident's {@code creationTime}
 * (engine wall-clock, carried on the incident document) to the moment it is first observed in
 * {@code ACTIVE} state — and surfaces the pending backlog per partition so that a fully stuck
 * partition can be detected.
 *
 * <p>Modeled on {@link ProcessInstanceStartMeter}, with one deliberate divergence: because the
 * start timestamp is an absolute engine wall-clock that rides on the incident document, the latency
 * end timestamp is taken from a wall clock ({@link Instant}), not from {@link System#nanoTime()}.
 *
 * <p>Each tick the meter:
 *
 * <ul>
 *   <li><b>discovers</b> incidents created at or after a {@code creationTime} cursor, over states
 *       {@code {PENDING, ACTIVE}}: an {@code ACTIVE} and unmeasured incident is recorded
 *       immediately (capturing the healthy baseline of incidents born and promoted within one
 *       tick); a {@code PENDING} incident is put in the watch-map;
 *   <li><b>confirms</b> the watch-map by looking incidents up by key in batches; any that flipped
 *       to {@code ACTIVE} is recorded, its process instance enqueued for cancellation, and it is
 *       removed from the watch-map;
 *   <li><b>expires</b> watch-map entries past the watch cap (records the capped elapsed and
 *       increments the timeout counter — survivorship-safe — then drops them);
 *   <li><b>cancels</b> a rate-limited batch of measured process instances (cancel-after-
 *       measurement);
 *   <li><b>refreshes</b> the per-partition backlog gauges.
 * </ul>
 */
public class IncidentResolutionMeter implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(IncidentResolutionMeter.class);
  private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();
  private static final int DISCOVERY_PAGE_SIZE = 100;
  private static final int MAX_DISCOVERY_PAGES = 50;
  private static final int LOOKUP_BATCH_SIZE = 100;

  private final Clock clock;
  private final Supplier<Instant> wallClock;
  private final MeterRegistry registry;
  private final ScheduledExecutorService executor;
  private final Duration checkInterval;
  private final Duration watchCap;
  private final int maxCancellationsPerTick;
  private final IncidentSource incidentSource;

  private final ConcurrentHashMap<Integer, Timer> partitionToLatencyTimer =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Integer, AtomicInteger> backlogByPartition =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Integer, AtomicLong> lastPromotionNanosByPartition =
      new ConcurrentHashMap<>();
  private final Map<Long, WatchedIncident> watchMap = new ConcurrentHashMap<>();
  private final Set<Long> measured = ConcurrentHashMap.newKeySet();
  private final Queue<Long> cancellationQueue = new ConcurrentLinkedQueue<>();
  private final AtomicReference<OffsetDateTime> cursor = new AtomicReference<>();

  private Counter timeoutCounter;

  public IncidentResolutionMeter(
      final Clock clock,
      final Supplier<Instant> wallClock,
      final MeterRegistry registry,
      final ScheduledExecutorService executor,
      final Duration checkInterval,
      final Duration watchCap,
      final double cancelRatePerSecond,
      final IncidentSource incidentSource) {
    this.clock = clock;
    this.wallClock = wallClock;
    this.registry = registry;
    this.executor = executor;
    this.checkInterval = checkInterval;
    this.watchCap = watchCap;
    this.incidentSource = incidentSource;
    maxCancellationsPerTick =
        Math.max(1, (int) Math.round(cancelRatePerSecond * checkInterval.toMillis() / 1000.0));
  }

  /** Starts the periodic discovery and confirmation of incidents. */
  public void start() {
    timeoutCounter =
        Counter.builder(IncidentResolutionMetricsDoc.RESOLUTION_TIMEOUT.getName())
            .description(IncidentResolutionMetricsDoc.RESOLUTION_TIMEOUT.getDescription())
            .register(registry);
    cursor.set(OffsetDateTime.ofInstant(wallClock.get(), ZoneOffset.UTC));
    scheduleCheck(checkInterval.toMillis());
  }

  /** Stops the periodic checking. */
  public void stop() {
    close();
  }

  @Override
  public void close() {
    executor.shutdownNow();
  }

  private void scheduleCheck(final long delayMillis) {
    executor.schedule(this::check, delayMillis, TimeUnit.MILLISECONDS);
  }

  private void check() {
    final long startNanos = clock.getNanos();
    try {
      discover();
      confirm();
      expireStaleWatches();
      drainCancellations();
      refreshBacklogGauges();
    } catch (final Exception e) {
      LOG.error("Failed to check incidents. Will retry...", e);
    } finally {
      rescheduleCheck(clock.getNanos() - startNanos);
    }
  }

  private void rescheduleCheck(final long elapsedNanos) {
    final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
    final long nextDelay = Math.max(0, checkInterval.toMillis() - elapsedMillis);
    scheduleCheck(nextDelay);
  }

  /**
   * Pulls incidents created at or after the cursor, over states {@code {PENDING, ACTIVE}}, sorted
   * by creation time ascending. {@code ACTIVE} & unmeasured are recorded immediately; {@code
   * PENDING} go to the watch-map. The cursor advances to the latest creation time observed;
   * re-scanning the boundary is harmless because the {@code measured} set and the watch-map key
   * deduplicate.
   */
  private void discover() {
    final OffsetDateTime from = cursor.get();
    OffsetDateTime maxCreationTime = from;
    int page = 0;
    boolean more = true;
    while (more && page < MAX_DISCOVERY_PAGES) {
      final List<IncidentSnapshot> incidents =
          join(incidentSource.discover(from, page * DISCOVERY_PAGE_SIZE, DISCOVERY_PAGE_SIZE));
      for (final IncidentSnapshot incident : incidents) {
        ensurePartitionRegistered(incident.partitionId());
        if (incident.creationTime().isAfter(maxCreationTime)) {
          maxCreationTime = incident.creationTime();
        }
        if (incident.state() == IncidentState.ACTIVE) {
          recordPromotion(incident);
        } else if (incident.state() == IncidentState.PENDING
            && !measured.contains(incident.incidentKey())) {
          watchMap.put(incident.incidentKey(), WatchedIncident.from(incident));
        }
      }
      more = incidents.size() == DISCOVERY_PAGE_SIZE;
      page++;
    }
    if (more) {
      LOG.warn(
          "Discovery hit the page cap of {} pages ({} incidents) in a single tick; "
              + "remaining incidents will be picked up on the next tick.",
          MAX_DISCOVERY_PAGES,
          MAX_DISCOVERY_PAGES * DISCOVERY_PAGE_SIZE);
    }
    cursor.set(maxCreationTime);
  }

  /**
   * Re-queries the watched incidents by key in batches and records any that flipped to {@code
   * ACTIVE}.
   */
  private void confirm() {
    if (watchMap.isEmpty()) {
      return;
    }
    final List<Long> keys = new ArrayList<>(watchMap.keySet());
    for (int start = 0; start < keys.size(); start += LOOKUP_BATCH_SIZE) {
      final List<Long> batch =
          keys.subList(start, Math.min(start + LOOKUP_BATCH_SIZE, keys.size()));
      final List<IncidentSnapshot> snapshots = join(incidentSource.lookup(batch));
      for (final IncidentSnapshot snapshot : snapshots) {
        if (snapshot.state() == IncidentState.ACTIVE
            && watchMap.containsKey(snapshot.incidentKey())) {
          recordPromotion(snapshot);
        }
      }
    }
  }

  /**
   * Records the capped elapsed time for incidents that have been pending past the watch cap and
   * increments the timeout counter, then drops them from the watch-map. A timeout is <em>not</em> a
   * promotion, so the partition's last-promotion timestamp is deliberately left untouched.
   */
  private void expireStaleWatches() {
    final Instant now = wallClock.get();
    final List<WatchedIncident> expired = new ArrayList<>();
    for (final WatchedIncident watched : watchMap.values()) {
      if (Duration.between(watched.creationTime().toInstant(), now).compareTo(watchCap) > 0) {
        expired.add(watched);
      }
    }
    for (final WatchedIncident watched : expired) {
      if (watchMap.remove(watched.incidentKey()) == null) {
        continue;
      }
      measured.add(watched.incidentKey());
      latencyTimer(watched.partitionId()).record(watchCap.toMillis(), TimeUnit.MILLISECONDS);
      timeoutCounter.increment();
      LOG.debug(
          "Incident {} on partition {} exceeded the watch cap of {}; recorded as a timeout.",
          watched.incidentKey(),
          watched.partitionId(),
          watchCap);
    }
  }

  private void drainCancellations() {
    for (int i = 0; i < maxCancellationsPerTick; i++) {
      final Long processInstanceKey = cancellationQueue.poll();
      if (processInstanceKey == null) {
        return;
      }
      incidentSource
          .cancel(processInstanceKey)
          .whenComplete(
              (ignored, error) -> {
                if (error != null) {
                  LOG.debug("Failed to cancel process instance {}", processInstanceKey, error);
                }
              });
    }
  }

  private void refreshBacklogGauges() {
    final Map<Integer, Integer> counts = new HashMap<>();
    for (final WatchedIncident watched : watchMap.values()) {
      counts.merge(watched.partitionId(), 1, Integer::sum);
    }
    backlogByPartition.forEach(
        (partitionId, backlog) -> backlog.set(counts.getOrDefault(partitionId, 0)));
  }

  /**
   * Records an observed promotion: the incident-resolution latency (wall-clock now − creation time,
   * clamped to {@code >= 0}) into the per-partition timer, marks the partition's last promotion,
   * deduplicates via the {@code measured} set, enqueues the process instance for cancellation, and
   * removes the incident from the watch-map.
   */
  private void recordPromotion(final IncidentSnapshot incident) {
    if (!measured.add(incident.incidentKey())) {
      watchMap.remove(incident.incidentKey());
      return;
    }
    final long latencyMillis =
        Math.max(
            0, Duration.between(incident.creationTime().toInstant(), wallClock.get()).toMillis());
    latencyTimer(incident.partitionId()).record(latencyMillis, TimeUnit.MILLISECONDS);
    lastPromotionNanos(incident.partitionId()).set(clock.getNanos());
    watchMap.remove(incident.incidentKey());
    cancellationQueue.add(incident.processInstanceKey());
    LOG.debug(
        "Incident {} on partition {} resolved in {} ms",
        incident.incidentKey(),
        incident.partitionId(),
        latencyMillis);
  }

  private Timer latencyTimer(final int partitionId) {
    return partitionToLatencyTimer.computeIfAbsent(
        partitionId,
        key ->
            MicrometerUtil.buildTimer(IncidentResolutionMetricsDoc.PENDING_TO_ACTIVE_LATENCY)
                .tag(StarterMetricKeyNames.PARTITION.asString(), Integer.toString(key))
                .register(registry));
  }

  private AtomicLong lastPromotionNanos(final int partitionId) {
    ensurePartitionRegistered(partitionId);
    return lastPromotionNanosByPartition.get(partitionId);
  }

  /**
   * Lazily registers the per-partition gauges the first time a partition is observed, seeding the
   * last-promotion timestamp to "now" so the promotion-age gauge starts at zero rather than a large
   * value.
   */
  private void ensurePartitionRegistered(final int partitionId) {
    lastPromotionNanosByPartition.computeIfAbsent(
        partitionId,
        key -> {
          final AtomicLong lastPromotion = new AtomicLong(clock.getNanos());
          Gauge.builder(
                  IncidentResolutionMetricsDoc.PARTITION_LAST_PROMOTION_AGE.getName(),
                  lastPromotion,
                  value -> (clock.getNanos() - value.get()) / (double) NANOS_PER_SECOND)
              .description(
                  IncidentResolutionMetricsDoc.PARTITION_LAST_PROMOTION_AGE.getDescription())
              .tag(StarterMetricKeyNames.PARTITION.asString(), Integer.toString(key))
              .register(registry);
          return lastPromotion;
        });
    backlogByPartition.computeIfAbsent(
        partitionId,
        key -> {
          final AtomicInteger backlog = new AtomicInteger(0);
          Gauge.builder(
                  IncidentResolutionMetricsDoc.PENDING_BACKLOG.getName(),
                  backlog,
                  AtomicInteger::doubleValue)
              .description(IncidentResolutionMetricsDoc.PENDING_BACKLOG.getDescription())
              .tag(StarterMetricKeyNames.PARTITION.asString(), Integer.toString(key))
              .register(registry);
          return backlog;
        });
  }

  private static <T> T join(final java.util.concurrent.CompletionStage<T> stage) {
    return stage.toCompletableFuture().join();
  }

  /** A pending incident the meter is watching until it flips to {@code ACTIVE} or times out. */
  private record WatchedIncident(
      long incidentKey, long processInstanceKey, OffsetDateTime creationTime, int partitionId) {
    static WatchedIncident from(final IncidentSnapshot incident) {
      return new WatchedIncident(
          incident.incidentKey(),
          incident.processInstanceKey(),
          incident.creationTime(),
          incident.partitionId());
    }
  }

  /** A minimal view of an incident as seen by the meter. */
  public record IncidentSnapshot(
      long incidentKey, long processInstanceKey, OffsetDateTime creationTime, IncidentState state) {

    public int partitionId() {
      return Protocol.decodePartitionId(incidentKey);
    }
  }

  /**
   * Decouples the meter from the search/command client. Implementations issue the actual incident
   * search and cancel commands against the cluster.
   */
  public interface IncidentSource {

    /**
     * Discovers incidents created at or after the given time, over states {@code {PENDING,
     * ACTIVE}}, sorted by creation time ascending, returning the page starting at {@code from} with
     * at most {@code limit} items.
     */
    java.util.concurrent.CompletionStage<List<IncidentSnapshot>> discover(
        OffsetDateTime createdAtOrAfter, int from, int limit);

    /** Looks up the current state of the given incident keys. */
    java.util.concurrent.CompletionStage<List<IncidentSnapshot>> lookup(List<Long> incidentKeys);

    /** Cancels the given process instance (cancel-after-measurement cleanup). */
    java.util.concurrent.CompletionStage<Void> cancel(long processInstanceKey);
  }
}
