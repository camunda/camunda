/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import io.camunda.authentication.oauth.ConditionalOnPersistentAuthorizedClientsEnabled;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.DocumentBasedWriteClient;
import io.camunda.search.clients.PersistentOAuth2AuthorizedClientsClient;
import io.camunda.search.clients.PersistentOAuth2AuthorizedClientsClientImpl;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.webapps.schema.descriptors.index.PersistentAuthorizedClientIndexDescriptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnPersistentAuthorizedClientsEnabled
public class PersistedOAuth2AuthorizedClientConfiguration {
  private final ConnectConfiguration connectConfiguration;

  public PersistedOAuth2AuthorizedClientConfiguration(
      final ConnectConfiguration connectConfiguration) {
    this.connectConfiguration = connectConfiguration;
  }

  @Bean
  public PersistentAuthorizedClientIndexDescriptor persistentAuthorizedClientIndex() {
    final var indexPrefix = connectConfiguration.getIndexPrefix();
    final var isElasticsearch =
        ConnectionTypes.from(connectConfiguration.getType()).equals(ConnectionTypes.ELASTICSEARCH);
    return new PersistentAuthorizedClientIndexDescriptor(indexPrefix, isElasticsearch);
  }

  @Bean
  public PersistentOAuth2AuthorizedClientsClient authorizedClientsClient(
      final DocumentBasedSearchClient searchClient,
      final DocumentBasedWriteClient writeClient,
      final PersistentAuthorizedClientIndexDescriptor descriptor) {
    return new PersistentOAuth2AuthorizedClientsClientImpl(searchClient, writeClient, descriptor);
  }
}
