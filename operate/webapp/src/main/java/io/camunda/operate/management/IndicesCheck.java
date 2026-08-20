/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.management;

import io.camunda.operate.property.OperateProperties;
import io.camunda.search.schema.SearchEngineHealthCheck;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.spring.utils.ConditionalOnRdbmsDisabled;
import io.camunda.zeebe.util.CloseableSilently;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRdbmsDisabled
public class IndicesCheck implements CloseableSilently {

  private final SearchEngineHealthCheck delegate;

  public IndicesCheck(
      final SearchEngineConfiguration configuration, final OperateProperties operateProperties) {
    final boolean isElasticSearch = configuration.connect().getTypeEnum().isElasticSearch();
    final boolean healthCheckEnabled =
        isElasticSearch
            ? operateProperties.getElasticsearch().isHealthCheckEnabled()
            : operateProperties.getOpensearch().isHealthCheckEnabled();
    delegate = new SearchEngineHealthCheck(configuration, healthCheckEnabled);
  }

  public boolean indicesArePresent() {
    return delegate.indicesArePresent();
  }

  public boolean isHealthy() {
    return delegate.isHealthy();
  }

  @Override
  public void close() {
    delegate.close();
  }
}
