/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import io.camunda.zeebe.StarterLatencyMetricsDoc.StarterMetricKeyNames;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessInstanceStartMeter implements AutoCloseable {
  private static final long MAX_DURATION = Duration.ofSeconds(90).toNanos();
  private static final Logger LOG = LoggerFactory.getLogger(ProcessInstanceStartMeter.class);
  private final ConcurrentHashMap<Integer, Timer> partitionToTimerMap;
  private final Map<Long, PiCreationResult> startedInstances;
  private final ScheduledExecutorService piCheckExecutorService;
  private final AvailabilityChecker availabilityChecker;
  private final MeterRegistry registry;
  private final Duration availabilityCheckInterval;
  private final Clock clock;
  private final Timer dataAvailabilityQueryDurationTimer;

  public ProcessInstanceStartMeter(
      final Clock clock,
      final MeterRegistry meterRegistry,
      final ScheduledExecutorService scheduledExecutorService,
      final Duration availabilityCheckInterval,
      final AvailabilityChecker availabilityChecker) {
    this.clock = clock;
    registry = meterRegistry;
    partitionToTimerMap = new ConcurrentHashMap<>();
    startedInstances = new ConcurrentHashMap<>();
    piCheckExecutorService = scheduledExecutorService;
    dataAvailabilityQueryDurationTimer =
        MicrometerUtil.buildTimer(StarterLatencyMetricsDoc.DATA_AVAILABILITY_QUERY_DURATION)
            .register(registry);
    this.availabilityCheckInterval = availabilityCheckInterval;
    this.availabilityChecker = availabilityChecker;
  }

  /** Starts the periodic checking for process instance availability. */
  public void start() {
    piCheckExecutorService.scheduleAtFixedRate(
        () -> {
          try {
            checkForProcessInstances();
          } catch (final Exception e) {
            LOG.error("Failed to check process instances. Will retry...", e);
          }
        },
        availabilityCheckInterval.toMillis(),
        availabilityCheckInterval.toMillis(),
        TimeUnit.MILLISECONDS);
  }

  /** Stops the periodic checking for process instance availability. */
  public void stop() {
    close();
  }

  @Override
  public void close() {
    piCheckExecutorService.shutdownNow();
  }

  private void checkForProcessInstances() {
    if (startedInstances.isEmpty()) {
      LOG.debug("No instances awaiting, skip check for process instances.");
      return;
    }

    LOG.debug("Current instances awaiting {}", startedInstances.size());
    final List<Long> availableInstances;
    final var startQueryTime = clock.getNanos();
    try {
      availableInstances =
          availabilityChecker
              .findAvailableInstances(List.copyOf(startedInstances.keySet()))
              .toCompletableFuture()
              .join();
    } finally {
      final var endQueryTime = clock.getNanos();
      dataAvailabilityQueryDurationTimer.record(
          endQueryTime - startQueryTime, TimeUnit.NANOSECONDS);
    }
    LOG.debug("Available process instances items: {}", availableInstances.size());
    processAvailableInstances(availableInstances);
    cleanUpStaleInstances();
  }

  private void cleanUpStaleInstances() {
    // clean up stale instances which exceeded the max duration - to save memory
    final long nanoTime = clock.getNanos();
    final var instancesWhereTimeExceededDeadline =
        startedInstances.values().stream()
            .filter(piCreationResult -> nanoTime - piCreationResult.startTimeNanos > MAX_DURATION)
            .toList();
    instancesWhereTimeExceededDeadline.forEach(
        piResults -> {
          final long durationNanos = clock.getNanos() - piResults.startTimeNanos;
          LOG.debug(
              "Process instance {} was not retrieved after {} ms, removing it from the awaiting list.",
              piResults.processInstanceKey,
              TimeUnit.NANOSECONDS.toMillis(durationNanos));
          recordInstanceAvailable(piResults, durationNanos);
        });
  }

  private void processAvailableInstances(final List<Long> availableInstances) {
    availableInstances.stream()
        .map(startedInstances::get)
        .filter(Objects::nonNull)
        .forEach(
            piResults -> {
              final long durationNanos = clock.getNanos() - piResults.startTimeNanos;
              LOG.debug(
                  "Process instance {} retrieved in {} ms",
                  piResults.processInstanceKey,
                  TimeUnit.NANOSECONDS.toMillis(durationNanos));
              recordInstanceAvailable(piResults, durationNanos);
            });
  }

  private void recordInstanceAvailable(
      final PiCreationResult awaitingPI, final long durationNanos) {
    final int partitionId = Protocol.decodePartitionId(awaitingPI.processInstanceKey);
    partitionToTimerMap
        .computeIfAbsent(
            partitionId,
            key ->
                MicrometerUtil.buildTimer(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY)
                    .tag(StarterMetricKeyNames.PARTITION.asString(), Integer.toString(key))
                    .register(registry))
        .record(durationNanos, TimeUnit.NANOSECONDS);
    startedInstances.remove(awaitingPI.processInstanceKey);
  }

  public void recordProcessInstanceStart(final long processInstanceKey, final long startTimeNanos) {
    startedInstances.put(
        processInstanceKey, new PiCreationResult(processInstanceKey, startTimeNanos));
  }

  private record PiCreationResult(long processInstanceKey, long startTimeNanos) {}

  /**
   * Interface to check the availability of process instances.
   *
   * <p>Used to decouple the availability checking logic from the ProcessInstanceStartMeter.
   */
  public interface AvailabilityChecker {
    /**
     * Finds the process instances that are available from the given list of process instance keys.
     *
     * @param processInstanceKeys the list of process instance keys to check
     * @return a CompletionStage that, when completed, provides the list of available process
     *     instance keys
     */
    CompletionStage<List<Long>> findAvailableInstances(List<Long> processInstanceKeys);
  }
}
