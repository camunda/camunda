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
  ZEEBE_APPLIED_POSITION(8, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * The Stage-2 segment dedup's admission watermarks: {@code sourcePartition(4) ++ streamId(4) ->
   * segment(8) ++ chunk(4)} (big-endian), the last admitted {@code SegmentPosition} per shuffle
   * stream. Persisted in the same atomic cut as the merged cells and the facts offset, and restored
   * at task open, so a Stage-1 crash in its produce-before-commit gap (re-publishing a segment
   * delta as a new facts-topic append) is still dropped by a restarted Stage 2 instead of being
   * double-folded by the non-idempotent merge.
   */
  SHUFFLE_DEDUP_WATERMARK(9, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * Stage-2 parked shuffle deltas for streams the merge topology has no applier for (yet): {@code
   * streamId(4) ++ seq(8) -> sourcePartition(4) ++ segment(8) ++ chunk(4) ++ windowStart(8) ++
   * keyLen(4) ++ key ++ payloadLen(4) ++ payload} (big-endian). A newly-declared cube's first
   * deltas can arrive before this task's catalog reload wires its merger; instead of being
   * consumed-and-lost (the facts offset advances regardless), they are parked here — persisted in
   * the same atomic cut as the offset — and drained through the segment dedup when a reload
   * installs the stream's applier, or discarded when the reloaded catalog does not know the stream
   * (a removed dataset's stragglers). Parked entries are deleted only in the cut <em>after</em>
   * their drain, so a crash between drain and cut replays them from here rather than losing the
   * merge.
   */
  PARKED_DELTAS(10, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * The Model-A per-instance variant accumulator: {@code processInstanceKey(8) ++ elementId(utf8)
   * -> activation count}. Bumped on every non-process {@code ELEMENT_ACTIVATED} fold, read by a
   * {@code processInstanceKey} prefix scan when the instance's end fact derives its variant
   * signature, and cleared when the instance's row is evicted — the {@code VARIABLE_ENTRIES}
   * pattern, keyed by instance instead of scope. A side family rather than a field on {@code
   * ElementEntity}: the entity is a fixed-shape flyweight rewritten whole on every mutation, while
   * these entries grow one-per-executed-element and are touched one at a time.
   *
   * <p>Known leak: an instance that never folds a terminal PROCESS record (banned instance, source
   * stream ends mid-flight) strands its entries — the same pre-existing gap as its element rows,
   * amplified to one entry per distinct executed element. A sweep/TTL is deliberately deferred
   * until it shows up in state-size numbers.
   */
  VARIANT_ELEMENTS(11, ColumnFamilyScope.PARTITION_LOCAL),

  /**
   * Stage-2 changelog position (streaming ADR 0009 Decision 1): {@code partition -> P}, the
   * broker-assigned position of the most recent cut's changelog offset-marker record. Persisted in
   * the same atomic cut as the merged cells and the facts offset, alongside the changelog append
   * itself; write-only today (no reader exists yet — the changelog-follower standby and
   * intact-disk-resume paths are ADR 0009 Decisions 5/6, not yet built).
   */
  CHANGELOG_POSITION(12, ColumnFamilyScope.PARTITION_LOCAL);

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
