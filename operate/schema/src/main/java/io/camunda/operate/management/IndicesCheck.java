/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.property.OperateProperties;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.schema.SchemaManager;
import io.camunda.search.schema.SearchEngineClient;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.search.schema.opensearch.OpensearchEngineClient;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.util.CloseableSilently;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IndicesCheck implements CloseableSilently {

  private static final Logger LOGGER = LoggerFactory.getLogger(IndicesCheck.class);

  private final SearchEngineClient searchEngineClient;
  private final SchemaManager schemaManager;
  private final boolean isHealthCheckEnabled;

  public IndicesCheck(
      final SearchEngineConfiguration configuration, final OperateProperties operateProperties) {
    final boolean isElasticSearch = configuration.connect().getTypeEnum().isElasticSearch();
    final ObjectMapper objectMapper;
    if (isElasticSearch) {
      final var connector = new ElasticsearchConnector(configuration.connect());
      objectMapper = connector.objectMapper();
      searchEngineClient = new ElasticsearchEngineClient(connector.createClient(), objectMapper);
      isHealthCheckEnabled = operateProperties.getElasticsearch().isHealthCheckEnabled();
    } else {
      final var connector = new OpensearchConnector(configuration.connect());
      objectMapper = connector.objectMapper();
      searchEngineClient = new OpensearchEngineClient(connector.createClient(), objectMapper);
      isHealthCheckEnabled = operateProperties.getOpensearch().isHealthCheckEnabled();
    }
    final IndexDescriptors indexDescriptors =
        new IndexDescriptors(configuration.connect().getIndexPrefix(), isElasticSearch);
    schemaManager =
        new SchemaManager(
            searchEngineClient,
            indexDescriptors.indices(),
            indexDescriptors.templates(),
            configuration,
            objectMapper);
  }

  public boolean indicesArePresent() {
    return schemaManager.isAllIndicesExist();
  }

  public boolean isHealthy() {
    if (isHealthCheckEnabled) {
      return searchEngineClient.isHealthy();
    } else {
      LOGGER.debug("Database health check is disabled.");
      return true;
    }
  }

  @Override
  public void close() {
    searchEngineClient.close();
  }
}
