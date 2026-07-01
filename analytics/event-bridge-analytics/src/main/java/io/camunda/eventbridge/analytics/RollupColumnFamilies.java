/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics;

import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;

/**
 * Column families for the durable rollups' state, held in their own {@code ZeebeDb} (separate from
 * the base projection). Each metric gets a cell store (windowed aggregate) and an offset store (the
 * per-partition dedup watermark co-committed with the cells).
 */
public enum RollupColumnFamilies implements EnumValue, ScopedColumnFamily {
  /** Reserved default (RocksDB requires a default CF). */
  DEFAULT(0, ColumnFamilyScope.PARTITION_LOCAL),

  /** Region execution-time cells: {@code windowStart ++ codec(RegionKey) -> codec(accumulator)}. */
  REGION_CELLS(1, ColumnFamilyScope.PARTITION_LOCAL),

  /** Region rollup offsets: {@code partitionId -> position} (plus the watermark slot). */
  REGION_OFFSETS(2, ColumnFamilyScope.PARTITION_LOCAL),

  /** Element heatmap cells: {@code windowStart ++ codec(ElementKey) -> codec(accumulator)}. */
  HEATMAP_CELLS(3, ColumnFamilyScope.PARTITION_LOCAL),

  /** Heatmap rollup offsets: {@code partitionId -> position} (plus the watermark slot). */
  HEATMAP_OFFSETS(4, ColumnFamilyScope.PARTITION_LOCAL),

  /** Definition duration-percentile cells: {@code windowStart ++ codec(DefinitionKey) -> KLL}. */
  DEF_PCTL_CELLS(5, ColumnFamilyScope.PARTITION_LOCAL),

  /** Definition duration-percentile offsets: {@code partitionId -> position} (plus watermark). */
  DEF_PCTL_OFFSETS(6, ColumnFamilyScope.PARTITION_LOCAL),

  /** Element duration-percentile cells: {@code windowStart ++ codec(ElementKey) -> KLL}. */
  ELEM_PCTL_CELLS(7, ColumnFamilyScope.PARTITION_LOCAL),

  /** Element duration-percentile offsets: {@code partitionId -> position} (plus watermark). */
  ELEM_PCTL_OFFSETS(8, ColumnFamilyScope.PARTITION_LOCAL),

  /** SLA-met ratio cells: {@code windowStart ++ codec(DefinitionKey) -> codec(matched,total)}. */
  SLA_RATIO_CELLS(9, ColumnFamilyScope.PARTITION_LOCAL),

  /** SLA-met ratio offsets: {@code partitionId -> position} (plus watermark). */
  SLA_RATIO_OFFSETS(10, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * No-incident ratio cells: {@code windowStart ++ codec(DefinitionKey) -> codec(matched,total)}.
   */
  INCIDENT_RATIO_CELLS(11, ColumnFamilyScope.PARTITION_LOCAL),

  /** No-incident ratio offsets: {@code partitionId -> position} (plus watermark). */
  INCIDENT_RATIO_OFFSETS(12, ColumnFamilyScope.PARTITION_LOCAL),

  /** Distinct-process cells: {@code windowStart ++ codec(tenantId) -> codec(HLL sketch)}. */
  DISTINCT_CELLS(13, ColumnFamilyScope.PARTITION_LOCAL),

  /** Distinct-process rollup offsets: {@code partitionId -> position} (plus watermark slot). */
  DISTINCT_OFFSETS(14, ColumnFamilyScope.PARTITION_LOCAL),

  /** Top-process cells: {@code windowStart ++ codec(tenantId) -> codec(frequent-items sketch)}. */
  TOPK_CELLS(15, ColumnFamilyScope.PARTITION_LOCAL),

  /** Top-process rollup offsets: {@code partitionId -> position} (plus watermark slot). */
  TOPK_OFFSETS(16, ColumnFamilyScope.PARTITION_LOCAL),

  /** All-time (single-bucket) definition duration-percentile cells — the coarsest granularity. */
  DEF_PCTL_TOTAL_CELLS(17, ColumnFamilyScope.PARTITION_LOCAL),

  /** All-time definition duration-percentile offsets. */
  DEF_PCTL_TOTAL_OFFSETS(18, ColumnFamilyScope.PARTITION_LOCAL),

  /** All-time (single-bucket) element duration-percentile cells. */
  ELEM_PCTL_TOTAL_CELLS(19, ColumnFamilyScope.PARTITION_LOCAL),

  /** All-time element duration-percentile offsets. */
  ELEM_PCTL_TOTAL_OFFSETS(20, ColumnFamilyScope.PARTITION_LOCAL),

  /** Active-instances gauge cells: {@code 0 ++ codec(DefinitionKey) -> codec(running sum)}. */
  ACTIVE_CELLS(21, ColumnFamilyScope.PARTITION_LOCAL),

  /** Active-instances gauge offsets. */
  ACTIVE_OFFSETS(22, ColumnFamilyScope.PARTITION_LOCAL),

  /** Activated-instances (windowed started count) cells. */
  ACTIVATED_CELLS(23, ColumnFamilyScope.PARTITION_LOCAL),

  /** Activated-instances rollup offsets. */
  ACTIVATED_OFFSETS(24, ColumnFamilyScope.PARTITION_LOCAL),

  /** All-time (single-bucket) top-processes cells. */
  TOPK_TOTAL_CELLS(25, ColumnFamilyScope.PARTITION_LOCAL),

  /** All-time top-processes offsets. */
  TOPK_TOTAL_OFFSETS(26, ColumnFamilyScope.PARTITION_LOCAL),

  // Intermediate tiers of the time hierarchy (1h and 1d), so a range read merges the coarsest tier
  // that still resolves the range instead of thousands of 1m sketch blobs. Only the
  // mergeable-sketch
  // metrics get these — additive metrics are read with a cheap SQL SUM and need no coarse tier.

  /** 1-hour-tier definition duration-percentile cells. */
  DEF_PCTL_1H_CELLS(27, ColumnFamilyScope.PARTITION_LOCAL),

  /** 1-hour-tier definition duration-percentile offsets. */
  DEF_PCTL_1H_OFFSETS(28, ColumnFamilyScope.PARTITION_LOCAL),

  /** 1-day-tier definition duration-percentile cells. */
  DEF_PCTL_1D_CELLS(29, ColumnFamilyScope.PARTITION_LOCAL),

  /** 1-day-tier definition duration-percentile offsets. */
  DEF_PCTL_1D_OFFSETS(30, ColumnFamilyScope.PARTITION_LOCAL),

  /** 1-hour-tier element duration-percentile cells. */
  ELEM_PCTL_1H_CELLS(31, ColumnFamilyScope.PARTITION_LOCAL),

  /** 1-hour-tier element duration-percentile offsets. */
  ELEM_PCTL_1H_OFFSETS(32, ColumnFamilyScope.PARTITION_LOCAL),

  /** 1-day-tier element duration-percentile cells. */
  ELEM_PCTL_1D_CELLS(33, ColumnFamilyScope.PARTITION_LOCAL),

  /** 1-day-tier element duration-percentile offsets. */
  ELEM_PCTL_1D_OFFSETS(34, ColumnFamilyScope.PARTITION_LOCAL),

  /** 1-hour-tier top-processes cells. */
  TOPK_1H_CELLS(35, ColumnFamilyScope.PARTITION_LOCAL),

  /** 1-hour-tier top-processes offsets. */
  TOPK_1H_OFFSETS(36, ColumnFamilyScope.PARTITION_LOCAL),

  /** 1-day-tier top-processes cells. */
  TOPK_1D_CELLS(37, ColumnFamilyScope.PARTITION_LOCAL),

  /** 1-day-tier top-processes offsets. */
  TOPK_1D_OFFSETS(38, ColumnFamilyScope.PARTITION_LOCAL),

  /** 1-day-tier distinct-process cells (the coarse tier above the hourly finest). */
  DISTINCT_1D_CELLS(39, ColumnFamilyScope.PARTITION_LOCAL),

  /** 1-day-tier distinct-process offsets. */
  DISTINCT_1D_OFFSETS(40, ColumnFamilyScope.PARTITION_LOCAL),

  /** Incident-frequency cells: {@code windowStart ++ codec(IncidentKey) -> created count}. */
  INCIDENT_FREQ_CELLS(41, ColumnFamilyScope.PARTITION_LOCAL),

  /** Incident-frequency offsets. */
  INCIDENT_FREQ_OFFSETS(42, ColumnFamilyScope.PARTITION_LOCAL),

  /** Open-incidents gauge cells: {@code 0 ++ codec(IncidentKey) -> (created - resolved) sum}. */
  INCIDENT_OPEN_CELLS(43, ColumnFamilyScope.PARTITION_LOCAL),

  /** Open-incidents gauge offsets. */
  INCIDENT_OPEN_OFFSETS(44, ColumnFamilyScope.PARTITION_LOCAL),

  /** Completion-time distribution (duration bands) per start cohort: cells. */
  DURATION_BUCKET_CELLS(45, ColumnFamilyScope.PARTITION_LOCAL),

  /** Completion-time distribution offsets. */
  DURATION_BUCKET_OFFSETS(46, ColumnFamilyScope.PARTITION_LOCAL);

  private final int value;
  private final ColumnFamilyScope scope;

  RollupColumnFamilies(final int value, final ColumnFamilyScope scope) {
    this.value = value;
    this.scope = scope;
  }

  @Override
  public int getValue() {
    return value;
  }

  @Override
  public ColumnFamilyScope partitionScope() {
    return scope;
  }
}
