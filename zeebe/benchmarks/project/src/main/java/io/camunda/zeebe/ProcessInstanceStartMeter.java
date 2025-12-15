/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessInstanceStartMeter implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessInstanceStartMeter.class);
  private final ConcurrentHashMap<Integer, Timer> partitionToTimerMap;
  private final CopyOnWriteArrayList<PiCreationResult> startedInstances;
  private final ScheduledExecutorService piCheckExecutorService;
  private final AvailabilityChecker availabilityChecker;
  private final MeterRegistry registry;
  private final Duration availabilityCheckInterval;

  public ProcessInstanceStartMeter(
      final MeterRegistry meterRegistry,
      final ScheduledExecutorService scheduledExecutorService,
      final Duration availabilityCheckInterval,
      final AvailabilityChecker availabilityChecker) {
    registry = meterRegistry;
    partitionToTimerMap = new ConcurrentHashMap<>();
    startedInstances = new CopyOnWriteArrayList<>();
    piCheckExecutorService = scheduledExecutorService;
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
    final List<Long> list = startedInstances.stream().map(k -> k.processInstanceKey).toList();
    availabilityChecker
        .findAvailableInstances(list)
        .whenCompleteAsync(
            (availableInstances, error) -> {
              if (error != null) {
                LOG.error("Error while checking for available process instances", error);
                return;
              }

              checkAvailableInstances(availableInstances);
            },
            piCheckExecutorService);
  }

  private void checkAvailableInstances(final List<Long> availableInstances) {
    LOG.debug("Available process instances items: {} ", availableInstances.size());
    for (final Long availableInstanceKey : availableInstances) {
      LOG.debug("Available process instance key: {} ", availableInstanceKey);

      for (final PiCreationResult awaitingPI : Collections.unmodifiableList(startedInstances)) {
        if (awaitingPI.processInstanceKey == availableInstanceKey) {
          recordInstanceAvailable(awaitingPI);
          break;
        }
      }
    }
  }

  private void recordInstanceAvailable(final PiCreationResult awaitingPI) {
    final long durationNanos = System.nanoTime() - awaitingPI.startTimeNanos;
    LOG.debug(
        "Process instance {} retrieved in {} ms",
        awaitingPI.processInstanceKey,
        TimeUnit.NANOSECONDS.toMillis(durationNanos));

    final int partitionId = Protocol.decodePartitionId(awaitingPI.processInstanceKey);
    partitionToTimerMap
        .computeIfAbsent(
            partitionId,
            key ->
                MicrometerUtil.buildTimer(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY)
                    .tag("partition", Integer.toString(key))
                    .register(registry))
        .record(durationNanos, TimeUnit.NANOSECONDS);
    startedInstances.remove(awaitingPI);
  }

  public void recordProcessInstanceStart(final long processInstanceKey, final long startTimeNanos) {
    startedInstances.add(new PiCreationResult(processInstanceKey, startTimeNanos));
  }

  record PiCreationResult(long processInstanceKey, long startTimeNanos) {}

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
