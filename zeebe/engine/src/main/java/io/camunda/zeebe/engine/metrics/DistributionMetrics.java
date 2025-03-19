/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DistributionMetrics {

  private final MeterRegistry meterRegistry;
  private final Map<Integer, PartitionDistributionMetrics> partitionMetrics =
      new ConcurrentHashMap<>(); // TODO check if concurrent map is needed

  public DistributionMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;

    // TODO: setup micrometer metrics once (performance)
  }

  /**
   * Resets the metrics (e.g. in case of recovery to remain in sync with the state). Be careful
   * about race conditions and call this only from the stream processing actor
   */
  public void reset(final int counter) {}

  public void addDistribution(final long distributionKey) {}

  public void removeDistribution(final long distributionKey) {}

  public void addPendingDistribution(final int partitionId, final long distributionKey) {
    getPartitionMetrics(partitionId).addPendingDistribution(distributionKey);
  }

  public void removePendingDistribution(final int partitionId, final long distributionKey) {
    getPartitionMetrics(partitionId).removePendingDistribution(distributionKey);
  }

  /**
   * This method is called when a pending distribution is not found for a received acknowledgement.
   * This may happen if a distribution has been retried, but in the meantime an acknowledgement was
   * already received - thus this was duplicate acknowledgement.
   *
   * <p>TBD if this metrics brings additional value, compared to the retryInflightDistribution
   * metric as it also represents any delay/issues between origin and target partition.
   *
   * @param partitionId the partition id of the distribution
   * @param distributionKey the key of the distribution
   */
  public void noPendingDistribution(final int partitionId, final long distributionKey) {
    getPartitionMetrics(partitionId).noPendingDistribution(distributionKey);
  }

  public void addInflightDistribution(final int partitionId, final long distributionKey) {
    getPartitionMetrics(partitionId).addInflightDistribution(distributionKey);
  }

  public void removeInflightDistribution(final int partitionId, final long distributionKey) {
    getPartitionMetrics(partitionId).removeInflightDistribution(distributionKey);
  }

  /**
   * This method is called on the target partition when a distribution command has been successfully
   * processed
   *
   * @param partitionId the partition id of the distribution
   * @param distributionKey the key of the distribution
   */
  public void acknowledgeDistribution(final int partitionId, final long distributionKey) {
    getPartitionMetrics(partitionId).acknowledgeDistribution(distributionKey);
  }

  /**
   * This method is called when a current inflight distribution is being retried. This may happen if
   * the acknowledgement of the target partition is not received by the origin partition in-time.
   * See CommandRedistribution.java
   *
   * @param partitionId the partition id of the distribution
   * @param distributionKey the key of the distribution
   */
  public void retryInflightDistribution(final int partitionId, final long distributionKey) {
    getPartitionMetrics(partitionId).retryInflightDistribution(distributionKey);
  }

  private PartitionDistributionMetrics getPartitionMetrics(final int partitionId) {
    return partitionMetrics.computeIfAbsent(
        partitionId, id -> new PartitionDistributionMetrics(id, meterRegistry));
  }

  private class PartitionDistributionMetrics {

    private final MeterRegistry meterRegistry;
    private final int partitionId;

    public PartitionDistributionMetrics(final int partitionId, final MeterRegistry meterRegistry) {
      this.partitionId = partitionId;
      this.meterRegistry = meterRegistry;

      // TODO: setup micrometer metrics once (performance)
    }

    public void addPendingDistribution(final long distributionKey) {}

    public void removePendingDistribution(final long distributionKey) {}

    public void noPendingDistribution(final long distributionKey) {}

    public void addInflightDistribution(final long distributionKey) {}

    public void removeInflightDistribution(final long distributionKey) {}

    public void retryInflightDistribution(final long distributionKey) {}

    public void acknowledgeDistribution(final long distributionKey) {}
  }
}
