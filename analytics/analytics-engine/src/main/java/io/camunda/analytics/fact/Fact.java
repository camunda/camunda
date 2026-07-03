/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.fact;

import io.camunda.analytics.dimension.FactRow;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The generic base-projection output: one uniform fact the metric core consumes, replacing the
 * per-metric marker records (e.g. {@code ProcessInstanceExecutionTimeFact}). It carries a {@link
 * FactType} tag, a bag of named {@code fields} (dimensions and measures), the {@code eventTime},
 * and the source coordinate — and implements {@link FactRow} natively, so the combiner reads
 * dimensions (via a key selector) and measures (via a measure ref) straight off it with no adapter.
 * Facts are a deterministic function of the in-order source stream and live only inside Stage 1;
 * the shuffle carries pre-aggregated partials, not facts, so no wire codec is needed here.
 *
 * <p>A missing field returns {@code null} (the "unknown" bucket for a dimension); absent and
 * null-valued are the same, so fields hold no null values. The lifecycle {@link Transition}, when
 * present, is the reserved {@link #TRANSITION} field (stored by name, so it can also be grouped
 * by).
 */
public final class Fact implements FactRow {

  /**
   * Reserved field name holding the {@link Transition} name, when the fact is a lifecycle event.
   */
  public static final String TRANSITION = "transition";

  private final FactType factType;
  private final Map<String, Object> fields;
  private final long eventTime;
  private final int sourcePartition;
  private final long sourcePosition;

  public Fact(
      final FactType factType,
      final Map<String, Object> fields,
      final long eventTime,
      final int sourcePartition,
      final long sourcePosition) {
    this.factType = Objects.requireNonNull(factType, "factType");
    this.fields = Map.copyOf(fields);
    this.eventTime = eventTime;
    this.sourcePartition = sourcePartition;
    this.sourcePosition = sourcePosition;
  }

  public static Builder builder(final FactType factType) {
    return new Builder(factType);
  }

  @Override
  public Object get(final String field) {
    return fields.get(field);
  }

  public FactType factType() {
    return factType;
  }

  public Map<String, Object> fields() {
    return fields;
  }

  public long eventTime() {
    return eventTime;
  }

  public int sourcePartition() {
    return sourcePartition;
  }

  public long sourcePosition() {
    return sourcePosition;
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof final Fact other
        && factType == other.factType
        && eventTime == other.eventTime
        && sourcePartition == other.sourcePartition
        && sourcePosition == other.sourcePosition
        && fields.equals(other.fields);
  }

  @Override
  public int hashCode() {
    return Objects.hash(factType, fields, eventTime, sourcePartition, sourcePosition);
  }

  @Override
  public String toString() {
    return "Fact[" + factType + ", " + fields + ", eventTime=" + eventTime + "]";
  }

  /** Ergonomic builder; {@link #field} ignores null values so absence and null coincide. */
  public static final class Builder {

    private final FactType factType;
    private final Map<String, Object> fields = new HashMap<>();
    private long eventTime;
    private int sourcePartition;
    private long sourcePosition;

    private Builder(final FactType factType) {
      this.factType = Objects.requireNonNull(factType, "factType");
    }

    public Builder eventTime(final long eventTime) {
      this.eventTime = eventTime;
      return this;
    }

    public Builder source(final int partition, final long position) {
      sourcePartition = partition;
      sourcePosition = position;
      return this;
    }

    public Builder transition(final Transition transition) {
      return field(TRANSITION, transition == null ? null : transition.name());
    }

    public Builder field(final String name, final Object value) {
      Objects.requireNonNull(name, "name");
      if (value != null) {
        fields.put(name, value);
      }
      return this;
    }

    public Fact build() {
      return new Fact(factType, fields, eventTime, sourcePartition, sourcePosition);
    }
  }
}
