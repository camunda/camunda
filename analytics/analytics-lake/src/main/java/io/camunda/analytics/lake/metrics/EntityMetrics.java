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
   * @param rawSchema the row shape dims/measures are resolved against — a {@link TableSchema} is
   *     used purely as a typed column catalog here, and need not back a physical sink table. A
   *     row-fed entity (see {@code io.camunda.analytics.lake.metrics.MetricsRider}) naturally
   *     passes an actual raw pipeline's own schema, since its rows really do live in that table. A
   *     record-fed entity (see {@code io.camunda.analytics.lake.metrics.PollFedRider}) instead
   *     declares a purely <b>logical</b> row shape — the tuple of typed fields one qualifying Zeebe
   *     record contributes — with no {@link io.camunda.analytics.lake.sink.pipeline.SinkPipeline}
   *     ever materializing rows of it: this is the intended generalization, not a workaround, and
   *     is why every method here validates against {@code rawSchema} by column name/type alone,
   *     never against anything that would require a real backing table (segments, a ring, a sorter)
   *     to exist.
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
    private String windowSource; // null == default to the raw schema's familyDaySource column
    private final Map<String, List<Algebra>> measures = new LinkedHashMap<>();
    private boolean counted;

    private Builder(final String entityName, final TableSchema rawSchema) {
      this.entityName = entityName;
      this.rawSchema = rawSchema;
    }

    /** Dimension column names, in the order they participate in group-by and sort order. */
    public Builder dims(final String... dimNames) {
      dims = List.of(dimNames);
      return this;
    }

    /**
     * Window duration for time-bucketed metrics; omit for a single all-time group per dims. The
     * slot is derived from the raw schema's own familyDaySource column — use {@link
     * #window(Duration, String)} when the semantically right event time is a different column (e.g.
     * an activity's {@code ended_at} rather than the partition day's source).
     */
    public Builder window(final Duration window) {
      this.window = window;
      return this;
    }

    /**
     * Window duration plus the raw column supplying each row's event time (epoch microseconds, i.e.
     * a {@code LONG} column — typically a {@code TIMESTAMPTZ}-logical one). Which column assigns
     * the slot changes what a stored partial row <em>means</em>, so it is part of the declaration
     * fingerprint.
     */
    public Builder window(final Duration window, final String sourceColumn) {
      this.window = window;
      windowSource = sourceColumn;
      return this;
    }

    /** Declares one measure, folded through every given algebra. */
    public Builder measure(final String name, final Algebra... algebras) {
      measures.put(name, List.of(algebras));
      return this;
    }

    /**
     * Declares a plain row count alongside (or instead of) any measures: the entity's {@code
     * _metrics} row gains an unprefixed, non-nullable {@code cnt} column counting every row that
     * reached a group (see {@link io.camunda.analytics.lake.sink.algebra.CountAlgebra}'s own
     * javadoc for why it needs no raw column and no measure-name prefix). At most once per
     * declaration — a count either exists or doesn't, there is nothing to disambiguate a second
     * call from the first.
     *
     * <p>A declaration may combine {@code count()} with one or more {@code measure(...)} calls (a
     * count is orthogonal to any measure), or declare {@code count()} alone with no measures at all
     * — the latter is exactly what a record-fed entity with nothing to measure but "how many" needs
     * (see {@code io.camunda.analytics.lake.metrics.PollFedRider}'s own class javadoc).
     *
     * @throws IllegalStateException if called twice on the same builder
     */
    public Builder count() {
      if (counted) {
        throw new IllegalStateException(
            "entity '" + entityName + "': count() was already declared");
      }
      counted = true;
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
      if (measures.isEmpty() && !counted) {
        throw new IllegalArgumentException(
            "entity '"
                + entityName
                + "' declares no measures and no count() — at least one is required");
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
      final int windowSourceColumn = resolveWindowSource();
      final int runPrefixLength = computeRunPrefixLength(dimColumnIndexes);
      final RiderPlan riderPlan =
          new RiderPlan(
              dimColumnIndexes,
              measureColumnIndexes,
              windowMicros,
              windowSourceColumn,
              runPrefixLength);

      return new CompiledEntityMetrics(
          entityName, rawSchema, dims, windowMicros, measureDeclarations, riderPlan, counted);
    }

    /**
     * The raw column each row's window slot derives from: the explicitly named one (validated to be
     * a {@code LONG} epoch-microseconds column), else the raw schema's own familyDaySource column;
     * {@code -1} when the declaration is unwindowed.
     */
    private int resolveWindowSource() {
      if (window == null) {
        if (windowSource != null) {
          throw new IllegalArgumentException(
              "entity '"
                  + entityName
                  + "': a window source column ('"
                  + windowSource
                  + "') was named but no window duration was declared");
        }
        return -1;
      }
      if (windowSource == null) {
        return rawSchema.familyDayColumn();
      }
      final int rawIndex = columnIndex(windowSource);
      if (rawIndex < 0) {
        throw new IllegalArgumentException(
            "entity '"
                + entityName
                + "': window source column '"
                + windowSource
                + "' not found in raw schema '"
                + rawSchema.table()
                + "'");
      }
      if (rawSchema.columns().get(rawIndex).type() != ColumnType.LONG) {
        throw new IllegalArgumentException(
            "entity '"
                + entityName
                + "': window source column '"
                + windowSource
                + "' must be a LONG epoch-microseconds column, got "
                + rawSchema.columns().get(rawIndex).type());
      }
      return rawIndex;
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
