/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import io.camunda.application.commons.session.SessionPersistenceAdapter;
import io.camunda.auth.domain.spi.SessionPersistencePort;
import io.camunda.auth.starter.condition.ConditionalOnPersistentWebSessionEnabled;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.db.rdbms.read.service.PersistentWebSessionDbReader;
import io.camunda.db.rdbms.read.service.PersistentWebSessionRdbmsClient;
import io.camunda.db.rdbms.write.service.PersistentWebSessionWriter;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.DocumentBasedWriteClient;
import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.clients.PersistentWebSessionSearchImpl;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.webapps.schema.descriptors.index.PersistentWebSessionIndexDescriptor;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the storage backends (Elasticsearch/OpenSearch and RDBMS) for persistent web sessions.
 * The auth library's {@link io.camunda.auth.starter.CamundaWebSessionAutoConfiguration} handles
 * WebSessionRepository, deletion task, and Spring HTTP Session enablement.
 */
@Configuration
@ConditionalOnPersistentWebSessionEnabled
@ConditionalOnRestGatewayEnabled
public class WebSessionRepositoryConfiguration {

  private final ConnectConfiguration connectConfiguration;

  public WebSessionRepositoryConfiguration(final ConnectConfiguration connectConfiguration) {
    this.connectConfiguration = connectConfiguration;
  }

  @Bean
  @ConditionalOnSecondaryStorageType({
    SecondaryStorageType.elasticsearch,
    SecondaryStorageType.opensearch
  })
  public PersistentWebSessionIndexDescriptor persistentWebSessionIndex() {
    final var indexPrefix = connectConfiguration.getIndexPrefix();
    final var isElasticsearch =
        ConnectionTypes.from(connectConfiguration.getType()).equals(ConnectionTypes.ELASTICSEARCH);
    return new PersistentWebSessionIndexDescriptor(indexPrefix, isElasticsearch);
  }

  @Bean
  @ConditionalOnSecondaryStorageType({
    SecondaryStorageType.elasticsearch,
    SecondaryStorageType.opensearch
  })
  public PersistentWebSessionClient persistentWebSessionClientSearch(
      final DocumentBasedSearchClient searchClient,
      final DocumentBasedWriteClient writeClient,
      final PersistentWebSessionIndexDescriptor descriptor) {
    return new PersistentWebSessionSearchImpl(searchClient, writeClient, descriptor);
  }

  @Bean
  @ConditionalOnSecondaryStorageType(SecondaryStorageType.rdbms)
  public PersistentWebSessionClient persistentWebSessionClientRdbms(
      final PersistentWebSessionDbReader persistentWebSessionDbReader,
      final PersistentWebSessionWriter persistentWebSessionWriter) {
    return new PersistentWebSessionRdbmsClient(
        persistentWebSessionDbReader, persistentWebSessionWriter);
  }

  @Bean
  public SessionPersistencePort sessionPersistencePort(
      final PersistentWebSessionClient persistentWebSessionClient) {
    return new SessionPersistenceAdapter(persistentWebSessionClient);
  }
}
