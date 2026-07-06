/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.store;

import io.camunda.analytics.dataset.store.DatasetStore;
import io.camunda.analytics.dataset.store.MetadataStore;
import io.camunda.analytics.store.document.DocumentStores;
import io.camunda.analytics.store.rdbms.RdbmsDatasetStore;
import io.camunda.analytics.store.rdbms.metadata.RdbmsMetadataStore;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import java.util.Locale;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;

/**
 * Resolves the serving backend from system properties — the single place a concrete backend is
 * named for the wiring. {@code -Danalytics.database=rdbms|elasticsearch|opensearch} (default {@code
 * rdbms}) picks the family; the RDBMS path reads {@code jdbcUrl}/{@code jdbcUser}, the document
 * path reads {@code analytics.database.url}/{@code .username}/{@code .password}. Everything above
 * the returned {@link AnalyticsBackend} is backend-neutral.
 */
public final class AnalyticsBackends {

  private AnalyticsBackends() {}

  /** Resolves the backend named by {@code -Danalytics.database} (default {@code rdbms}). */
  public static AnalyticsBackend fromSystemProperties() {
    final String backend =
        System.getProperty("analytics.database", "rdbms").toLowerCase(Locale.ROOT);
    return switch (backend) {
      case "rdbms" -> rdbms();
      case "elasticsearch", "opensearch" -> document(backend);
      default ->
          throw new IllegalArgumentException(
              "Unknown analytics.database '"
                  + backend
                  + "' (expected rdbms | elasticsearch | opensearch)");
    };
  }

  private static AnalyticsBackend rdbms() {
    final JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(
        System.getProperty("jdbcUrl", "jdbc:h2:file:./data/analytics-dataset;DB_CLOSE_DELAY=-1"));
    dataSource.setUser(System.getProperty("jdbcUser", "sa"));
    return new RdbmsBackend(dataSource);
  }

  private static AnalyticsBackend document(final String type) {
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

  private record RdbmsBackend(DataSource dataSource) implements AnalyticsBackend {
    @Override
    public MetadataStore metadataStore() {
      return new RdbmsMetadataStore(dataSource);
    }

    @Override
    public DatasetStore newDatasetStore() {
      return new RdbmsDatasetStore(dataSource);
    }
  }

  private record DocumentBackend(ConnectConfiguration configuration) implements AnalyticsBackend {
    @Override
    public MetadataStore metadataStore() {
      return DocumentStores.metadataStore(configuration);
    }

    @Override
    public DatasetStore newDatasetStore() {
      return DocumentStores.datasetStore(configuration);
    }
  }
}
