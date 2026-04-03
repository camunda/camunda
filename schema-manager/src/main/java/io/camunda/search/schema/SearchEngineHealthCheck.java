/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.search.schema.opensearch.OpensearchEngineClient;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.util.CloseableSilently;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared health check logic for search engine indices. Wraps {@link SearchEngineClient} and {@link
 * SchemaManager} to answer two questions: is the search engine cluster healthy, and do all expected
 * indices exist.
 *
 * <p>Used by module-specific Spring health indicators (Operate, Tasklist) that delegate to this
 * class.
 */
public class SearchEngineHealthCheck implements CloseableSilently {

  private static final Logger LOG = LoggerFactory.getLogger(SearchEngineHealthCheck.class);

  private final SearchEngineClient searchEngineClient;
  private final SchemaManager schemaManager;
  private final boolean healthCheckEnabled;

  public SearchEngineHealthCheck(
      final SearchEngineConfiguration configuration, final boolean healthCheckEnabled) {
    final boolean isElasticSearch = configuration.connect().getTypeEnum().isElasticSearch();
    final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    if (isElasticSearch) {
      final var connector = new ElasticsearchConnector(configuration.connect());
      objectMapper = connector.objectMapper();
      searchEngineClient = new ElasticsearchEngineClient(connector.createClient(), objectMapper);
    } else {
      final var connector = new OpensearchConnector(configuration.connect());
      objectMapper = connector.objectMapper();
      searchEngineClient = new OpensearchEngineClient(connector.createClient(), objectMapper);
    }
    final var indexDescriptors =
        new IndexDescriptors(configuration.connect().getIndexPrefix(), isElasticSearch);
    schemaManager =
        new SchemaManager(
            searchEngineClient,
            indexDescriptors.indices(),
            indexDescriptors.templates(),
            configuration,
            objectMapper);
    this.healthCheckEnabled = healthCheckEnabled;
  }

  /** Returns true if the search engine cluster is healthy, or if the health check is disabled. */
  public boolean isHealthy() {
    if (!healthCheckEnabled) {
      LOG.debug("Search engine health check is disabled.");
      return true;
    }
    return searchEngineClient.isHealthy();
  }

  /** Returns true if all expected indices and index templates exist. */
  public boolean indicesArePresent() {
    return schemaManager.isAllIndicesExist();
  }

  @Override
  public void close() {
    searchEngineClient.close();
    schemaManager.close();
  }
}
