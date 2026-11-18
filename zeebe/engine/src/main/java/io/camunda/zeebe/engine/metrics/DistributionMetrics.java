/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.util.collection.Table;
import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class DistributionMetrics {

  /** Tag value for distributions which are not part of any queue. */
  private static final String NO_QUEUE = "NONE";

  /**
   * Tag value for queues which are not declared in {@link DistributionQueue}. Queue ids can also be
   * user-controlled (e.g. cluster variable names), so they must never be used as a tag value as
   * that would make the number of time series unbounded.
   */
  private static final String OTHER_QUEUE = "OTHER";

  private static final Set<String> KNOWN_QUEUES =
      Arrays.stream(DistributionQueue.getValues())
          .map(DistributionQueue::getQueueId)
          .collect(Collectors.toUnmodifiableSet());

  private final MeterRegistry meterRegistry;

  /** Metrics by target partition id and (bounded) queue tag. */
  private final Table<Integer, String, PartitionDistributionMetrics> partitionMetrics =
      Table.concurrent();

  // Metrics
  private final StatefulGauge activeDistributionsGauge;
  private final Counter distributionsCounter;

  public DistributionMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;

    activeDistributionsGauge =
        StatefulGauge.builder(DistributionMetricsDoc.ACTIVE_COMMAND_DISTRIBUTIONS.getName())
            .description(DistributionMetricsDoc.ACTIVE_COMMAND_DISTRIBUTIONS.getDescription())
            .register(meterRegistry);

    distributionsCounter =
        Counter.builder(DistributionMetricsDoc.COMMAND_DISTRIBUTIONS.getName())
            .description(DistributionMetricsDoc.COMMAND_DISTRIBUTIONS.getDescription())
            .register(meterRegistry);
  }

  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

  public void reset() {
    activeDistributionsGauge.set(0);
    partitionMetrics.forEachValue(PartitionDistributionMetrics::resetGauges);
  }

  public void startedDistribution() {
    distributionsCounter.increment();
  }

  public void addActiveDistribution() {
    activeDistributionsGauge.increment();
  }

  public void removeActiveDistribution() {
    activeDistributionsGauge.decrement();
  }

  public void addPendingDistribution(final int targetPartitionId, final @Nullable String queueId) {
    getPartitionMetrics(targetPartitionId, queueId).addPendingDistribution();
  }

  public void removePendingDistribution(
      final int targetPartitionId, final @Nullable String queueId) {
    getPartitionMetrics(targetPartitionId, queueId).removePendingDistribution();
  }

  public void addInflightDistribution(final int targetPartitionId, final @Nullable String queueId) {
    getPartitionMetrics(targetPartitionId, queueId).addInflightDistribution();
  }

  public void removeInflightDistribution(
      final int targetPartitionId, final @Nullable String queueId) {
    getPartitionMetrics(targetPartitionId, queueId).removeInflightDistribution();
  }

  /**
   * This method is called on the target partition when a distribution command has been successfully
   * processed and an acknowledgement is sent back to the origin partition.
   *
   * <p>The queue is only known to the origin partition, so this is always tagged with {@link
   * #NO_QUEUE}.
   *
   * @param originPartitionId the source partition id of the distribution (where the distribution
   *     was started)
   */
  public void sentAcknowledgeDistribution(final int originPartitionId) {
    getPartitionMetrics(originPartitionId, null).sentAcknowledgeDistribution();
  }

  /**
   * This method is called on the origin partition when a distribution command has been successfully
   * acknowledged by the target partition.
   *
   * @param targetPartitionId the source partition id of the distribution (where the distribution
   *     was started)
   */
  public void receivedAcknowledgeDistribution(
      final int targetPartitionId, final @Nullable String queueId) {
    getPartitionMetrics(targetPartitionId, queueId).receivedAcknowledgeDistribution();
  }

  /**
   * This method is called when a current inflight distribution is being retried. This may happen if
   * the acknowledgement of the target partition is not received by the origin partition in-time.
   * See {@link io.camunda.zeebe.engine.processing.distribution.CommandRedistributionScheduler}.
   *
   * @param targetPartitionId the target partition id of the distribution
   */
  public void retryInflightDistribution(
      final int targetPartitionId, final @Nullable String queueId) {
    getPartitionMetrics(targetPartitionId, queueId).retryInflightDistribution();
  }

  private PartitionDistributionMetrics getPartitionMetrics(
      final int targetPartitionId, final @Nullable String queueId) {
    return partitionMetrics.computeIfAbsent(
        targetPartitionId,
        toQueueTag(queueId),
        (partitionId, queueTag) ->
            new PartitionDistributionMetrics(partitionId, queueTag, meterRegistry));
  }

  /** Maps the queue id to a tag value out of a bounded set: known queues, NONE, or OTHER. */
  private static String toQueueTag(final @Nullable String queueId) {
    if (queueId == null) {
      return NO_QUEUE;
    }
    return KNOWN_QUEUES.contains(queueId) ? queueId : OTHER_QUEUE;
  }

  private static class PartitionDistributionMetrics {

    private final MeterRegistry meterRegistry;
    private final int targetPartitionId;
    private final String queueId;

    // Metrics
    private final StatefulGauge pendingDistributionsGauge;
    private final StatefulGauge inflightDistributionsGauge;
    private final Counter retryInflightDistributionsCounter;
    private final Counter receivedAcknowledgeDistributionsCounter;
    private final Counter sentAcknowledgeDistributionsCounter;

    public PartitionDistributionMetrics(
        final int targetPartitionId, final String queueId, final MeterRegistry meterRegistry) {
      this.targetPartitionId = targetPartitionId;
      this.queueId = queueId;
      this.meterRegistry = meterRegistry;

      pendingDistributionsGauge =
          StatefulGauge.builder(DistributionMetricsDoc.PENDING_COMMAND_DISTRIBUTIONS.getName())
              .description(DistributionMetricsDoc.PENDING_COMMAND_DISTRIBUTIONS.getDescription())
              .tags(tags())
              .register(meterRegistry);

      inflightDistributionsGauge =
          StatefulGauge.builder(DistributionMetricsDoc.INFLIGHT_COMMAND_DISTRIBUTIONS.getName())
              .description(DistributionMetricsDoc.INFLIGHT_COMMAND_DISTRIBUTIONS.getDescription())
              .tags(tags())
              .register(meterRegistry);

      retryInflightDistributionsCounter =
          Counter.builder(DistributionMetricsDoc.RETRY_INFLIGHT_COMMAND_DISTRIBUTIONS.getName())
              .description(
                  DistributionMetricsDoc.RETRY_INFLIGHT_COMMAND_DISTRIBUTIONS.getDescription())
              .tags(tags())
              .register(meterRegistry);

      sentAcknowledgeDistributionsCounter =
          Counter.builder(DistributionMetricsDoc.SENT_ACKNOWLEDGE_COMMAND_DISTRIBUTIONS.getName())
              .description(
                  DistributionMetricsDoc.SENT_ACKNOWLEDGE_COMMAND_DISTRIBUTIONS.getDescription())
              .tags(tags())
              .register(meterRegistry);

      receivedAcknowledgeDistributionsCounter =
          Counter.builder(
                  DistributionMetricsDoc.RECEIVED_ACKNOWLEDGE_COMMAND_DISTRIBUTIONS.getName())
              .description(
                  DistributionMetricsDoc.RECEIVED_ACKNOWLEDGE_COMMAND_DISTRIBUTIONS
                      .getDescription())
              .tags(tags())
              .register(meterRegistry);
    }

    private Tags tags() {
      return DistributionMetricsDoc.DistributionMetricKeyNames.tags(targetPartitionId, queueId);
    }

    public void resetGauges() {
      pendingDistributionsGauge.set(0);
      inflightDistributionsGauge.set(0);
    }

    public void addPendingDistribution() {
      pendingDistributionsGauge.increment();
    }

    public void removePendingDistribution() {
      pendingDistributionsGauge.decrement();
    }

    public void addInflightDistribution() {
      inflightDistributionsGauge.increment();
    }

    public void removeInflightDistribution() {
      inflightDistributionsGauge.decrement();
    }

    public void retryInflightDistribution() {
      retryInflightDistributionsCounter.increment();
    }

    public void sentAcknowledgeDistribution() {
      sentAcknowledgeDistributionsCounter.increment();
    }

    public void receivedAcknowledgeDistribution() {
      receivedAcknowledgeDistributionsCounter.increment();
    }
  }
}
