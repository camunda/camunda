/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.application.commons.search.SearchClientDatabaseConfiguration.SearchClientProperties;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.rdbms.RdbmsSearchClient;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRestGatewayEnabled
@EnableConfigurationProperties(SearchClientProperties.class)
public class SearchClientDatabaseConfiguration {

  @Bean
  public RdbmsSearchClient rdbmsSearchClient(
      final RdbmsService rdbmsService) {
    return new RdbmsSearchClient(rdbmsService);
  }

  @ConfigurationProperties("camunda.database")
  public static final class SearchClientProperties extends ConnectConfiguration {

  }

}
