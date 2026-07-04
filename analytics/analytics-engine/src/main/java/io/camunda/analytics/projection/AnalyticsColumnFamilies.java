/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection;

import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;

/**
 * Column families for the whole analytics stage, held in a single {@code ZeebeDb} (one RocksDB per
 * stage): the base projection (variables, element starts, incidents, consumed position)
 * <em>and</em> the durable rollups. All rollups share {@link #ROLLUP_CELLS} and {@link
 * #ROLLUP_OFFSETS}, keyed by a {@code rollupId} prefix, so a new metric or user-created dataset is
 * a new id, not a new column family — and one checkpoint transaction can commit the base
 * projection, every rollup, and the consumed offset as one atomic cut.
 */
public enum AnalyticsColumnFamilies implements EnumValue, ScopedColumnFamily {
  /** Reserved default (RocksDB requires a default CF). */
  DEFAULT(0, ColumnFamilyScope.PARTITION_LOCAL),

  /** Per-process-instance variables, keyed by {@code processInstanceKey}. */
  INSTANCE_VARIABLES(1, ColumnFamilyScope.PARTITION_LOCAL),

  /** Per source partition: the position up to which the fold has consumed. */
  CONSUMED_POSITION(2, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * Per in-flight element instance: its activation time, keyed by {@code instanceKey:elementId}.
   */
  ELEMENT_START(3, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * Per process instance: a flag set when it has raised at least one incident, keyed by {@code
   * processInstanceKey}. Read (and cleared) when the instance reaches a terminal state so the
   * derived fact can record whether the instance ever had an incident.
   */
  INSTANCE_INCIDENT(4, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * Per element instance with an open incident: the incident's create time, keyed by {@code
   * elementInstanceKey}. Read (and cleared) on resolve to derive the incident's open→resolve
   * duration.
   */
  INCIDENT_START(5, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * Shared windowed cells for every rollup: {@code rollupId ++ windowStart ++ codec(groupingKey) ->
   * codec(accumulator)}. Each rollup reads/evicts only its own cells via a {@code rollupId} prefix
   * scan.
   */
  ROLLUP_CELLS(6, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * Shared rollup dedup offsets: {@code rollupId ++ partitionId -> position} (plus a per-rollup
   * watermark slot). Co-committed with the cells so state and offset never diverge.
   */
  ROLLUP_OFFSETS(7, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * Stage 2 reducer per-writer slots: {@code aggId ++ windowStart ++ writer ++ key -> accumulator}.
   * Shared across all mergers; each reads its own via {@code prefixScan(aggId)}.
   */
  SLOT_CELLS(8, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * The Model-A base projection's materialized element rows, keyed by {@code elementInstanceKey}
   * (the process instance's root element keys in like any element): {@code start, end, status,
   * durationMs, isProcess, hadIncident}. Upserted on activation, finalized on completion, and
   * evicted after the completion fact is derived.
   */
  ELEMENT_ENTITY(9, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * Per-{@code (scopeKey, name)} variable instances, so a completing element instance's fact can be
   * enriched with the variables visible to its scope. Zeebe-style per-entry storage (not a map
   * blob); a scope's variables are read/cleared by a {@code scopeKey} prefix scan, and a completion
   * resolves the visible snapshot up the scope hierarchy.
   */
  VARIABLE_ENTRIES(10, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * Event-time deadline secondary index for straggler eviction: {@code deadlineMs ++
   * elementInstanceKey -> (empty)}. A {@code STREAM_TIME} punctuator scans it in event-time order
   * and evicts rows whose deadline has passed, bounding the materialized projection for instances
   * that never complete. Only holds in-flight rows (removed on completion).
   */
  ELEMENT_DEADLINE(11, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * The Model-A materialized incident rows, keyed by {@code elementInstanceKey}: {@code createMs,
   * resolveMs}. Opened on {@code INCIDENT/CREATED}, finalized on {@code RESOLVED} (so the
   * resolution duration is read from the row), then evicted.
   */
  INCIDENT_ENTITY(12, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * The Stage-1 windowed aggregate's checkpointed open segment (Model F): {@code group ++
   * windowStart ++ codec(key) -> codec(accumulator)} plus a per-{@code group} meta entry. Shared by
   * every meter's sealing aggregation on the partition, each scoped by its {@code aggId} group.
   */
  OPEN_SEGMENT(13, ColumnFamilyScope.PARTITION_LOCAL);

  private final int value;
  private final ColumnFamilyScope scope;

  AnalyticsColumnFamilies(final int value, final ColumnFamilyScope scope) {
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
