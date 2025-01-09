/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import io.camunda.application.commons.conditions.WebappEnabledCondition;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.webapps.profiles.ProfileWebApp;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional(WebappEnabledCondition.class)
@ProfileWebApp
public class DynamicIndicesConfiguration {

  private final ConnectConfiguration connectConfiguration;
  private final DocumentBasedSearchClient searchClient;

  public DynamicIndicesConfiguration(
      final ConnectConfiguration connectConfiguration,
      final DocumentBasedSearchClient searchClient) {
    this.connectConfiguration = connectConfiguration;
    this.searchClient = searchClient;
  }

  /* @Bean
  public DynamicIndicesProvider dynamicIndicesProvider() {
    return new SearchDynamicIndicesProvider(
        searchClient,
        connectConfiguration.getTypeEnum().isElasticSearch(),
        connectConfiguration.getIndexPrefix());
  } */
}
