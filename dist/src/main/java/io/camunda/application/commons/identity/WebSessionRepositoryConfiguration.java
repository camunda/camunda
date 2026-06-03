/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import io.camunda.authentication.config.spi.SessionStoreAdapter;
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
import io.camunda.security.core.port.out.SessionStorePort;
import io.camunda.security.spring.annotation.ConditionalOnPersistentWebSessionEnabled;
import io.camunda.security.spring.session.WebSessionConfiguration;
import io.camunda.security.spring.session.WebSessionRepository;
import io.camunda.webapps.schema.descriptors.index.PersistentWebSessionIndexDescriptor;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import io.camunda.zeebe.util.error.FatalErrorHandler;
import java.lang.Thread.UncaughtExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Host-side wiring for persistent web sessions. The session lifecycle beans (repository, mapper,
 * attribute converter, deletion scheduler) live in CSL's {@link WebSessionConfiguration}, imported
 * here so it activates only when the REST gateway is enabled. This class supplies the host-specific
 * pieces: the secondary-storage backend clients, the {@link SessionStorePort} adapter, and a {@link
 * FatalErrorHandler}-backed uncaught-exception handler that overrides CSL's default so a fatal
 * error in the deletion thread still halts the JVM.
 *
 * <p>Enablement uses {@code camunda.security.session.persistent.enabled}; legacy keys are bridged
 * onto it by {@link PersistentWebSessionPropertiesPostProcessor}.
 */
@Configuration
@ConditionalOnRestGatewayEnabled
@ConditionalOnPersistentWebSessionEnabled
@ImportAutoConfiguration(WebSessionConfiguration.class)
public class WebSessionRepositoryConfiguration {

  private static final Logger WEB_SESSION_DELETION_LOGGER =
      LoggerFactory.getLogger(WebSessionRepository.class);

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
  public SessionStorePort sessionStorePort(
      final PersistentWebSessionClient persistentWebSessionClient) {
    return new SessionStoreAdapter(persistentWebSessionClient);
  }

  @Bean("webSessionDeletionUncaughtExceptionHandler")
  public UncaughtExceptionHandler webSessionDeletionUncaughtExceptionHandler() {
    return FatalErrorHandler.uncaughtExceptionHandler(WEB_SESSION_DELETION_LOGGER);
  }
}
