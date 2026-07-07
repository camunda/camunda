/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.fact;

import io.camunda.analytics.dataset.DimensionSpec;
import io.camunda.analytics.dimension.FactRow;
import io.camunda.analytics.dimension.Utf8View;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

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

  // Lazy variable enrichment: the visible variable snapshot is resolved on first var.* access and
  // memoized, so the many cube-meters that read the same forwarded fact resolve it at most once
  // (and a fact no dataset groups/filters by a variable never resolves any). Null for a fact with
  // no variable scope. Not part of identity — see equals/hashCode.
  private final Supplier<Map<String, Utf8View>> variableSource;
  private Map<String, Utf8View> resolvedVariables;

  public Fact(
      final FactType factType,
      final Map<String, Object> fields,
      final long eventTime,
      final int sourcePartition,
      final long sourcePosition) {
    this(factType, fields, eventTime, sourcePartition, sourcePosition, null);
  }

  private Fact(
      final FactType factType,
      final Map<String, Object> fields,
      final long eventTime,
      final int sourcePartition,
      final long sourcePosition,
      final Supplier<Map<String, Utf8View>> variableSource) {
    this.factType = Objects.requireNonNull(factType, "factType");
    this.fields = Map.copyOf(fields);
    this.eventTime = eventTime;
    this.sourcePartition = sourcePartition;
    this.sourcePosition = sourcePosition;
    this.variableSource = variableSource;
  }

  public static Builder builder(final FactType factType) {
    return new Builder(factType);
  }

  @Override
  public Object get(final String field) {
    final Object eager = fields.get(field);
    if (eager != null) {
      return eager;
    }
    // A var.* field the deriver did not stamp eagerly resolves lazily off the projection.
    if (variableSource != null && field.startsWith(DimensionSpec.VARIABLE_PREFIX)) {
      return variables().get(field.substring(DimensionSpec.VARIABLE_PREFIX.length()));
    }
    return null;
  }

  /** The visible variable snapshot, resolved once on first access and memoized. */
  private Map<String, Utf8View> variables() {
    if (resolvedVariables == null) {
      resolvedVariables = variableSource.get();
    }
    return resolvedVariables;
  }

  /**
   * Resolves the lazy variables into a self-contained fact whose {@code var.*} fields are eager — a
   * snapshot to take while the projection is still live (e.g. handing the fact off past the point
   * the source rows are evicted). A fact with no variable source returns itself.
   */
  public Fact materialize() {
    if (variableSource == null) {
      return this;
    }
    final Map<String, Object> merged = new LinkedHashMap<>(fields);
    variables().forEach((name, value) -> merged.put(DimensionSpec.VARIABLE_PREFIX + name, value));
    return new Fact(factType, merged, eventTime, sourcePartition, sourcePosition, null);
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
    private Supplier<Map<String, Utf8View>> variableSource;

    private Builder(final FactType factType) {
      this.factType = Objects.requireNonNull(factType, "factType");
    }

    /**
     * Binds a lazy source of the fact's visible variable snapshot; {@code var.*} reads resolve (and
     * memoize) through it. The supplier is invoked at most once, on first access, so pass a cheap
     * reference to live projection state rather than a pre-collected map.
     */
    public Builder variables(final Supplier<Map<String, Utf8View>> variableSource) {
      this.variableSource = variableSource;
      return this;
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
      if (transition != null) {
        fields.put(TRANSITION, transition.name());
      }
      return this;
    }

    /**
     * Sets a named field; null values are ignored so absence and null coincide. The reserved {@link
     * #TRANSITION} name is rejected — the lifecycle transition is typed, so it must be set via
     * {@link #transition(Transition)}, never as a free-form field that could silently disagree with
     * the {@link Transition} enum.
     */
    public Builder field(final String name, final Object value) {
      Objects.requireNonNull(name, "name");
      if (TRANSITION.equals(name)) {
        throw new IllegalArgumentException(
            "field name 'transition' is reserved for the lifecycle transition; set it via"
                + " transition(Transition)");
      }
      if (value != null) {
        fields.put(name, value);
      }
      return this;
    }

    public Fact build() {
      return new Fact(factType, fields, eventTime, sourcePartition, sourcePosition, variableSource);
    }
  }
}
