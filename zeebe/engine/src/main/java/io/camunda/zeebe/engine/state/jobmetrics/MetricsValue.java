/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.value.JobMetricsExportState;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Value for the metrics column family. Contains an array of 3 StatusMetrics objects (one per
 * JobMetricsExportState enum value).
 *
 * <p>Total size: 3 * (4 bytes int + 8 bytes long) = 96 bytes
 */
public final class MetricsValue implements DbValue {

  /** Fixed size: 3 StatusMetrics = 36 bytes */
  public static final int TOTAL_SIZE_BYTES =
      JobMetricsExportState.count() * StatusMetrics.TOTAL_SIZE_BYTES;

  private final StatusMetrics[] metrics;

  public MetricsValue() {
    metrics = new StatusMetrics[JobMetricsExportState.count()];
    for (int i = 0; i < metrics.length; i++) {
      metrics[i] = new StatusMetrics();
    }
  }

  public StatusMetrics[] getMetrics() {
    return metrics;
  }

  public StatusMetrics getMetricForStatus(final JobMetricsExportState status) {
    return metrics[status.getIndex()];
  }

  public void incrementMetric(final JobMetricsExportState status, final long timestamp) {
    metrics[status.getIndex()].increment(timestamp);
  }

  public void reset() {
    for (final StatusMetrics metric : metrics) {
      metric.reset();
    }
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    int currentOffset = offset;
    for (final StatusMetrics metric : metrics) {
      final int count = buffer.getInt(currentOffset, Protocol.ENDIANNESS);
      currentOffset += Integer.BYTES;
      final long lastUpdatedAt = buffer.getLong(currentOffset, Protocol.ENDIANNESS);
      currentOffset += Long.BYTES;
      metric.setCount(count);
      metric.setLastUpdatedAt(lastUpdatedAt);
    }
  }

  @Override
  public int getLength() {
    return TOTAL_SIZE_BYTES;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    int currentOffset = offset;
    for (final StatusMetrics metric : metrics) {
      buffer.putInt(currentOffset, metric.getCount(), Protocol.ENDIANNESS);
      currentOffset += Integer.BYTES;
      buffer.putLong(currentOffset, metric.getLastUpdatedAt(), Protocol.ENDIANNESS);
      currentOffset += Long.BYTES;
    }
    return getLength();
  }

  /**
   * Creates a deep copy of the StatusMetrics array.
   *
   * @return a new array with copied StatusMetrics objects
   */
  public StatusMetrics[] copyMetrics() {
    final StatusMetrics[] copy = new StatusMetrics[metrics.length];
    for (int i = 0; i < metrics.length; i++) {
      copy[i] = new StatusMetrics(metrics[i].getCount(), metrics[i].getLastUpdatedAt());
    }
    return copy;
  }

  /**
   * Creates a deep copy of this MetricsValue.
   *
   * @return a new MetricsValue with copied data
   */
  public MetricsValue copy() {
    final MetricsValue copy = new MetricsValue();
    for (int i = 0; i < metrics.length; i++) {
      copy.metrics[i].setCount(metrics[i].getCount());
      copy.metrics[i].setLastUpdatedAt(metrics[i].getLastUpdatedAt());
    }
    return copy;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("MetricsValue{");
    for (int i = 0; i < metrics.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(JobMetricsExportState.values()[i].name()).append("=").append(metrics[i]);
    }
    sb.append('}');
    return sb.toString();
  }
}
