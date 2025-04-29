/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DistributionMetrics {

  private final MeterRegistry meterRegistry;
  private final Map<Integer, PartitionDistributionMetrics> partitionMetrics =
      new ConcurrentHashMap<>();

  // Metrics
  private final StatefulGauge commandDistributionsGauge;

  public DistributionMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;

    commandDistributionsGauge =
        StatefulGauge.builder(DistributionMetricsDoc.ACTIVE_COMMAND_DISTRIBUTIONS.getName())
            .description(DistributionMetricsDoc.ACTIVE_COMMAND_DISTRIBUTIONS.getDescription())
            .register(meterRegistry);
  }

  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

  public void addDistribution(final long distributionKey) {
    commandDistributionsGauge.increment();
  }

  public void removeDistribution(final long distributionKey) {
    commandDistributionsGauge.decrement();
  }

  public void addPendingDistribution(final int targetPartitionId, final long distributionKey) {
    getPartitionMetrics(targetPartitionId).addPendingDistribution(distributionKey);
  }

  public void removePendingDistribution(final int targetPartitionId, final long distributionKey) {
    getPartitionMetrics(targetPartitionId).removePendingDistribution(distributionKey);
  }

  public void addInflightDistribution(final int targetPartitionId, final long distributionKey) {
    getPartitionMetrics(targetPartitionId).addInflightDistribution(distributionKey);
  }

  public void removeInflightDistribution(final int targetPartitionId, final long distributionKey) {
    getPartitionMetrics(targetPartitionId).removeInflightDistribution(distributionKey);
  }

  /**
   * This method is called on the target partition when a distribution command has been successfully
   * processed and an acknowledgement is sent back to the origin partition.
   *
   * @param originPartitionId the source partition id of the distribution (where the distribution
   *     was started)
   * @param distributionKey the key of the distribution
   */
  public void acknowledgeDistribution(final int originPartitionId, final long distributionKey) {
    getPartitionMetrics(originPartitionId).acknowledgeDistribution(distributionKey);
  }

  /**
   * This method is called when a current inflight distribution is being retried. This may happen if
   * the acknowledgement of the target partition is not received by the origin partition in-time.
   * See CommandRedistribution.java
   *
   * @param targetPartitionId the target partition id of the distribution
   * @param distributionKey the key of the distribution
   */
  public void retryInflightDistribution(final int targetPartitionId, final long distributionKey) {
    getPartitionMetrics(targetPartitionId).retryInflightDistribution(distributionKey);
  }

  public PartitionDistributionMetrics getPartitionMetrics(final int targetPartitionId) {
    return partitionMetrics.computeIfAbsent(
        targetPartitionId, id -> new PartitionDistributionMetrics(id, meterRegistry));
  }

  public void timedRedistribution(final Runnable redistribution) {
    // TODO: wrap with a long task timer
    redistribution.run();
  }

  private class PartitionDistributionMetrics {

    private final MeterRegistry meterRegistry;
    private final int targetPartitionId;

    // Metrics
    private final StatefulGauge pendingDistributionsGauge;
    private final StatefulGauge inflightDistributionsGauge;
    private final Counter retryInflightDistributionsCounter;
    private final Counter acknowledgeDistributionsCounter;

    public PartitionDistributionMetrics(
        final int targetPartitionId, final MeterRegistry meterRegistry) {
      this.targetPartitionId = targetPartitionId;
      this.meterRegistry = meterRegistry;

      pendingDistributionsGauge =
          StatefulGauge.builder(DistributionMetricsDoc.PENDING_COMMAND_DISTRIBUTIONS.getName())
              .description(DistributionMetricsDoc.PENDING_COMMAND_DISTRIBUTIONS.getDescription())
              .tags(MicrometerUtil.PartitionKeyNames.targetPartitionTags(targetPartitionId))
              .register(meterRegistry);

      inflightDistributionsGauge =
          StatefulGauge.builder(DistributionMetricsDoc.INFLIGHT_COMMAND_DISTRIBUTIONS.getName())
              .description(DistributionMetricsDoc.INFLIGHT_COMMAND_DISTRIBUTIONS.getDescription())
              .tags(MicrometerUtil.PartitionKeyNames.targetPartitionTags(targetPartitionId))
              .register(meterRegistry);

      retryInflightDistributionsCounter =
          Counter.builder(DistributionMetricsDoc.RETRY_INFLIGHT_COMMAND_DISTRIBUTIONS.getName())
              .description(
                  DistributionMetricsDoc.RETRY_INFLIGHT_COMMAND_DISTRIBUTIONS.getDescription())
              .tags(MicrometerUtil.PartitionKeyNames.targetPartitionTags(targetPartitionId))
              .register(meterRegistry);

      acknowledgeDistributionsCounter =
          Counter.builder(DistributionMetricsDoc.ACKNOWLEDGE_COMMAND_DISTRIBUTIONS.getName())
              .description(
                  DistributionMetricsDoc.ACKNOWLEDGE_COMMAND_DISTRIBUTIONS.getDescription())
              .tags(MicrometerUtil.PartitionKeyNames.targetPartitionTags(targetPartitionId))
              .register(meterRegistry);
    }

    public void addPendingDistribution(final long distributionKey) {
      pendingDistributionsGauge.increment();
    }

    public void removePendingDistribution(final long distributionKey) {
      pendingDistributionsGauge.decrement();
    }

    public void addInflightDistribution(final long distributionKey) {
      inflightDistributionsGauge.increment();
    }

    public void removeInflightDistribution(final long distributionKey) {
      inflightDistributionsGauge.decrement();
    }

    public void retryInflightDistribution(final long distributionKey) {
      retryInflightDistributionsCounter.increment();
    }

    public void acknowledgeDistribution(final long distributionKey) {
      acknowledgeDistributionsCounter.increment();
    }
  }
}
