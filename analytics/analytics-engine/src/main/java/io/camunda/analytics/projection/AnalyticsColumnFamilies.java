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
 * Column families for an analytics stage, held in one {@code ZeebeDb} per partition: the Model-A
 * base projection rows (element entities, variables, incidents), the Stage-1 open segments, the
 * Stage-2 rollup cells, and the consumed offset — so one checkpoint transaction commits the stage's
 * state and its offset as a single atomic cut.
 */
public enum AnalyticsColumnFamilies implements EnumValue, ScopedColumnFamily {
  /** Reserved default (RocksDB requires a default column family). */
  DEFAULT(0, ColumnFamilyScope.PARTITION_LOCAL),

  /** Per partition: the source position up to which the stage has consumed and committed. */
  CONSUMED_POSITION(1, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * Stage-2 merged rollup cells: {@code aggId ++ windowStart ++ codec(groupingKey) ->
   * codec(accumulator)}. Each meter's merger reads/evicts only its own cells via an {@code aggId}
   * prefix scan.
   */
  CUBE_CELLS(2, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * The Model-A base projection's materialized element rows, keyed by {@code elementInstanceKey}
   * (the process instance's root element keys in like any element): {@code start, end, status,
   * durationMs, isProcess, hadIncident}. Upserted on activation, finalized on completion, and
   * evicted after the completion fact is derived.
   */
  ELEMENT_ENTITY(3, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * Per-{@code (scopeKey, name)} variable instances, so a completing element instance's fact can be
   * enriched with the variables visible to its scope. Zeebe-style per-entry storage (not a map
   * blob); a scope's variables are read/cleared by a {@code scopeKey} prefix scan, and a completion
   * resolves the visible snapshot up the scope hierarchy.
   */
  VARIABLE_ENTRIES(4, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * The Model-A materialized incident rows, keyed by {@code elementInstanceKey}: {@code createMs,
   * resolveMs, errorType}. Opened on {@code INCIDENT/CREATED}, finalized on {@code RESOLVED} (so
   * the resolution duration is read from the row), then evicted.
   */
  INCIDENT_ENTITY(5, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * The Stage-1 windowed aggregate's checkpointed open segment (Model F): {@code group ++
   * windowStart ++ codec(key) -> codec(accumulator)} plus a per-{@code group} meta entry. Shared by
   * every meter's sealing aggregation on the partition, each scoped by its {@code aggId} group.
   */
  OPEN_SEGMENT(6, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * Marks which scopes currently hold at least one variable: {@code scopeKey -> nil}. Written when
   * a variable is put and removed when a scope is cleared, so eviction can skip the {@code
   * VARIABLE_ENTRIES} prefix scan entirely for the common case of an element with no local
   * variables — avoiding a RocksDB iterator seek (and the transaction it opens) per eviction.
   */
  VARIABLE_SCOPES(7, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * Stage-1 pre-fold dedup (ADR 0007): the high-watermark of the last <em>applied</em> Zeebe record
   * position per Zeebe partition, {@code zeebePartitionId -> position}. Checked before folding each
   * record — an exporter duplicate (the same Zeebe record re-appended at a later Event Bridge
   * offset) arrives at-or-below the watermark and is skipped — and committed in the same atomic cut
   * as the topology state and the consumed offset, so replay after a crash re-folds exactly the
   * not-yet-committed records.
   */
  ZEEBE_APPLIED_POSITION(8, ColumnFamilyScope.PARTITION_LOCAL);

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
