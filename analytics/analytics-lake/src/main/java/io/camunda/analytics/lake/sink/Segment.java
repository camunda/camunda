/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink;

/**
 * One row-group-sized slice of the segment ring: schema-driven column vectors plus the minimal
 * bookkeeping the flush side needs. Allocated once (vectors included), recycled forever via {@link
 * #reset()}.
 *
 * <p>Ownership follows the ring: exactly one thread touches a segment at any time (poll thread
 * while filling, flush thread after sealing); the ring's counter stores publish the transitions.
 *
 * <p>Note on stats: at encoder rung 1 (iceberg-parquet) the library computes file metrics and bloom
 * filters itself, so this class tracks only the family-day instant range — a two-compare per-row
 * convenience for the day router and metrics. Full inline stats/bloom tracking belongs to the
 * rung-2 bespoke encoder and would be reintroduced here.
 */
public final class Segment {

  private final TableSchema schema;
  private final ColumnVector[] vectors;
  private final int rowCapacity;
  private final int familyDayColumn;

  private int size;
  private SealReason sealReason;
  private long minFamilyInstantMs = Long.MAX_VALUE;
  private long maxFamilyInstantMs = Long.MIN_VALUE;

  public Segment(final TableSchema schema, final ColumnVector[] vectors, final int rowCapacity) {
    if (vectors.length != schema.columns().size()) {
      throw new IllegalArgumentException(
          "expected %d vectors for schema %s but got %d"
              .formatted(schema.columns().size(), schema.table(), vectors.length));
    }
    this.schema = schema;
    this.vectors = vectors;
    this.rowCapacity = rowCapacity;
    familyDayColumn = schema.familyDayColumn();
  }

  public TableSchema schema() {
    return schema;
  }

  public ColumnVector vector(final int column) {
    return vectors[column];
  }

  public int size() {
    return size;
  }

  public int rowCapacity() {
    return rowCapacity;
  }

  public boolean isFull() {
    return size >= rowCapacity;
  }

  /** Called by the appender once per completed row (poll thread). */
  public void rowCompleted() {
    final long instant = ((ColumnVector.LongColumn) vectors[familyDayColumn]).get(size);
    if (instant < minFamilyInstantMs) {
      minFamilyInstantMs = instant;
    }
    if (instant > maxFamilyInstantMs) {
      maxFamilyInstantMs = instant;
    }
    size++;
  }

  public void markSealed(final SealReason reason) {
    sealReason = reason;
  }

  public SealReason sealReason() {
    return sealReason;
  }

  public long minFamilyInstantMs() {
    return minFamilyInstantMs;
  }

  public long maxFamilyInstantMs() {
    return maxFamilyInstantMs;
  }

  /** Recycle for reuse (flush thread, before the ring republishes the slot). */
  public void reset() {
    for (final ColumnVector vector : vectors) {
      vector.reset();
    }
    size = 0;
    sealReason = null;
    minFamilyInstantMs = Long.MAX_VALUE;
    maxFamilyInstantMs = Long.MIN_VALUE;
  }
}
