/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.es;

import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.schema.SchemaManager;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.IndexSchemaValidator;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.util.CloseableSilently;
import java.util.*;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class IndexSchemaValidatorElasticSearch implements IndexSchemaValidator, CloseableSilently {

  private final ElasticsearchEngineClient searchEngineClient;
  private final SchemaManager schemaManager;
  private final TasklistProperties tasklistProperties;

  public IndexSchemaValidatorElasticSearch(
      final SearchEngineConfiguration configuration, final TasklistProperties tasklistProperties) {
    final var connector = new ElasticsearchConnector(configuration.connect());
    final IndexDescriptors indexDescriptors =
        new IndexDescriptors(configuration.connect().getIndexPrefix(), true);
    searchEngineClient =
        new ElasticsearchEngineClient(connector.createClient(), connector.objectMapper());
    schemaManager =
        new SchemaManager(
            searchEngineClient,
            indexDescriptors.indices(),
            indexDescriptors.templates(),
            configuration,
            connector.objectMapper());
    this.tasklistProperties = tasklistProperties;
  }

  @Override
  public boolean isHealthCheckEnabled() {
    return tasklistProperties.getElasticsearch().isHealthCheckEnabled();
  }

  @Override
  public boolean schemaExists() {
    return schemaManager.isAllIndicesExist();
  }

  @Override
  public void close() {
    searchEngineClient.close();
  }
}
