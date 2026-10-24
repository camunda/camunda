/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import io.camunda.zeebe.metrics.StarterMetricsDoc.StarterMetricKeyNames;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
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
  private final ConcurrentHashMap<Integer, Counter> partitionToTimedOutCounterMap;
  private final Map<Long, PiCreationResult> startedInstances;
  private final ScheduledExecutorService piCheckExecutorService;
  private final AvailabilityChecker availabilityChecker;
  private final MeterRegistry registry;
  private final Duration availabilityCheckInterval;
  private final Clock clock;
  private final int pageSize;
  private final Timer dataAvailabilityQueryDurationTimer;
  private final Counter fullPageCounter;

  public ProcessInstanceStartMeter(
      final Clock clock,
      final MeterRegistry meterRegistry,
      final ScheduledExecutorService scheduledExecutorService,
      final Duration availabilityCheckInterval,
      final int pageSize,
      final AvailabilityChecker availabilityChecker) {
    this.clock = clock;
    registry = meterRegistry;
    partitionToTimerMap = new ConcurrentHashMap<>();
    partitionToTimedOutCounterMap = new ConcurrentHashMap<>();
    startedInstances = new ConcurrentHashMap<>();
    piCheckExecutorService = scheduledExecutorService;
    this.pageSize = pageSize;
    dataAvailabilityQueryDurationTimer =
        MicrometerUtil.buildTimer(StarterLatencyMetricsDoc.DATA_AVAILABILITY_QUERY_DURATION)
            .register(registry);
    fullPageCounter =
        Counter.builder(StarterLatencyMetricsDoc.DATA_AVAILABILITY_FULL_PAGE.getName())
            .description(StarterLatencyMetricsDoc.DATA_AVAILABILITY_FULL_PAGE.getDescription())
            .register(registry);
    this.availabilityCheckInterval = availabilityCheckInterval;
    this.availabilityChecker = availabilityChecker;
    MicrometerUtil.buildGauge(
            StarterLatencyMetricsDoc.PENDING_PROCESS_INSTANCES, startedInstances::size)
        .register(registry);
  }

  /** Starts the periodic checking for process instance availability. */
  public void start() {
    scheduleCheck(availabilityCheckInterval.toMillis());
  }

  private void scheduleCheck(final long delay) {
    piCheckExecutorService.schedule(
        () -> {
          try {
            checkForProcessInstances();
          } catch (final Exception e) {
            LOG.error("Failed to check process instances. Will retry...", e);
            rescheduleCheck(0);
          }
        },
        delay,
        TimeUnit.MILLISECONDS);
  }

  /**
   * Reschedule the process instance check with a delay computed based on the elapsed time, which is
   * recorded for the previous query duration (in nanoseconds).
   *
   * <p>If the query already took longer then the check interval, we immediately retrigger the
   * check.
   *
   * @param elapsedTime the elapsed time (duration) of the previous query in nanosecond
   */
  private void rescheduleCheck(final long elapsedTime) {
    // We only reschedule another check AFTER the previous is done
    // This is to avoid multiple queries running at the same time, with quering the same
    // data. Especially if the response takes longer than our schedule interval there is no
    // point in running again in between.
    // If responses are slow - we will time out the active instances eventually
    final var nextDelay =
        TimeUnit.NANOSECONDS.toMillis(elapsedTime) > availabilityCheckInterval.toMillis()
            ? 0
            : availabilityCheckInterval.toMillis() - TimeUnit.NANOSECONDS.toMillis(elapsedTime);
    scheduleCheck(nextDelay);
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
      rescheduleCheck(0);
      return;
    }

    LOG.debug("Current instances awaiting {}", startedInstances.size());
    final var startQueryTime = clock.getNanos();
    availabilityChecker
        .findAvailableInstances(List.copyOf(startedInstances.keySet()))
        .whenCompleteAsync(
            (availableInstances, error) -> {
              final var endQueryTime = clock.getNanos();
              final long duration = endQueryTime - startQueryTime;
              try {
                dataAvailabilityQueryDurationTimer.record(duration, TimeUnit.NANOSECONDS);

                if (error == null) {
                  LOG.debug("Available process instances items: {}", availableInstances.size());
                  if (availableInstances.size() >= pageSize) {
                    fullPageCounter.increment();
                  }
                  processAvailableInstances(availableInstances);
                } else {
                  LOG.error("Error while checking for available process instances", error);
                }

                cleanUpStaleInstances();
              } catch (final Exception e) {
                // Guard the callback: without this, an exception here would prevent the next check
                // from ever being scheduled and the meter would silently stop.
                LOG.error("Failed to process availability check response. Will retry...", e);
              } finally {
                rescheduleCheck(duration);
              }
            },
            piCheckExecutorService);
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
          // Still record the latency so a plateau at the limit stays visible, and in addition count
          // the timeout separately so true timeouts are not confused with instances found at the
          // limit.
          recordTimedOut(piResults);
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

  private void recordTimedOut(final PiCreationResult awaitingPI) {
    final int partitionId = Protocol.decodePartitionId(awaitingPI.processInstanceKey);
    partitionToTimedOutCounterMap
        .computeIfAbsent(
            partitionId,
            key ->
                Counter.builder(StarterLatencyMetricsDoc.DATA_AVAILABILITY_TIMED_OUT.getName())
                    .description(
                        StarterLatencyMetricsDoc.DATA_AVAILABILITY_TIMED_OUT.getDescription())
                    .tag(StarterMetricKeyNames.PARTITION.asString(), Integer.toString(key))
                    .register(registry))
        .increment();
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
