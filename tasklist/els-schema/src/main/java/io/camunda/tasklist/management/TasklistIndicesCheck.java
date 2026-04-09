/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.management;

import io.camunda.search.schema.SearchEngineHealthCheck;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.util.CloseableSilently;
import org.springframework.stereotype.Component;

@Component
public class TasklistIndicesCheck implements CloseableSilently {

  private final SearchEngineHealthCheck delegate;
  private final boolean healthCheckEnabled;

  public TasklistIndicesCheck(
      final SearchEngineConfiguration configuration, final TasklistProperties tasklistProperties) {
    final boolean isElasticSearch = configuration.connect().getTypeEnum().isElasticSearch();
    healthCheckEnabled =
        isElasticSearch
            ? tasklistProperties.getElasticsearch().isHealthCheckEnabled()
            : tasklistProperties.getOpenSearch().isHealthCheckEnabled();
    delegate = new SearchEngineHealthCheck(configuration, healthCheckEnabled);
  }

  public boolean isHealthCheckEnabled() {
    return healthCheckEnabled;
  }

  public boolean isHealthy() {
    return delegate.isHealthy();
  }

  public boolean schemaExists() {
    return delegate.indicesArePresent();
  }

  @Override
  public void close() {
    delegate.close();
  }
}
