/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.aggregate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the declared datasets the pipeline should aggregate into, from the shared {@code
 * analytics_dataset} table (written by the webapp when a user declares a dataset). Tolerates the
 * table not existing yet (webapp not started) by returning an empty list — the pipeline simply has
 * nothing to fold into until a dataset is declared.
 */
public final class DatasetRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(DatasetRegistry.class);
  private static final String SELECT_DATASETS =
      "SELECT id, window_size_ms FROM analytics_dataset ORDER BY id";

  private final DataSource dataSource;

  public DatasetRegistry(final DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /** The currently declared datasets, or an empty list if none / the registry is not ready. */
  public List<AggregateDataset> datasets() {
    final List<AggregateDataset> datasets = new ArrayList<>();
    try (final Connection connection = dataSource.getConnection();
        final Statement statement = connection.createStatement();
        final ResultSet rs = statement.executeQuery(SELECT_DATASETS)) {
      while (rs.next()) {
        datasets.add(new AggregateDataset(rs.getLong("id"), rs.getLong("window_size_ms")));
      }
    } catch (final SQLException e) {
      LOG.debug("Dataset registry not readable yet (table missing?), treating as empty", e);
      return List.of();
    }
    return datasets;
  }
}
