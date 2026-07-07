/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKeySelector;
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
 * schema and its key selector, derives the fact binding (filters + per-variable enrichment timing),
 * and resolves each meter against the {@link MeterCatalog} for every declared window tier — each
 * getting a stable {@code aggId} from the {@link MeterIdRegistry}, keyed by {@code (cube, meter,
 * tier)} so tiers are independent rollups. The physical {@link DatasetSchema} is the grain plus one
 * accumulator column per meter. This is the "think backwards" step: the declaration alone
 * determines the fact stream, the grain, the aggregates, and the serving schema.
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

    final List<CompiledMeter> meters = new ArrayList<>();
    for (final Meter meter : declaration.meters()) {
      for (final long windowMs : declaration.windowSizesMs()) {
        final int aggId = registry.aggIdFor(cubeId, meter.name() + "@" + windowMs);
        meters.add(
            new CompiledMeter(
                meter.name(),
                windowMs,
                aggId,
                bind(declaration, meter),
                TumblingWindows.ofSizeAndGrace(windowMs, declaration.latenessMs())));
      }
    }

    final DatasetSchema schema =
        new DatasetSchema(grain, declaration.meters().stream().map(Meter::name).toList());
    return new CompiledDataset(
        cubeId,
        declaration.name(),
        factBinding,
        grain,
        new DimensionKeySelector(grain),
        meters,
        schema);
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
   * Compiles a {@link DatasetKind#TABLE} declaration into a runnable {@link CompiledTable}: the
   * fact binding (source fact, filters, per-variable enrichment) and the declared dimensions as the
   * projected row columns. No meters/windows/aggIds — a projected row is written directly, not
   * shuffled and reduced.
   */
  public CompiledTable compileTable(final long cubeId, final DatasetDeclaration declaration) {
    if (declaration.kind() != DatasetKind.TABLE) {
      throw new IllegalArgumentException("dataset '" + declaration.name() + "' is not projected");
    }
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
