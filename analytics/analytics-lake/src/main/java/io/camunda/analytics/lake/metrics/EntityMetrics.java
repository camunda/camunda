/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.metrics;

import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.TableSchema;
import io.camunda.analytics.lake.sink.algebra.Algebra;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point for declaring an entity's dimensions, window, and measures, e.g.:
 *
 * <pre>{@code
 * EntityMetrics.declare("user_tasks", USER_TASKS_SCHEMA)
 *     .dims("process_id", "element_id")
 *     .window(Duration.ofMinutes(1))
 *     .measure("work_time_ms", Algebras.scalarStats(), Algebras.expHistogram(3))
 *     .build();
 * }</pre>
 *
 * <p>{@link Builder#build()} validates the declaration against the raw schema and compiles it into
 * a {@link CompiledEntityMetrics} — see that class for the rider plan, generated partials schemas,
 * fingerprint, and generated merge/finalize SQL this produces.
 */
public final class EntityMetrics {

  private EntityMetrics() {}

  /**
   * @param entityName a non-empty name identifying this entity (e.g. {@code user_tasks}); used to
   *     name the generated partials tables ({@code <entityName>_metrics}/{@code <entityName>_hist})
   * @param rawSchema the raw table schema dims/measures are resolved against
   */
  public static Builder declare(final String entityName, final TableSchema rawSchema) {
    return new Builder(entityName, rawSchema);
  }

  /** Mutable, single-use builder returned by {@link #declare}. */
  public static final class Builder {

    private final String entityName;
    private final TableSchema rawSchema;
    private List<String> dims = List.of();
    private Duration window; // null == NONE
    private final Map<String, List<Algebra>> measures = new LinkedHashMap<>();

    private Builder(final String entityName, final TableSchema rawSchema) {
      this.entityName = entityName;
      this.rawSchema = rawSchema;
    }

    /** Dimension column names, in the order they participate in group-by and sort order. */
    public Builder dims(final String... dimNames) {
      dims = List.of(dimNames);
      return this;
    }

    /** Window duration for time-bucketed metrics; omit for a single all-time group per dims. */
    public Builder window(final Duration window) {
      this.window = window;
      return this;
    }

    /** Declares one measure, folded through every given algebra. */
    public Builder measure(final String name, final Algebra... algebras) {
      measures.put(name, List.of(algebras));
      return this;
    }

    /**
     * Validates the declaration against the raw schema and compiles it.
     *
     * @throws IllegalArgumentException with a precise message identifying which rule failed
     */
    public CompiledEntityMetrics build() {
      if (entityName == null || entityName.isBlank()) {
        throw new IllegalArgumentException("entity name must not be blank");
      }
      if (dims.isEmpty()) {
        throw new IllegalArgumentException(
            "entity '" + entityName + "' declares no dims — at least one is required");
      }
      if (measures.isEmpty()) {
        throw new IllegalArgumentException(
            "entity '" + entityName + "' declares no measures — at least one is required");
      }

      final int[] dimColumnIndexes = new int[dims.size()];
      for (int i = 0; i < dims.size(); i++) {
        final String dim = dims.get(i);
        if (dim == null || dim.isBlank()) {
          throw new IllegalArgumentException(
              "entity '" + entityName + "': dim name at position " + i + " is blank");
        }
        final int rawIndex = columnIndex(dim);
        if (rawIndex < 0) {
          throw new IllegalArgumentException(
              "entity '"
                  + entityName
                  + "': dim '"
                  + dim
                  + "' not found in raw schema '"
                  + rawSchema.table()
                  + "'");
        }
        final TableSchema.Column column = rawSchema.columns().get(rawIndex);
        if (column.type() != ColumnType.STRING_DICT && column.type() != ColumnType.INT) {
          throw new IllegalArgumentException(
              "entity '"
                  + entityName
                  + "': dim '"
                  + dim
                  + "' has type "
                  + column.type()
                  + " in raw schema '"
                  + rawSchema.table()
                  + "', must be STRING_DICT or INT");
        }
        dimColumnIndexes[i] = rawIndex;
      }

      if (window != null) {
        if (window.compareTo(Duration.ofSeconds(1)) < 0) {
          throw new IllegalArgumentException(
              "entity '"
                  + entityName
                  + "': window "
                  + window
                  + " is shorter than the minimum of 1 second");
        }
        final long windowMillis = window.toMillis();
        final long hourMillis = Duration.ofHours(1).toMillis();
        if (windowMillis <= 0 || hourMillis % windowMillis != 0) {
          throw new IllegalArgumentException(
              "entity '"
                  + entityName
                  + "': window "
                  + window
                  + " must evenly divide 1 hour (for an epoch-aligned grid), got "
                  + windowMillis
                  + "ms");
        }
      }

      final List<MeasureDeclaration> measureDeclarations = new ArrayList<>(measures.size());
      final int[] measureColumnIndexes = new int[measures.size()];
      int m = 0;
      for (final Map.Entry<String, List<Algebra>> entry : measures.entrySet()) {
        final String measure = entry.getKey();
        if (measure == null || measure.isBlank()) {
          throw new IllegalArgumentException(
              "entity '" + entityName + "': a measure name is blank");
        }
        if (entry.getValue().isEmpty()) {
          throw new IllegalArgumentException(
              "entity '"
                  + entityName
                  + "': measure '"
                  + measure
                  + "' declares no algebras — at least one is required");
        }
        final int rawIndex = columnIndex(measure);
        if (rawIndex < 0) {
          throw new IllegalArgumentException(
              "entity '"
                  + entityName
                  + "': measure '"
                  + measure
                  + "' not found in raw schema '"
                  + rawSchema.table()
                  + "'");
        }
        final TableSchema.Column column = rawSchema.columns().get(rawIndex);
        if (column.type() != ColumnType.LONG) {
          throw new IllegalArgumentException(
              "entity '"
                  + entityName
                  + "': measure '"
                  + measure
                  + "' has type "
                  + column.type()
                  + " in raw schema '"
                  + rawSchema.table()
                  + "', must be LONG");
        }
        measureColumnIndexes[m] = rawIndex;
        measureDeclarations.add(new MeasureDeclaration(measure, rawIndex, entry.getValue()));
        m++;
      }

      final long windowMicros = window == null ? 0L : window.toNanos() / 1000L;
      final int runPrefixLength = computeRunPrefixLength(dimColumnIndexes);
      final RiderPlan riderPlan =
          new RiderPlan(dimColumnIndexes, measureColumnIndexes, windowMicros, runPrefixLength);

      return new CompiledEntityMetrics(
          entityName, rawSchema, dims, windowMicros, measureDeclarations, riderPlan);
    }

    private int columnIndex(final String name) {
      final List<TableSchema.Column> columns = rawSchema.columns();
      for (int i = 0; i < columns.size(); i++) {
        if (columns.get(i).name().equals(name)) {
          return i;
        }
      }
      return -1;
    }

    private int computeRunPrefixLength(final int[] dimColumnIndexes) {
      final int[] sortKeyColumns = rawSchema.sortKeyColumns();
      int prefix = 0;
      while (prefix < dimColumnIndexes.length
          && prefix < sortKeyColumns.length
          && dimColumnIndexes[prefix] == sortKeyColumns[prefix]) {
        prefix++;
      }
      return prefix;
    }
  }
}
