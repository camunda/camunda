/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class DistributionMetrics {

  private static final String NO_QUEUE = "NONE";

  private final MeterRegistry meterRegistry;
  private final Map<MetricKey, PartitionDistributionMetrics> partitionMetrics =
      new ConcurrentHashMap<>();

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
    partitionMetrics.values().forEach(PartitionDistributionMetrics::resetGauges);
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

  public void addPendingDistribution(final int targetPartitionId, final String queueId) {
    getPartitionMetrics(targetPartitionId, queueId).addPendingDistribution();
  }

  public void removePendingDistribution(final int targetPartitionId, final String queueId) {
    getPartitionMetrics(targetPartitionId, queueId).removePendingDistribution();
  }

  public void addInflightDistribution(final int targetPartitionId, final String queueId) {
    getPartitionMetrics(targetPartitionId, queueId).addInflightDistribution();
  }

  public void removeInflightDistribution(final int targetPartitionId, final String queueId) {
    getPartitionMetrics(targetPartitionId, queueId).removeInflightDistribution();
  }

  /**
   * This method is called on the target partition when a distribution command has been successfully
   * processed and an acknowledgement is sent back to the origin partition.
   *
   * @param originPartitionId the source partition id of the distribution (where the distribution
   *     was started)
   */
  public void sentAcknowledgeDistribution(final int originPartitionId, final String queueId) {
    getPartitionMetrics(originPartitionId, queueId).sentAcknowledgeDistribution();
  }

  /**
   * This method is called on the origin partition when a distribution command has been successfully
   * acknowledged by the target partition.
   *
   * @param targetPartitionId the source partition id of the distribution (where the distribution
   *     was started)
   */
  public void receivedAcknowledgeDistribution(final int targetPartitionId, final String queueId) {
    getPartitionMetrics(targetPartitionId, queueId).receivedAcknowledgeDistribution();
  }

  /**
   * This method is called when a current inflight distribution is being retried. This may happen if
   * the acknowledgement of the target partition is not received by the origin partition in-time.
   * See {@link io.camunda.zeebe.engine.processing.distribution.CommandRedistributionScheduler}.
   *
   * @param targetPartitionId the target partition id of the distribution
   */
  public void retryInflightDistribution(final int targetPartitionId, final String queueId) {
    getPartitionMetrics(targetPartitionId, queueId).retryInflightDistribution();
  }

  private PartitionDistributionMetrics getPartitionMetrics(
      final int targetPartitionId, final String queueId) {
    return partitionMetrics.computeIfAbsent(
        new MetricKey(targetPartitionId, normalizeQueueId(queueId)),
        key ->
            new PartitionDistributionMetrics(
                key.targetPartitionId(), key.queueId(), meterRegistry));
  }

  private static String normalizeQueueId(final String queueId) {
    return queueId == null ? NO_QUEUE : queueId;
  }

  private record MetricKey(int targetPartitionId, String queueId) {}

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
      this.queueId = Objects.requireNonNull(queueId);
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
