/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.Meter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The single source of truth for a dataset: what facts to aggregate ({@code sourceFact} + {@code
 * filters}), how to group them ({@code dimensions}, in order — the cube's grain), what to compute
 * ({@code meters}), and over which windows ({@code windowSizesMs} — one per tier, e.g. 1m/1h/1d).
 * The compiler derives from this the fact binding, the dimension schema, the bound meters (with
 * their stable aggIds), and the physical serving schema; a new metric or grouping is a new
 * declaration, not new Java. Pure data — the "think backwards" declaration the deck describes.
 */
public record DatasetDeclaration(
    String name,
    FactType sourceFact,
    DatasetKind kind,
    List<FilterPredicate> filters,
    List<DimensionSpec> dimensions,
    List<Meter> meters,
    List<Long> windowSizesMs,
    String keyField,
    long latenessMs) {

  /**
   * A dataset name is a metadata-plane lookup key (never a physical identifier), so its charset is
   * wide enough for the report builder's derived names ({@code q:<entity>:<measure>:…:g:<ms>},
   * which carry {@code : = , .}) — but it must never contain whitespace, quotes, or {@code @}.
   */
  private static final Pattern SAFE_DATASET_NAME = Pattern.compile("[a-zA-Z][a-zA-Z0-9_.:=,-]*");

  /**
   * A meter name feeds two derivations that make its charset strict: the {@code aggId} registry key
   * is {@code <name>@<windowMs>} (so {@code @} must be impossible — a meter literally named {@code
   * foo@60000} would collide with meter {@code foo} at the 60s tier), and the physical serving
   * column is derived from it (so it must already be a plain identifier; see the serving-side
   * {@code Identifiers} allowlist this mirrors). Structural dimension names become columns the same
   * way.
   */
  private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*");

  /** Postgres caps identifiers at 63 bytes; mirror the serving-side 60-char cap. */
  private static final int MAX_IDENTIFIER_LENGTH = 60;

  public DatasetDeclaration {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(sourceFact, "sourceFact");
    kind = kind == null ? DatasetKind.AGGREGATED : kind;
    if (name.isBlank()) {
      throw new IllegalArgumentException("dataset name must not be blank");
    }
    if (!SAFE_DATASET_NAME.matcher(name).matches()) {
      throw new IllegalArgumentException(
          "dataset name '"
              + name
              + "' is not a safe name: it must start with a letter and contain only letters,"
              + " digits, and '_ - . : = ,' (no whitespace, quotes, or '@')");
    }
    filters = List.copyOf(filters == null ? List.of() : filters);
    dimensions = List.copyOf(dimensions == null ? List.of() : dimensions);
    meters = List.copyOf(meters == null ? List.of() : meters);
    windowSizesMs = List.copyOf(windowSizesMs == null ? List.of() : windowSizesMs);
    requireUnique(dimensions.stream().map(DimensionSpec::name).toList(), "dimension");
    requireUnique(meters.stream().map(Meter::name).toList(), "meter");
    for (final Meter meter : meters) {
      requireSafeIdentifier(name, "meter", meter.name());
    }
    for (final DimensionSpec dimension : dimensions) {
      requireSafeDimensionName(name, dimension.name());
    }
    if (kind == DatasetKind.TABLE) {
      // A table is a flat, keyed row list — no windowed aggregation.
      if (keyField == null || keyField.isBlank()) {
        throw new IllegalArgumentException("table '" + name + "' declares no key field");
      }
      if (dimensions.isEmpty()) {
        throw new IllegalArgumentException("table '" + name + "' declares no columns");
      }
      if (!meters.isEmpty() || !windowSizesMs.isEmpty()) {
        throw new IllegalArgumentException(
            "table '" + name + "' must not declare meters or windows");
      }
    } else {
      if (keyField != null) {
        throw new IllegalArgumentException(
            "aggregated dataset '" + name + "' must not declare a key field");
      }
      if (meters.isEmpty()) {
        throw new IllegalArgumentException("dataset '" + name + "' declares no meters");
      }
      if (windowSizesMs.isEmpty()) {
        throw new IllegalArgumentException("dataset '" + name + "' declares no window tiers");
      }
      requireValidTiers(name, windowSizesMs);
    }
    if (latenessMs < 0) {
      throw new IllegalArgumentException("lateness must be non-negative, was " + latenessMs);
    }
  }

  /**
   * The window tiers must be positive, strictly ascending (which also rejects duplicates), and
   * every coarser tier must be an integer multiple of the finest tier: Stage 2 derives coarse cells
   * by rolling up finest-tier cells, and the query planner picks tiers by divisibility — a
   * non-multiple tier (e.g. 90s next to 120s) would silently produce wrong coarse cells.
   */
  private static void requireValidTiers(final String name, final List<Long> windowSizesMs) {
    for (final long windowMs : windowSizesMs) {
      if (windowMs <= 0) {
        throw new IllegalArgumentException(
            "dataset '" + name + "' has a non-positive window size: " + windowMs);
      }
    }
    final long finest = windowSizesMs.get(0);
    for (int i = 1; i < windowSizesMs.size(); i++) {
      final long previous = windowSizesMs.get(i - 1);
      final long current = windowSizesMs.get(i);
      if (current <= previous) {
        throw new IllegalArgumentException(
            "dataset '"
                + name
                + "' window tiers must be strictly ascending (no duplicates): "
                + current
                + "ms follows "
                + previous
                + "ms in "
                + windowSizesMs);
      }
      if (current % finest != 0) {
        throw new IllegalArgumentException(
            "dataset '"
                + name
                + "' window tier "
                + current
                + "ms is not an integer multiple of the finest tier "
                + finest
                + "ms — coarse cells are rolled up from finest-tier cells, so every tier must"
                + " divide evenly");
      }
    }
  }

  private static void requireUnique(final List<String> names, final String what) {
    if (names.stream().distinct().count() != names.size()) {
      throw new IllegalArgumentException(what + " names must be unique: " + names);
    }
  }

  private static void requireSafeIdentifier(
      final String dataset, final String what, final String identifier) {
    if (!SAFE_IDENTIFIER.matcher(identifier).matches()
        || identifier.length() > MAX_IDENTIFIER_LENGTH) {
      throw new IllegalArgumentException(
          "dataset '"
              + dataset
              + "' declares an unsafe "
              + what
              + " name '"
              + identifier
              + "': it must start with a letter, contain only letters, digits, and '_', and be at"
              + " most "
              + MAX_IDENTIFIER_LENGTH
              + " chars");
    }
  }

  /**
   * A structural dimension name is a plain identifier (it becomes a physical column). A {@code
   * var.*} dimension names user process data, so only what breaks the physical derivation is
   * forbidden: after folding the namespace dots to underscores (exactly what the serving-side
   * identifier mapping does), the result must still be a plain identifier — which keeps dots legal
   * inside variable names but rejects whitespace, quotes, {@code @}, and other metacharacters.
   */
  private static void requireSafeDimensionName(final String dataset, final String dimension) {
    if (dimension.startsWith(DimensionSpec.VARIABLE_PREFIX)) {
      final String folded = dimension.replace('.', '_');
      if (dimension.length() == DimensionSpec.VARIABLE_PREFIX.length()
          || !SAFE_IDENTIFIER.matcher(folded).matches()
          || folded.length() > MAX_IDENTIFIER_LENGTH) {
        throw new IllegalArgumentException(
            "dataset '"
                + dataset
                + "' declares an unsafe variable dimension '"
                + dimension
                + "': after 'var.', the variable name may contain only letters, digits, '_' and"
                + " '.', and the whole name must be at most "
                + MAX_IDENTIFIER_LENGTH
                + " chars");
      }
    } else {
      requireSafeIdentifier(dataset, "dimension", dimension);
    }
  }

  public static Builder builder(final String name, final FactType sourceFact) {
    return new Builder(name, sourceFact);
  }

  /** Fluent builder; lists accumulate in declaration order. */
  public static final class Builder {

    private final String name;
    private final FactType sourceFact;
    private final List<FilterPredicate> filters = new ArrayList<>();
    private final List<DimensionSpec> dimensions = new ArrayList<>();
    private final List<Meter> meters = new ArrayList<>();
    private final List<Long> windowSizesMs = new ArrayList<>();
    private DatasetKind kind = DatasetKind.AGGREGATED;
    private String keyField;
    private long latenessMs;

    private Builder(final String name, final FactType sourceFact) {
      this.name = name;
      this.sourceFact = sourceFact;
    }

    /**
     * Marks this as a {@link DatasetKind#TABLE raw table} keyed by {@code keyField}: the declared
     * dimensions are the row columns, and no meters/windows may be declared.
     */
    public Builder asTable(final String keyField) {
      kind = DatasetKind.TABLE;
      this.keyField = keyField;
      return this;
    }

    public Builder filter(final FilterPredicate filter) {
      filters.add(filter);
      return this;
    }

    public Builder filterNotEquals(final String field, final String value) {
      filters.add(FilterPredicate.notEquals(field, value));
      return this;
    }

    public Builder filterEquals(final String field, final String value) {
      filters.add(FilterPredicate.equals(field, value));
      return this;
    }

    public Builder filterLessThan(final String field, final String value) {
      filters.add(FilterPredicate.lessThan(field, value));
      return this;
    }

    public Builder filterLessOrEqual(final String field, final String value) {
      filters.add(FilterPredicate.lessOrEqual(field, value));
      return this;
    }

    public Builder filterGreaterThan(final String field, final String value) {
      filters.add(FilterPredicate.greaterThan(field, value));
      return this;
    }

    public Builder filterGreaterOrEqual(final String field, final String value) {
      filters.add(FilterPredicate.greaterOrEqual(field, value));
      return this;
    }

    /** Keep facts whose {@code field} is in the comma-separated {@code values} list. */
    public Builder filterIn(final String field, final String values) {
      filters.add(FilterPredicate.in(field, values));
      return this;
    }

    public Builder filterIsNull(final String field) {
      filters.add(FilterPredicate.isNull(field));
      return this;
    }

    public Builder filterNotNull(final String field) {
      filters.add(FilterPredicate.notNull(field));
      return this;
    }

    public Builder dimension(final String dimensionName, final DimensionType type) {
      dimensions.add(DimensionSpec.of(dimensionName, type));
      return this;
    }

    public Builder dimension(
        final String dimensionName, final DimensionType type, final EnrichmentTiming enrichment) {
      dimensions.add(new DimensionSpec(dimensionName, type, enrichment));
      return this;
    }

    public Builder meter(final Meter meter) {
      meters.add(meter);
      return this;
    }

    public Builder window(final long windowMs) {
      windowSizesMs.add(windowMs);
      return this;
    }

    public Builder lateness(final long lateness) {
      latenessMs = lateness;
      return this;
    }

    public DatasetDeclaration build() {
      return new DatasetDeclaration(
          name, sourceFact, kind, filters, dimensions, meters, windowSizesMs, keyField, latenessMs);
    }
  }
}
