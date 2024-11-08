/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.adapters;

import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.cache.process.CachedProcessEntity;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.schema.SearchEngineClient;
import io.camunda.exporter.store.BatchRequest;
import java.io.IOException;

public interface ClientAdapter {

  static ClientAdapter of(final ExporterConfiguration configuration) {
    return switch (ConnectionTypes.from(configuration.getConnect().getType())) {
      case ELASTICSEARCH -> new ElasticsearchAdapter(configuration);
      case OPENSEARCH -> new OpensearchAdapter(configuration);
    };
  }

  SearchEngineClient getSearchEngineClient();

  BatchRequest createBatchRequest();

  CacheLoader<Long, CachedProcessEntity> getProcessCacheLoader(String processIndexName);

  CacheLoader<String, CachedFormEntity> getFormCacheLoader(String formIndexName);

  void close() throws IOException;
}
