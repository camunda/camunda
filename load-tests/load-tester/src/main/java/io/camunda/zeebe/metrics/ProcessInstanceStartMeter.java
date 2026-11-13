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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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
  private final ConcurrentHashMap<Integer, Counter> partitionToTimedOutCounterMap;
  // Roughly oldest first: instances are added when their create response arrives.
  private final LinkedBlockingQueue<PiCreationResult> pendingInstances;
  private final ScheduledExecutorService piCheckExecutorService;
  private final AvailabilityChecker availabilityChecker;
  private final MeterRegistry registry;
  private final Duration availabilityCheckInterval;
  private final Clock clock;
  private final Timer dataAvailabilityQueryDurationTimer;
  private final int samplingStride;
  private final ConcurrentHashMap<Integer, AtomicLong> startedPerPartition =
      new ConcurrentHashMap<>();
  private final Counter fullPageCounter;

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
        samplingStride(
            properties.getStarter().getRatePerSecond(),
            properties.getMonitorDataAvailabilitySamplingPercentage()),
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
    this(
        clock,
        meterRegistry,
        scheduledExecutorService,
        availabilityCheckInterval,
        1,
        availabilityChecker);
  }

  /**
   * @param samplingStride only every n-th started instance is tracked, see {@link
   *     #samplingStride(double, double)}; 1 tracks every instance
   */
  public ProcessInstanceStartMeter(
      final Clock clock,
      final MeterRegistry meterRegistry,
      final ScheduledExecutorService scheduledExecutorService,
      final Duration availabilityCheckInterval,
      final int samplingStride,
      final AvailabilityChecker availabilityChecker) {
    this.clock = clock;
    registry = meterRegistry;
    partitionToTimerMap = new ConcurrentHashMap<>();
    partitionToTimedOutCounterMap = new ConcurrentHashMap<>();
    pendingInstances = new LinkedBlockingQueue<>();
    if (samplingStride < 1) {
      throw new IllegalArgumentException("The sampling stride must be at least 1");
    }
    this.samplingStride = samplingStride;
    piCheckExecutorService = scheduledExecutorService;
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
            StarterLatencyMetricsDoc.PENDING_PROCESS_INSTANCES, pendingInstances::size)
        .register(registry);
    MicrometerUtil.buildGauge(
            StarterLatencyMetricsDoc.SAMPLING_PERCENTAGE, () -> 100.0 / this.samplingStride)
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

    LOG.debug(
        "Current instances awaiting {}, checking {}", pendingInstances.size(), batchKeys.size());
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
                  if (resultPageFull) {
                    fullPageCounter.increment();
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
   * <p>With sampling, the pending set stays around one batch at the default sampling. At much
   * higher loads, or without sampling, a stalled partition can hold the whole batch until its
   * instances time out and delay the check of instances behind it.
   */
  private List<Long> selectBatchKeys() {
    return pendingInstances.stream()
        .limit(AVAILABILITY_CHECK_BATCH_SIZE)
        .map(PiCreationResult::processInstanceKey)
        .toList();
  }

  private void cleanUpStaleInstances() {
    // clean up stale instances which exceeded the max duration - to save memory
    final long nanoTime = clock.getNanos();
    // Instances are queued in response order, which is not strictly start order, so check them all
    pendingInstances.removeIf(
        piResults -> {
          if (nanoTime - piResults.startTimeNanos <= MAX_DURATION) {
            return false;
          }
          final long durationNanos = clock.getNanos() - piResults.startTimeNanos;
          LOG.debug(
              "Process instance {} was not retrieved after {} ms, removing it from the awaiting list.",
              piResults.processInstanceKey,
              TimeUnit.NANOSECONDS.toMillis(durationNanos));
          // Still record the latency so a plateau at the limit stays visible, and count the timeout
          // separately so it is not mistaken for an instance found at the limit.
          recordTimedOut(piResults);
          recordInstanceAvailable(piResults, durationNanos);
          return true;
        });
  }

  private void processAvailableInstances(final List<Long> availableInstances) {
    final var availableKeys = Set.copyOf(availableInstances);
    pendingInstances.removeIf(
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
  }

  /**
   * Computes the stride n for tracking every n-th started instance, so that about {@code
   * samplingPercentage} percent of the instances the starter creates at {@code ratePerSecond} are
   * tracked, and at least one per second, so a low load stays fully covered. Under backpressure the
   * starter creates fewer instances than configured, and proportionally fewer are tracked.
   *
   * <p>Example: 300 instances per second at 10% is 30 tracked instances per second, so every 10th
   * instance is tracked.
   */
  public static int samplingStride(final double ratePerSecond, final double samplingPercentage) {
    final double percentage = Math.max(0, Math.min(100, samplingPercentage));
    final double trackedPerSecond = Math.max(1, Math.ceil(ratePerSecond * percentage / 100));
    return (int) Math.max(1, Math.floor(ratePerSecond / trackedPerSecond));
  }

  /**
   * Tracks the availability of every n-th started instance of each partition (see {@link
   * #samplingStride(double, double)}). A sample answers the same question about the latency
   * distribution as all instances, while the pending set does not grow with the load. The counter
   * is per partition, as a single one would only track some partitions when the stride and the
   * partition count share a factor, because instances are distributed round-robin.
   */
  public void recordProcessInstanceStart(final long processInstanceKey, final long startTimeNanos) {
    final var started =
        startedPerPartition.computeIfAbsent(
            Protocol.decodePartitionId(processInstanceKey), partition -> new AtomicLong());
    if (started.getAndIncrement() % samplingStride != 0) {
      return;
    }
    pendingInstances.add(new PiCreationResult(processInstanceKey, startTimeNanos));
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
