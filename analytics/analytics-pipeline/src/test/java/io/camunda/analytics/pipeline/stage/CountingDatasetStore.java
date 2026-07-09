/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.serving.spi.DatasetQueryClient;
import io.camunda.analytics.serving.spi.DatasetSchemaManager;
import io.camunda.analytics.serving.spi.DatasetStore;
import io.camunda.analytics.serving.spi.VersionedDatasetWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link DatasetStore} wrapper that counts the schema-manager {@code ensure}/{@code ensureTable}
 * calls per dataset id, so a reload test can prove DDL runs only for newly-added datasets.
 */
final class CountingDatasetStore implements DatasetStore {

  private final DatasetStore delegate;
  private final Map<Long, Integer> ensures = new HashMap<>();
  private final Map<Long, Integer> tableEnsures = new HashMap<>();

  CountingDatasetStore(final DatasetStore delegate) {
    this.delegate = delegate;
  }

  int ensures(final long cubeId) {
    return ensures.getOrDefault(cubeId, 0);
  }

  int tableEnsures(final long cubeId) {
    return tableEnsures.getOrDefault(cubeId, 0);
  }

  @Override
  public DatasetSchemaManager schemaManager() {
    final DatasetSchemaManager inner = delegate.schemaManager();
    return new DatasetSchemaManager() {
      @Override
      public void ensure(final CompiledDataset dataset) {
        ensures.merge(dataset.cubeId(), 1, Integer::sum);
        inner.ensure(dataset);
      }

      @Override
      public void ensureTable(final CompiledTable table) {
        tableEnsures.merge(table.cubeId(), 1, Integer::sum);
        inner.ensureTable(table);
      }
    };
  }

  @Override
  public VersionedDatasetWriter writer() {
    return delegate.writer();
  }

  @Override
  public DatasetQueryClient queryClient() {
    return delegate.queryClient();
  }

  @Override
  public void close() {
    delegate.close();
  }
}
