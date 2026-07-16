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
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.query.DatasetQueryExecutor;
import io.camunda.analytics.query.DatasetQueryPlanner;
import io.camunda.analytics.query.SnapshotQueryExecutor;
import io.camunda.analytics.query.TableQueryExecutor;
import io.camunda.analytics.serving.catalog.DatasetProvisioningService;
import io.camunda.analytics.serving.catalog.StandardDatasets;
import io.camunda.analytics.serving.catalog.StandardReports;
import io.camunda.analytics.serving.spi.DatasetStore;
import io.camunda.analytics.serving.spi.MetadataStore;
import io.camunda.analytics.store.document.DocumentStores;
import io.camunda.analytics.store.rdbms.RdbmsDatasetStore;
import io.camunda.analytics.store.rdbms.metadata.RdbmsMetadataStore;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the backend-neutral serving stack the dashboard read path runs on: a {@link MetadataStore}
 * (migrated + bootstrapped with the standard dataset specs), a {@link DatasetStore}, a {@link
 * DatasetQueryExecutor}, and the {@link DatasetCatalog} of compiled cubes. The concrete backend is
 * chosen from {@code -Danalytics.database} exactly as the pipeline's {@code AnalyticsBackends} does
 * (replicated here so the webapp does not depend on the pipeline module); everything above the
 * resolved backend is backend-neutral.
 */
@Configuration
public class AnalyticsServingConfig {

  private final Backend backend = Backend.fromSystemProperties();

  /**
   * The metadata plane: migrate the fixed schema, bootstrap the standard dataset specs, then seed
   * the default saved reports (create-if-absent by name, so restarts never duplicate them).
   */
  @Bean(destroyMethod = "close")
  public MetadataStore metadataStore() {
    final MetadataStore store = backend.metadataStore();
    store.migrate();
    StandardDatasets.bootstrap(store);
    StandardReports.seedDefaults(store);
    return store;
  }

  /** The serving store (schema/write/read seams) for the chosen backend. */
  @Bean(destroyMethod = "close")
  public DatasetStore datasetStore() {
    return backend.datasetStore();
  }

  /**
   * The read-path executor: plan, fetch cells, app-merge, finalize. Timed end to end ({@code
   * analytics.query.duration}, tagged by dataset) — the simplest correct seam for "is serving
   * healthy" rather than per-controller-method plumbing.
   */
  @Bean
  public DatasetQueryExecutor datasetQueryExecutor(
      final DatasetStore datasetStore, final MeterRegistry meterRegistry) {
    return new DatasetQueryExecutor(
        new DatasetQueryPlanner(),
        datasetStore.queryClient(),
        new MicrometerQueryMetrics(meterRegistry));
  }

  /** The SNAPSHOT executor (ADR 0010): baseline + sparse range points + carry-forward walk. */
  @Bean
  public SnapshotQueryExecutor snapshotQueryExecutor(final DatasetStore datasetStore) {
    return new SnapshotQueryExecutor(datasetStore.queryClient());
  }

  /**
   * The single admission path for a user-defined dataset: compile + allocate ids, freeze the
   * forward-only event-time activation ({@code now + 30s}, above the 10s reload-check so the stages
   * pick the cube up before its first fact is due — ADR 0005), persist the spec, and provision its
   * serving structure.
   */
  @Bean
  public DatasetProvisioningService datasetProvisioningService(
      final MetadataStore metadataStore, final DatasetStore datasetStore) {
    return new DatasetProvisioningService(
        metadataStore,
        datasetStore.schemaManager(),
        MeterCatalog.withDefaults(),
        System::currentTimeMillis,
        30_000L);
  }

  /** The table read-path executor: validate filters, fetch rows (no reduction). */
  @Bean
  public TableQueryExecutor tableQueryExecutor(final DatasetStore datasetStore) {
    return new TableQueryExecutor(datasetStore.queryClient());
  }

  /**
   * The compiled tables by name. Also ensures each table's serving structure exists so a read never
   * hits a missing table before the pipeline has written anything.
   */
  @Bean
  public TableCatalog tableCatalog(
      final MetadataStore metadataStore, final DatasetStore datasetStore) {
    final Map<String, CompiledTable> byName = new LinkedHashMap<>();
    for (final ActiveTable table : StandardDatasets.loadTables(metadataStore)) {
      final CompiledTable compiled = table.compiled();
      datasetStore.schemaManager().ensureTable(compiled);
      byName.put(compiled.name(), compiled);
    }
    return new TableCatalog(byName);
  }

  /**
   * The compiled cubes by name. Also ensures every cube's serving structure exists so the read path
   * never hits a missing table before the pipeline (or the demo seeder) has written anything.
   */
  @Bean
  public DatasetCatalog datasetCatalog(
      final MetadataStore metadataStore, final DatasetStore datasetStore) {
    final Map<String, CompiledDataset> byName = new LinkedHashMap<>();
    for (final ActiveCube cube : StandardDatasets.loadCubes(metadataStore)) {
      final CompiledDataset compiled = cube.compiled();
      datasetStore.schemaManager().ensure(compiled);
      byName.put(compiled.name(), compiled);
    }
    return new DatasetCatalog(byName);
  }

  /**
   * The backend resolved from {@code -Danalytics.database} (default {@code rdbms}) — the single
   * place a concrete backend is named. Mirrors the pipeline's {@code AnalyticsBackends}.
   */
  private sealed interface Backend {

    MetadataStore metadataStore();

    DatasetStore datasetStore();

    static Backend fromSystemProperties() {
      final String selected =
          System.getProperty("analytics.database", "rdbms").toLowerCase(Locale.ROOT);
      return switch (selected) {
        case "rdbms" -> rdbms();
        case "elasticsearch", "opensearch" -> document(selected);
        default ->
            throw new IllegalArgumentException(
                "Unknown analytics.database '"
                    + selected
                    + "' (expected rdbms | elasticsearch | opensearch)");
      };
    }

    private static Backend rdbms() {
      final JdbcDataSource dataSource = new JdbcDataSource();
      dataSource.setURL(
          System.getProperty("jdbcUrl", "jdbc:h2:file:./data/analytics-dataset;DB_CLOSE_DELAY=-1"));
      dataSource.setUser(System.getProperty("jdbcUser", "sa"));
      return new RdbmsBackend(dataSource);
    }

    private static Backend document(final String type) {
      final ConnectConfiguration configuration = new ConnectConfiguration();
      configuration.setType(type);
      configuration.setUrl(System.getProperty("analytics.database.url", configuration.getUrl()));
      final String username = System.getProperty("analytics.database.username");
      if (username != null) {
        configuration.setUsername(username);
        configuration.setPassword(System.getProperty("analytics.database.password"));
      }
      return new DocumentBackend(configuration);
    }
  }

  private record RdbmsBackend(DataSource dataSource) implements Backend {
    @Override
    public MetadataStore metadataStore() {
      return new RdbmsMetadataStore(dataSource);
    }

    @Override
    public DatasetStore datasetStore() {
      return new RdbmsDatasetStore(dataSource);
    }
  }

  private record DocumentBackend(ConnectConfiguration configuration) implements Backend {
    @Override
    public MetadataStore metadataStore() {
      return DocumentStores.metadataStore(configuration);
    }

    @Override
    public DatasetStore datasetStore() {
      return DocumentStores.datasetStore(configuration);
    }
  }
}
