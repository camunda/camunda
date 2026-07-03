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
import io.camunda.analytics.projection.EnrichmentTiming;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

  public DatasetDeclaration {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(sourceFact, "sourceFact");
    kind = kind == null ? DatasetKind.AGGREGATED : kind;
    if (name.isBlank()) {
      throw new IllegalArgumentException("dataset name must not be blank");
    }
    filters = List.copyOf(filters == null ? List.of() : filters);
    dimensions = List.copyOf(dimensions == null ? List.of() : dimensions);
    meters = List.copyOf(meters == null ? List.of() : meters);
    windowSizesMs = List.copyOf(windowSizesMs == null ? List.of() : windowSizesMs);
    requireUnique(dimensions.stream().map(DimensionSpec::name).toList(), "dimension");
    requireUnique(meters.stream().map(Meter::name).toList(), "meter");
    if (kind == DatasetKind.PROJECTED) {
      // A projected (raw) dataset is a flat, keyed row list — no windowed aggregation.
      if (keyField == null || keyField.isBlank()) {
        throw new IllegalArgumentException(
            "projected dataset '" + name + "' declares no key field");
      }
      if (dimensions.isEmpty()) {
        throw new IllegalArgumentException(
            "projected dataset '" + name + "' declares no projected columns");
      }
      if (!meters.isEmpty() || !windowSizesMs.isEmpty()) {
        throw new IllegalArgumentException(
            "projected dataset '" + name + "' must not declare meters or windows");
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
      for (final long windowMs : windowSizesMs) {
        if (windowMs <= 0) {
          throw new IllegalArgumentException(
              "dataset '" + name + "' has a non-positive window size: " + windowMs);
        }
      }
    }
    if (latenessMs < 0) {
      throw new IllegalArgumentException("lateness must be non-negative, was " + latenessMs);
    }
  }

  private static void requireUnique(final List<String> names, final String what) {
    if (names.stream().distinct().count() != names.size()) {
      throw new IllegalArgumentException(what + " names must be unique: " + names);
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
     * Marks this as a projected (raw) dataset keyed by {@code keyField}: the declared dimensions
     * are the projected row columns, and no meters/windows may be declared.
     */
    public Builder projectedBy(final String keyField) {
      kind = DatasetKind.PROJECTED;
      this.keyField = keyField;
      return this;
    }

    public Builder filterEquals(final String field, final String value) {
      filters.add(FilterPredicate.equals(field, value));
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
