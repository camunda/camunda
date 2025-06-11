/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import io.camunda.authentication.oauth.ConditionalOnPersistentAuthorizedClientsEnabled;
import io.camunda.authentication.oauth.PersistedCamundaOAuth2AuthorizedClientService;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.DocumentBasedWriteClient;
import io.camunda.search.clients.PersistentOAuth2AuthorizedClientsClient;
import io.camunda.search.clients.PersistentOAuth2AuthorizedClientsClientImpl;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.webapps.schema.descriptors.index.PersistentAuthorizedClientIndexDescriptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

@Configuration
@ConditionalOnPersistentAuthorizedClientsEnabled
public class OAuth2AuthorizedClientConfiguration {

  private final GenericConversionService conversionService;
  private final ConnectConfiguration connectConfiguration;

  public OAuth2AuthorizedClientConfiguration(
      final GenericConversionService conversionService,
      final ConnectConfiguration connectConfiguration) {
    this.conversionService = conversionService;
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

  @Bean
  public OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository() {
    return new HttpSessionOAuth2AuthorizedClientRepository();
  }

  @Bean
  public OAuth2AuthorizedClientService oAuth2AuthorizedClientService(
      final ClientRegistrationRepository clientRegistrationRepository,
      final OAuth2AuthorizedClientRepository  oAuth2AuthorizedClientRepository,
      final PersistentOAuth2AuthorizedClientsClient authorizedClientsClient) {
    System.out.println("Creating a client service!");
    return new PersistedCamundaOAuth2AuthorizedClientService(
        clientRegistrationRepository, oAuth2AuthorizedClientRepository, authorizedClientsClient);
  }
}
