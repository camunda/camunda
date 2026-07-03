/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata;

import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.DatasetKind;
import io.camunda.analytics.dataset.DimensionSpec;
import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.dataset.RegisteredDataset;
import io.camunda.analytics.dataset.store.DatasetSpecStore;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.projection.EnrichmentTiming;
import io.camunda.analytics.store.rdbms.metadata.row.ActivationRow;
import io.camunda.analytics.store.rdbms.metadata.row.DatasetRow;
import io.camunda.analytics.store.rdbms.metadata.row.DimensionRow;
import io.camunda.analytics.store.rdbms.metadata.row.FilterRow;
import io.camunda.analytics.store.rdbms.metadata.row.MeterParamRow;
import io.camunda.analytics.store.rdbms.metadata.row.MeterRow;
import io.camunda.analytics.store.rdbms.metadata.row.WindowRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Persists and reloads {@link RegisteredDataset} specs in the metadata plane as <b>normalized
 * columns</b> via the {@link DatasetSpecMapper} — one row in {@code ANALYTICS_DATASET} plus child
 * rows per filter, dimension, meter (+ param), window, and activation entry. The database is the
 * source of truth for which datasets exist: the control plane bootstraps declarations here once,
 * and both stages reload the full specs and compile them, so a dataset change is a data change, not
 * a redeploy.
 */
final class RdbmsDatasetSpecStore implements DatasetSpecStore {

  private final SqlSessionFactory sessionFactory;

  RdbmsDatasetSpecStore(final SqlSessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public boolean isEmpty() {
    try (SqlSession session = sessionFactory.openSession()) {
      return session.getMapper(DatasetSpecMapper.class).countDatasets() == 0;
    }
  }

  @Override
  public void save(final RegisteredDataset registered) {
    final DatasetDeclaration declaration = registered.declaration();
    final long cubeId = registered.cubeId();
    try (SqlSession session = sessionFactory.openSession()) {
      final DatasetSpecMapper mapper = session.getMapper(DatasetSpecMapper.class);
      mapper.insertDataset(
          new DatasetRow(
              cubeId,
              declaration.name(),
              declaration.sourceFact().name(),
              declaration.kind().name(),
              declaration.keyField(),
              declaration.latenessMs(),
              registered.schemaVersion()));
      final List<FilterPredicate> filters = declaration.filters();
      for (int i = 0; i < filters.size(); i++) {
        final FilterPredicate filter = filters.get(i);
        mapper.insertFilter(
            new FilterRow(cubeId, i, filter.field(), filter.operator().name(), filter.value()));
      }
      final List<DimensionSpec> dimensions = declaration.dimensions();
      for (int i = 0; i < dimensions.size(); i++) {
        final DimensionSpec dimension = dimensions.get(i);
        mapper.insertDimension(
            new DimensionRow(
                cubeId,
                i,
                dimension.name(),
                dimension.type().name(),
                dimension.enrichment().name()));
      }
      final List<Meter> meters = declaration.meters();
      for (int i = 0; i < meters.size(); i++) {
        final Meter meter = meters.get(i);
        mapper.insertMeter(
            new MeterRow(cubeId, i, meter.name(), meter.type(), meter.measureField()));
        final int meterOrdinal = i;
        meter
            .params()
            .forEach(
                (key, value) ->
                    mapper.insertMeterParam(new MeterParamRow(cubeId, meterOrdinal, key, value)));
      }
      final List<Long> windows = declaration.windowSizesMs();
      for (int i = 0; i < windows.size(); i++) {
        mapper.insertWindow(new WindowRow(cubeId, i, windows.get(i)));
      }
      registered
          .activation()
          .forEach(
              (partition, position) ->
                  mapper.insertActivation(new ActivationRow(cubeId, partition, position)));
      session.commit();
    }
  }

  @Override
  public List<RegisteredDataset> loadAll() {
    try (SqlSession session = sessionFactory.openSession()) {
      final DatasetSpecMapper mapper = session.getMapper(DatasetSpecMapper.class);
      final Map<Long, List<FilterPredicate>> filters = filtersByCube(mapper.selectFilters());
      final Map<Long, List<DimensionSpec>> dimensions = dimensionsByCube(mapper.selectDimensions());
      final Map<Long, List<Meter>> meters =
          metersByCube(mapper.selectMeters(), mapper.selectMeterParams());
      final Map<Long, List<Long>> windows = windowsByCube(mapper.selectWindows());
      final Map<Long, Map<Integer, Long>> activation = activationByCube(mapper.selectActivation());

      final List<RegisteredDataset> datasets = new ArrayList<>();
      for (final DatasetRow row : mapper.selectDatasets()) {
        final long cubeId = row.getCubeId();
        final DatasetDeclaration declaration =
            new DatasetDeclaration(
                row.getName(),
                FactType.valueOf(row.getSourceFact()),
                DatasetKind.valueOf(row.getKind()),
                filters.getOrDefault(cubeId, List.of()),
                dimensions.getOrDefault(cubeId, List.of()),
                meters.getOrDefault(cubeId, List.of()),
                windows.getOrDefault(cubeId, List.of()),
                row.getKeyField(),
                row.getLatenessMs());
        datasets.add(
            new RegisteredDataset(
                cubeId,
                declaration,
                activation.getOrDefault(cubeId, Map.of()),
                row.getSchemaVersion()));
      }
      return datasets;
    }
  }

  private static Map<Long, List<FilterPredicate>> filtersByCube(final List<FilterRow> rows) {
    final Map<Long, List<FilterPredicate>> byCube = new LinkedHashMap<>();
    for (final FilterRow row : rows) {
      byCube
          .computeIfAbsent(row.getCubeId(), k -> new ArrayList<>())
          .add(
              new FilterPredicate(
                  row.getField(),
                  FilterPredicate.Operator.valueOf(row.getOperator()),
                  row.getFilterValue()));
    }
    return byCube;
  }

  private static Map<Long, List<DimensionSpec>> dimensionsByCube(final List<DimensionRow> rows) {
    final Map<Long, List<DimensionSpec>> byCube = new LinkedHashMap<>();
    for (final DimensionRow row : rows) {
      byCube
          .computeIfAbsent(row.getCubeId(), k -> new ArrayList<>())
          .add(
              new DimensionSpec(
                  row.getName(),
                  DimensionType.valueOf(row.getDimType()),
                  EnrichmentTiming.valueOf(row.getEnrichment())));
    }
    return byCube;
  }

  private static Map<Long, List<Meter>> metersByCube(
      final List<MeterRow> meterRows, final List<MeterParamRow> paramRows) {
    final Map<Long, Map<Integer, Map<String, String>>> params = new LinkedHashMap<>();
    for (final MeterParamRow row : paramRows) {
      params
          .computeIfAbsent(row.getCubeId(), k -> new TreeMap<>())
          .computeIfAbsent(row.getMeterOrdinal(), k -> new LinkedHashMap<>())
          .put(row.getParamKey(), row.getParamValue());
    }
    final Map<Long, List<Meter>> byCube = new LinkedHashMap<>();
    for (final MeterRow row : meterRows) {
      final Map<String, String> meterParams =
          params.getOrDefault(row.getCubeId(), Map.of()).getOrDefault(row.getOrdinal(), Map.of());
      byCube
          .computeIfAbsent(row.getCubeId(), k -> new ArrayList<>())
          .add(new Meter(row.getName(), row.getMeterType(), row.getMeasureField(), meterParams));
    }
    return byCube;
  }

  private static Map<Long, List<Long>> windowsByCube(final List<WindowRow> rows) {
    final Map<Long, List<Long>> byCube = new LinkedHashMap<>();
    for (final WindowRow row : rows) {
      byCube.computeIfAbsent(row.getCubeId(), k -> new ArrayList<>()).add(row.getWindowMs());
    }
    return byCube;
  }

  private static Map<Long, Map<Integer, Long>> activationByCube(final List<ActivationRow> rows) {
    final Map<Long, Map<Integer, Long>> byCube = new LinkedHashMap<>();
    for (final ActivationRow row : rows) {
      byCube
          .computeIfAbsent(row.getCubeId(), k -> new LinkedHashMap<>())
          .put(row.getPartitionId(), row.getActivationPosition());
    }
    return byCube;
  }
}
