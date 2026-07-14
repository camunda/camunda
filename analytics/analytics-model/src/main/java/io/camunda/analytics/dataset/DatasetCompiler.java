/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionSchema;
import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import io.camunda.eventbridge.streaming.window.TumblingWindows;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles a {@link DatasetDeclaration} into a runnable {@link CompiledDataset}: builds the grain
 * schema, derives the fact binding (filters + per-variable enrichment timing), and resolves each
 * meter against the {@link MeterCatalog} for every declared window tier — each getting a stable
 * {@code aggId} from the {@link MeterIdRegistry}, keyed by {@code (cube, meter, tier)} so tiers are
 * independent rollups. The physical {@link DatasetSchema} is the grain plus one accumulator column
 * per meter. This is the "think backwards" step: the declaration alone determines the fact stream,
 * the grain, the aggregates, and the serving schema.
 */
public final class DatasetCompiler {

  private final MeterCatalog catalog;
  private final MeterIdRegistry registry;

  public DatasetCompiler(final MeterCatalog catalog, final MeterIdRegistry registry) {
    this.catalog = catalog;
    this.registry = registry;
  }

  public CompiledDataset compile(final long cubeId, final DatasetDeclaration declaration) {
    if (declaration.kind() == DatasetKind.TABLE) {
      throw new IllegalArgumentException(
          "projected dataset '" + declaration.name() + "' must be compiled via compileTable");
    }
    validateFilters(declaration);
    final List<DimensionColumn> columns = new ArrayList<>();
    final Map<String, EnrichmentTiming> enrichment = new LinkedHashMap<>();
    for (final DimensionSpec dimension : declaration.dimensions()) {
      columns.add(new DimensionColumn(dimension.name(), dimension.type()));
      if (dimension.name().startsWith(DimensionSpec.VARIABLE_PREFIX)) {
        enrichment.put(dimension.name(), dimension.enrichment());
      }
    }
    final DimensionSchema grain = DimensionSchema.of(columns);
    final FactBinding factBinding =
        new FactBinding(declaration.sourceFact(), declaration.filters(), enrichment);

    // One compiled meter per declared meter — its index is its composite slot (ADR 0009) — and
    // one tier per declared window size, finest first. The cube's shuffle stream and each tier's
    // durable cell group get stable ids from the registry, keyed so retiring/re-adding never
    // rebinds an id.
    final int streamId = registry.aggIdFor(cubeId, "cube");
    final List<CompiledMeter> meters = new ArrayList<>();
    for (final Meter meter : declaration.meters()) {
      meters.add(new CompiledMeter(meter.name(), bind(declaration, meter)));
    }
    final List<CompiledTier> tiers = new ArrayList<>();
    for (final long windowMs : declaration.windowSizesMs().stream().sorted().toList()) {
      tiers.add(
          new CompiledTier(
              windowMs,
              registry.aggIdFor(cubeId, "cells@" + windowMs),
              TumblingWindows.ofSizeAndGrace(windowMs, declaration.latenessMs())));
    }

    final CompiledSnapshots snapshots = compileSnapshots(cubeId, declaration, meters);

    final DatasetSchema schema =
        new DatasetSchema(grain, declaration.meters().stream().map(Meter::name).toList());
    return new CompiledDataset(
        cubeId, declaration.name(), factBinding, grain, streamId, meters, tiers, snapshots, schema);
  }

  /**
   * Compiles the optional periodic-snapshot configuration. Additive meters only (checked here,
   * after binding, because additivity is a property of the bound meter's pushdown capability): a
   * snapshot row carries every meter's cumulative absolute value, and a cumulative all-time sketch
   * per key is unbounded in meaning; the gate can be lifted later if a real ask appears.
   */
  private CompiledSnapshots compileSnapshots(
      final long cubeId, final DatasetDeclaration declaration, final List<CompiledMeter> meters) {
    if (declaration.snapshotEveryMs() == 0) {
      return null;
    }
    for (final CompiledMeter meter : meters) {
      if (meter.pushdown().isEmpty()) {
        throw new DatasetValidationException(
            "dataset '"
                + declaration.name()
                + "' declares snapshots, but meter '"
                + meter.meterName()
                + "' is not additive — snapshots require additive meters only");
      }
    }
    return new CompiledSnapshots(
        declaration.snapshotEveryMs(),
        registry.aggIdFor(cubeId, "snapshots@" + declaration.snapshotEveryMs()));
  }

  /**
   * Resolves one meter against the catalog — the compile-time validation gate for the stringly
   * typed meter declaration (unknown type, missing measure, malformed params). A bind failure is
   * rethrown as a {@link DatasetValidationException} carrying the dataset + meter context, so a bad
   * declaration is rejected at admission time instead of blowing up a live topology reload.
   */
  private BoundMeter<?, ?> bind(final DatasetDeclaration declaration, final Meter meter) {
    try {
      return catalog.bind(meter);
    } catch (final RuntimeException e) {
      throw new DatasetValidationException(
          "dataset '"
              + declaration.name()
              + "' declares an invalid meter '"
              + meter.name()
              + "' ("
              + meter.type()
              + "): "
              + e.getMessage(),
          e);
    }
  }

  /**
   * The compile-time validation gate for the stringly typed filter values — an ordering operator
   * needs a numeric bound, {@code IN} a non-empty list (see {@link
   * FilterPredicate#validateValue()}). Covers the dataset-level filters and every meter's per-meter
   * filters. A failure is rethrown as a {@link DatasetValidationException} carrying the dataset (+
   * meter) + filter context, the same admission gate as meter params: a bad declaration is rejected
   * at compile/provisioning time instead of silently never matching a fact.
   */
  private static void validateFilters(final DatasetDeclaration declaration) {
    for (final FilterPredicate filter : declaration.filters()) {
      try {
        filter.validateValue();
      } catch (final RuntimeException e) {
        // reads: dataset 'x' declares an invalid filter on 'f' (GT): value must be ...
        throw new DatasetValidationException(
            "dataset '" + declaration.name() + "' declares an invalid " + e.getMessage(), e);
      }
    }
    for (final Meter meter : declaration.meters()) {
      for (final FilterPredicate filter : meter.filters()) {
        try {
          filter.validateValue();
        } catch (final RuntimeException e) {
          // reads: dataset 'x' meter 'm' (count) declares an invalid filter on 'f' (GT): ...
          throw new DatasetValidationException(
              "dataset '"
                  + declaration.name()
                  + "' meter '"
                  + meter.name()
                  + "' ("
                  + meter.type()
                  + ") declares an invalid "
                  + e.getMessage(),
              e);
        }
      }
    }
  }

  /**
   * Compiles a {@link DatasetKind#TABLE} declaration into a runnable {@link CompiledTable}: the
   * fact binding (source fact, filters, per-variable enrichment) and the declared dimensions as the
   * projected row columns. No meters/windows/aggIds — a projected row is written directly, not
   * shuffled and reduced.
   */
  public CompiledTable compileTable(final long cubeId, final DatasetDeclaration declaration) {
    if (declaration.kind() != DatasetKind.TABLE) {
      throw new IllegalArgumentException("dataset '" + declaration.name() + "' is not projected");
    }
    validateFilters(declaration);
    final List<DimensionColumn> columns = new ArrayList<>();
    final Map<String, EnrichmentTiming> enrichment = new LinkedHashMap<>();
    for (final DimensionSpec dimension : declaration.dimensions()) {
      columns.add(new DimensionColumn(dimension.name(), dimension.type()));
      if (dimension.name().startsWith(DimensionSpec.VARIABLE_PREFIX)) {
        enrichment.put(dimension.name(), dimension.enrichment());
      }
    }
    final FactBinding factBinding =
        new FactBinding(declaration.sourceFact(), declaration.filters(), enrichment);
    return new CompiledTable(
        cubeId, declaration.name(), factBinding, declaration.keyField(), columns);
  }
}
