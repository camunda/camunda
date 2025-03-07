/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import io.camunda.application.commons.conditions.WebappEnabledCondition;
import io.camunda.db.search.engine.config.ConnectConfiguration;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.webapps.backup.DynamicIndicesProvider;
import io.camunda.webapps.profiles.ProfileWebApp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional(WebappEnabledCondition.class)
@ProfileWebApp
public class DynamicIndicesConfiguration {

  private final ConnectConfiguration connectConfiguration;
  private final DocumentBasedSearchClient searchClient;
  private final String optimizeIndexPrefix;

  public DynamicIndicesConfiguration(
      final ConnectConfiguration connectConfiguration,
      final DocumentBasedSearchClient searchClient,
      @Autowired(required = false) final OptimizeIndexNameService optimizeIndexNameService) {
    this.connectConfiguration = connectConfiguration;
    this.searchClient = searchClient;
    optimizeIndexPrefix =
        optimizeIndexNameService != null
            ? optimizeIndexNameService.getIndexPrefix()
            : OptimizeIndexNameService.defaultIndexPrefix;
  }

  @Bean
  public DynamicIndicesProvider dynamicIndicesProvider() {
    return new SearchDynamicIndicesProvider(
        searchClient, connectConfiguration.getTypeEnum().isElasticSearch(), optimizeIndexPrefix);
  }
}
