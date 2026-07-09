/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.ActiveTable;
import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.meter.CompositeAccumulatorValue;
import io.camunda.analytics.meter.CompositeAggregateFunction;
import io.camunda.analytics.query.DatasetQueryExecutor;
import io.camunda.analytics.query.DatasetQueryPlanner;
import io.camunda.analytics.query.TableQueryExecutor;
import io.camunda.analytics.serving.catalog.StandardDatasets;
import io.camunda.analytics.serving.spi.DatasetStore;
import io.camunda.analytics.serving.spi.MetadataStore;
import io.camunda.analytics.store.rdbms.RdbmsDatasetStore;
import io.camunda.analytics.store.rdbms.metadata.RdbmsMetadataStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;

/**
 * Builds an H2-backed serving stack for the read-path tests: an {@link RdbmsMetadataStore} + {@link
 * RdbmsDatasetStore} on a fresh in-memory database, migrated and bootstrapped with the standard
 * dataset specs, plus the compiled {@link DatasetCatalog} and a {@link DatasetQueryExecutor}. Seeds
 * cells the same way {@code DemoSeeder} does — folding facts through a cube's declared meter and
 * upserting the encoded accumulator.
 */
final class ServingTestSupport {

  static final long BASE_MS = 1_700_000_000_000L; // fixed, in the past, aligns cleanly

  private ServingTestSupport() {}

  static Fixture create() {
    final JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:serving-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");

    final MetadataStore metadataStore = new RdbmsMetadataStore(dataSource);
    metadataStore.migrate();
    StandardDatasets.bootstrap(metadataStore);

    final DatasetStore datasetStore = new RdbmsDatasetStore(dataSource);
    final Map<String, CompiledDataset> byName = new LinkedHashMap<>();
    for (final ActiveCube cube : StandardDatasets.loadCubes(metadataStore)) {
      final CompiledDataset compiled = cube.compiled();
      datasetStore.schemaManager().ensure(compiled);
      byName.put(compiled.name(), compiled);
    }
    final DatasetCatalog catalog = new DatasetCatalog(byName);
    final DatasetQueryExecutor executor =
        new DatasetQueryExecutor(new DatasetQueryPlanner(), datasetStore.queryClient());

    final Map<String, CompiledTable> tablesByName = new LinkedHashMap<>();
    for (final ActiveTable table : StandardDatasets.loadTables(metadataStore)) {
      final CompiledTable compiled = table.compiled();
      datasetStore.schemaManager().ensureTable(compiled);
      tablesByName.put(compiled.name(), compiled);
    }
    final TableCatalog tableCatalog = new TableCatalog(tablesByName);
    final TableRepository tableRepository =
        new TableRepository(new TableQueryExecutor(datasetStore.queryClient()), tableCatalog);
    return new Fixture(
        metadataStore, datasetStore, catalog, executor, tableCatalog, tableRepository);
  }

  /** Seeds one raw table row (upsert by key), mirroring how Stage 1 writes a table. */
  static void seedRow(
      final Fixture fixture,
      final String tableName,
      final String rowKey,
      final List<Object> values) {
    final CompiledTable table = fixture.tableCatalog().require(tableName);
    fixture.datasetStore().writer().upsertRow(table, rowKey, values);
    fixture.datasetStore().writer().flush();
  }

  /** Aligns a window start to {@code tier}, in the range the default read window covers. */
  static long window(final long tier) {
    return BASE_MS - Math.floorMod(BASE_MS, tier);
  }

  /** Folds the facts into the dataset's composite accumulator — every meter slot at once. */
  static byte[] fold(final CompiledDataset dataset, final List<Fact> facts) {
    final CompositeAggregateFunction aggregate =
        new CompositeAggregateFunction(dataset.meterBounds());
    Object[] accumulator = aggregate.createAccumulator();
    for (final Fact fact : facts) {
      accumulator = aggregate.add(fact, accumulator);
    }
    return new CompositeAccumulatorValue(dataset.meterBounds()).toBytes(accumulator);
  }

  /** Seeds one cube cell (finest tier, single aligned window) and returns its window start. */
  static long seed(
      final Fixture fixture,
      final String cubeName,
      final String meterName,
      final List<Fact> facts,
      final Object... keyValues) {
    final CompiledDataset dataset = fixture.catalog().require(cubeName);
    final long windowMs = dataset.finestTier().windowMs();
    final long windowStart = window(windowMs);
    final DimensionKey key = DimensionKey.of(dataset.grain(), keyValues);
    fixture
        .datasetStore()
        .writer()
        .upsertCell(dataset, key, windowStart, windowMs, fold(dataset, facts));
    fixture.datasetStore().writer().flush();
    return windowStart;
  }

  record Fixture(
      MetadataStore metadataStore,
      DatasetStore datasetStore,
      DatasetCatalog catalog,
      DatasetQueryExecutor executor,
      TableCatalog tableCatalog,
      TableRepository tableRepository) {}
}
