/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.metrics.StarterMetricsDoc.StarterMetricKeyNames;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.starter.ProcessInstanceCreator;
import io.camunda.zeebe.starter.StarterFinished;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Profile("starter")
@ConditionalOnProperty(
    name = "load-tester.monitor-data-availability",
    havingValue = "true",
    matchIfMissing = true)
public class ProcessInstanceStartMeter implements AutoCloseable {
  /**
   * Maximum number of pending process instance keys sent per availability check. A search returns
   * at most one page of instances, so sending more keys than fit on a page only inflates the query
   * cost (see https://github.com/camunda/camunda/issues/65373) without answering them. The search
   * in {@code searchStartedInstances} uses it as its page limit.
   */
  public static final int AVAILABILITY_CHECK_BATCH_SIZE = 2500;

  private static final long MAX_DURATION = Duration.ofSeconds(90).toNanos();
  private static final Logger LOG = LoggerFactory.getLogger(ProcessInstanceStartMeter.class);

  private final ConcurrentHashMap<Integer, Timer> partitionToTimerMap;
  // Pending instances per partition, so a partition whose data is slow to become available cannot
  // crowd the others out of the availability check. Each queue is roughly oldest first: instances
  // are added when their create response arrives.
  private final ConcurrentHashMap<Integer, LinkedBlockingDeque<PiCreationResult>>
      pendingByPartition;
  private final ScheduledExecutorService piCheckExecutorService;
  private final AvailabilityChecker availabilityChecker;
  private final MeterRegistry registry;
  private final Duration availabilityCheckInterval;
  private final Clock clock;
  private final Timer dataAvailabilityQueryDurationTimer;

  @Autowired
  public ProcessInstanceStartMeter(
      final MeterRegistry meterRegistry,
      final LoadTesterProperties properties,
      @Qualifier("queryCamundaClient") final CamundaClient queryClient,
      final ProcessInstanceCreator processInstanceCreator) {
    this(
        System::nanoTime,
        meterRegistry,
        Executors.newScheduledThreadPool(1),
        properties.getMonitorDataAvailabilityInterval(),
        startedInstances -> searchStartedInstances(queryClient, startedInstances));
    processInstanceCreator.addStartListener(this::recordProcessInstanceStart);
  }

  // Could be merged with the constructor above if the clock, the executor and the
  // availability checker were beans.
  public ProcessInstanceStartMeter(
      final Clock clock,
      final MeterRegistry meterRegistry,
      final ScheduledExecutorService scheduledExecutorService,
      final Duration availabilityCheckInterval,
      final AvailabilityChecker availabilityChecker) {
    this.clock = clock;
    registry = meterRegistry;
    partitionToTimerMap = new ConcurrentHashMap<>();
    pendingByPartition = new ConcurrentHashMap<>();
    piCheckExecutorService = scheduledExecutorService;
    dataAvailabilityQueryDurationTimer =
        MicrometerUtil.buildTimer(StarterLatencyMetricsDoc.DATA_AVAILABILITY_QUERY_DURATION)
            .register(registry);
    this.availabilityCheckInterval = availabilityCheckInterval;
    this.availabilityChecker = availabilityChecker;
    MicrometerUtil.buildGauge(
            StarterLatencyMetricsDoc.PENDING_PROCESS_INSTANCES, this::pendingCount)
        .register(registry);
  }

  /** Starts the periodic checking for process instance availability. */
  @EventListener(ApplicationReadyEvent.class)
  public void start() {
    scheduleCheck(availabilityCheckInterval.toMillis());
  }

  private static CompletionStage<List<Long>> searchStartedInstances(
      final CamundaClient queryClient, final List<Long> startedInstances) {
    return queryClient
        .newProcessInstanceSearchRequest()
        .filter((f) -> f.processInstanceKey(key -> key.in(startedInstances)))
        .sort(s -> s.startDate().asc())
        .page(p -> p.limit(AVAILABILITY_CHECK_BATCH_SIZE))
        .send()
        .thenApply(
            response ->
                response.items().stream().map(ProcessInstance::getProcessInstanceKey).toList());
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
  @EventListener(StarterFinished.class)
  public void close() {
    piCheckExecutorService.shutdownNow();
  }

  private void checkForProcessInstances() {
    final var batchKeys = selectBatchKeys();
    if (batchKeys.isEmpty()) {
      LOG.debug("No instances awaiting, skip check for process instances.");
      rescheduleCheck(0);
      return;
    }

    LOG.debug("Current instances awaiting {}, checking {}", pendingCount(), batchKeys.size());
    final var startQueryTime = clock.getNanos();
    availabilityChecker
        .findAvailableInstances(batchKeys)
        .whenCompleteAsync(
            (availableInstances, error) -> {
              final var endQueryTime = clock.getNanos();
              final long duration = endQueryTime - startQueryTime;
              dataAvailabilityQueryDurationTimer.record(duration, TimeUnit.NANOSECONDS);

              // The reschedule must always run, or the meter silently stops. Processing and
              // cleanup are guarded separately, so a response that keeps failing to process
              // cannot stop stale instances from being evicted.
              boolean checkAgainImmediately = false;
              try {
                try {
                  final boolean resultPageFull =
                      error == null && availableInstances.size() >= AVAILABILITY_CHECK_BATCH_SIZE;
                  if (error == null) {
                    LOG.debug("Available process instances items: {}", availableInstances.size());
                    processAvailableInstances(availableInstances);
                  } else {
                    LOG.error("Error while checking for available process instances", error);
                  }
                  checkAgainImmediately = resultPageFull;
                } catch (final Exception e) {
                  LOG.error("Failed to process availability check response. Will retry...", e);
                }

                cleanUpStaleInstances();
              } catch (final Exception e) {
                LOG.error("Failed to clean up stale instances. Will retry...", e);
              } finally {
                if (checkAgainImmediately) {
                  // every queried instance was available, so more are likely available beyond the
                  // batch: don't wait for the interval. Otherwise (errors, data not yet
                  // searchable, few confirmed) keep the interval to not hammer a struggling
                  // backend.
                  scheduleCheck(0);
                } else {
                  rescheduleCheck(duration);
                }
              }
            },
            piCheckExecutorService);
  }

  /**
   * Selects the keys to check: the oldest pending instances, as they have been pending the longest
   * and are the most likely to be available (see {@link #AVAILABILITY_CHECK_BATCH_SIZE} for why the
   * batch is capped).
   *
   * <p>The batch is shared fairly between the partitions, so a partition whose data is slow to
   * become available cannot crowd the others out. A partition with fewer pending instances than its
   * share leaves the rest to the partitions with more, so the batch is full whenever at least
   * {@link #AVAILABILITY_CHECK_BATCH_SIZE} instances are pending.
   */
  private List<Long> selectBatchKeys() {
    final var queues =
        pendingByPartition.values().stream()
            .filter(queue -> !queue.isEmpty())
            .sorted(Comparator.comparingInt(LinkedBlockingDeque::size))
            .toList();
    final var batchKeys = new ArrayList<Long>();
    for (int i = 0; i < queues.size(); i++) {
      final int partitionsLeft = queues.size() - i;
      final int remaining = AVAILABILITY_CHECK_BATCH_SIZE - batchKeys.size();
      final int share = (remaining + partitionsLeft - 1) / partitionsLeft;
      queues.get(i).stream()
          .limit(share)
          .map(PiCreationResult::processInstanceKey)
          .forEach(batchKeys::add);
    }
    return batchKeys;
  }

  private int pendingCount() {
    return pendingByPartition.values().stream().mapToInt(LinkedBlockingDeque::size).sum();
  }

  private void removePending(final Predicate<PiCreationResult> shouldRemove) {
    pendingByPartition.values().forEach(queue -> queue.removeIf(shouldRemove));
  }

  private void cleanUpStaleInstances() {
    // clean up stale instances which exceeded the max duration - to save memory
    final long nanoTime = clock.getNanos();
    // Instances are queued in response order, which is not strictly start order, so check them all
    removePending(
        piResults -> {
          if (nanoTime - piResults.startTimeNanos <= MAX_DURATION) {
            return false;
          }
          final long durationNanos = clock.getNanos() - piResults.startTimeNanos;
          LOG.debug(
              "Process instance {} was not retrieved after {} ms, removing it from the awaiting list.",
              piResults.processInstanceKey,
              TimeUnit.NANOSECONDS.toMillis(durationNanos));
          recordInstanceAvailable(piResults, durationNanos);
          return true;
        });
  }

  private void processAvailableInstances(final List<Long> availableInstances) {
    final var availableKeys = Set.copyOf(availableInstances);
    removePending(
        piResults -> {
          if (!availableKeys.contains(piResults.processInstanceKey)) {
            return false;
          }
          final long durationNanos = clock.getNanos() - piResults.startTimeNanos;
          LOG.debug(
              "Process instance {} retrieved in {} ms",
              piResults.processInstanceKey,
              TimeUnit.NANOSECONDS.toMillis(durationNanos));
          recordInstanceAvailable(piResults, durationNanos);
          return true;
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
  }

  public void recordProcessInstanceStart(final long processInstanceKey, final long startTimeNanos) {
    pendingByPartition
        .computeIfAbsent(
            Protocol.decodePartitionId(processInstanceKey),
            partition -> new LinkedBlockingDeque<>())
        .add(new PiCreationResult(processInstanceKey, startTimeNanos));
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
