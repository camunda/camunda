/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import static io.camunda.zeebe.engine.state.rdbms.RdbmsProcessStore.PARTITION_STATE;

import io.camunda.zeebe.engine.state.metrics.PersistedUsageMetrics;
import io.camunda.zeebe.engine.state.metrics.UsageMetricStore;
import io.camunda.zeebe.engine.state.rdbms.PartitionStateMapper.PartitionStateRow;
import io.camunda.zeebe.engine.state.rdbms.UsageMetricMapper.UsageMetricKey;
import io.camunda.zeebe.engine.state.rdbms.UsageMetricMapper.UsageMetricRow;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Stores the active usage metrics bucket in a relational database, through the partition's {@link
 * EntitySession}: the writes are staged until the commit, the reads see them.
 *
 * <p>The bucket has a row per tenant counter and per unique task user of a tenant, so recording a
 * metric reads and writes one row, which the session collapses when it is recorded more than once
 * in a transaction. The start time of the bucket is a value of the partition state; a bucket
 * without it, created by the first recorded metric, has no start time.
 */
public final class RdbmsUsageMetricStore implements UsageMetricStore {

  static final EntityType<UsageMetricKey, UsageMetricRow> USAGE_METRIC =
      new EntityType<>(UsageMetricMapper.class, "UsageMetric", "usage metric", UsageMetricRow::key);

  private static final String FROM_TIME_KEY = "usage-metrics.from-time";

  private static final short RPI = 1;
  private static final short EDI = 2;
  private static final short TU = 3;

  private static final Comparator<UsageMetricRow> BY_KEY =
      Comparator.comparingInt((UsageMetricRow row) -> row.metricType())
          .thenComparing(UsageMetricRow::tenantId)
          .thenComparingLong(UsageMetricRow::assigneeHash);

  private final EntitySession session;
  private final int partitionId;

  /** Most process instance creations update a counter, so the rows are kept, see the cache. */
  private final CommittedRowCache<UsageMetricKey, UsageMetricRow> metrics;

  RdbmsUsageMetricStore(final EntitySession session) {
    this.session = session;
    partitionId = session.partitionId();
    metrics = new CommittedRowCache<>(session.transactionContext());
  }

  @Override
  public @Nullable PersistedUsageMetrics getActiveBucket() {
    final var fromTime = session.get(PARTITION_STATE, FROM_TIME_KEY);
    final var rows = rows();
    if (fromTime == null && rows.isEmpty()) {
      return null;
    }

    final Map<String, Long> tenantRPI = new HashMap<>();
    final Map<String, Long> tenantEDI = new HashMap<>();
    final Map<String, Set<Long>> tenantTU = new HashMap<>();
    for (final var row : rows) {
      switch (row.metricType()) {
        case RPI -> tenantRPI.put(row.tenantId(), row.metricCount());
        case EDI -> tenantEDI.put(row.tenantId(), row.metricCount());
        case TU ->
            tenantTU
                .computeIfAbsent(row.tenantId(), tenant -> new HashSet<>())
                .add(row.assigneeHash());
        default ->
            throw new IllegalStateException(
                "Expected a usage metric of type RPI, EDI or TU, but got " + row.metricType());
      }
    }

    final var bucket =
        new PersistedUsageMetrics()
            .setTenantRPIMap(tenantRPI)
            .setTenantEDIMap(tenantEDI)
            .setTenantTUMap(tenantTU);
    if (fromTime != null) {
      bucket.setFromTime(Long.parseLong(fromTime.value()));
    }
    return bucket;
  }

  @Override
  public void incrementCounter(final EventType eventType, final String tenantId) {
    final short metricType =
        switch (eventType) {
          case RPI -> RPI;
          case EDI -> EDI;
          default ->
              throw new IllegalArgumentException(
                  "Expected a counter event type RPI or EDI, but got " + eventType);
        };
    final var key = new UsageMetricKey(metricType, tenantId, 0);
    final var row = metric(key);
    if (row == null) {
      final var inserted = new UsageMetricRow(metricType, tenantId, 0, 1);
      session.insert(USAGE_METRIC, inserted);
      metrics.put(key, inserted);
    } else {
      final var updated = row.incremented();
      session.update(USAGE_METRIC, updated);
      metrics.put(key, updated);
    }
  }

  @Override
  public void addAssignee(final String tenantId, final long assigneeHash) {
    final var key = new UsageMetricKey(TU, tenantId, assigneeHash);
    if (metric(key) == null) {
      final var inserted = new UsageMetricRow(TU, tenantId, assigneeHash, 1);
      session.insert(USAGE_METRIC, inserted);
      metrics.put(key, inserted);
    }
  }

  @Override
  public void resetActiveBucket(final long fromTime) {
    for (final var row : rows()) {
      session.delete(USAGE_METRIC, row.key());
      metrics.remove(row.key());
    }
    final var value = new PartitionStateRow(FROM_TIME_KEY, Long.toString(fromTime));
    if (session.get(PARTITION_STATE, FROM_TIME_KEY) == null) {
      session.insert(PARTITION_STATE, value);
    } else {
      session.update(PARTITION_STATE, value);
    }
  }

  /** The metrics of the bucket, committed and staged. */
  private @Nullable UsageMetricRow metric(final UsageMetricKey key) {
    return metrics.get(key, k -> session.get(USAGE_METRIC, k));
  }

  private List<UsageMetricRow> rows() {
    return session.query(
        USAGE_METRIC,
        session.read(UsageMetricMapper.class, mapper -> mapper.selectUsageMetrics(partitionId)),
        row -> true,
        BY_KEY);
  }
}
