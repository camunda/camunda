/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.application.commons.search.SearchClientDatabaseConfiguration.SearchClientProperties;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.os.clients.OpensearchSearchClient;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRestGatewayEnabled
@ConditionalOnProperty(prefix = "camunda.database", name = "type", havingValue = "opensearch")
public class OpenSearchSearchClientConfiguration {

  @Bean
  public OpensearchSearchClient opensearchSearchClient(final SearchClientProperties configuration) {
    final var connector = new OpensearchConnector(configuration);
    final var elasticsearch = connector.createClient();
    return new OpensearchSearchClient(elasticsearch);
  }
}
