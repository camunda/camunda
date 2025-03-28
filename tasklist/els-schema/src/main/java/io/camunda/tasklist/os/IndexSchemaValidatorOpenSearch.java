/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.os;

import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.schema.SchemaManager;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.opensearch.OpensearchEngineClient;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.IndexSchemaValidator;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.util.CloseableSilently;
import java.util.*;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class IndexSchemaValidatorOpenSearch implements IndexSchemaValidator, CloseableSilently {

  private final OpensearchEngineClient searchEngineClient;
  private final SchemaManager schemaManager;
  private final TasklistProperties tasklistProperties;

  public IndexSchemaValidatorOpenSearch(
      final SearchEngineConfiguration configuration, final TasklistProperties tasklistProperties) {
    final var connector = new OpensearchConnector(configuration.connect());
    final IndexDescriptors indexDescriptors =
        new IndexDescriptors(configuration.connect().getIndexPrefix(), false);
    searchEngineClient =
        new OpensearchEngineClient(connector.createClient(), connector.objectMapper());
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
    return tasklistProperties.getOpenSearch().isHealthCheckEnabled();
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
