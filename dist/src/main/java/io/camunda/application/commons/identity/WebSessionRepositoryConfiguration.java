/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import io.camunda.authentication.config.spi.PhysicalTenantScopedSessionStorePortProvider;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.clients.tenant.PhysicalTenantScoped;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.security.core.port.out.SessionStorePort;
import io.camunda.security.spring.annotation.ConditionalOnPersistentWebSessionEnabled;
import io.camunda.security.spring.session.WebSessionAttributeConverter;
import io.camunda.security.spring.session.WebSessionConfiguration;
import io.camunda.security.spring.session.WebSessionRepository;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.PersistentWebSessionIndexDescriptor;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import io.camunda.zeebe.util.error.FatalErrorHandler;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Map;
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

  // CSL's default webSessionAttributeConverter is @ConditionalOnMissingBean, so this
  // unconditional host-side bean takes precedence — the migration is always active for
  // this deployment. Omitting @ConditionalOnMissingBean here is intentional: a stale
  // second WebSessionAttributeConverter definition would fail loudly at startup rather
  // than silently winning the race.
  @Bean
  public WebSessionAttributeConverter webSessionAttributeConverter() {
    return new MigratingWebSessionAttributeConverter();
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

  @Bean("persistentWebSessionClientProvider")
  @ConditionalOnSecondaryStorageType({
    SecondaryStorageType.elasticsearch,
    SecondaryStorageType.opensearch
  })
  public PhysicalTenantScoped<PersistentWebSessionClient> persistentWebSessionClientProviderSearch(
      final Map<String, DocumentBasedSearchClient> physicalTenantDocumentSearchClients,
      final Map<String, IndexDescriptors> physicalTenantScopedIndexDescriptors) {
    return PhysicalTenantScopedPersistentWebSessionClientFactory.fromDocumentSearchClients(
        physicalTenantDocumentSearchClients, physicalTenantScopedIndexDescriptors);
  }

  @Bean("persistentWebSessionClientProvider")
  @ConditionalOnSecondaryStorageType(SecondaryStorageType.rdbms)
  public PhysicalTenantScoped<PersistentWebSessionClient> persistentWebSessionClientProviderRdbms(
      final Map<String, RdbmsMapperBundle> rdbmsMapperBundles) {
    return PhysicalTenantScopedPersistentWebSessionClientFactory.fromRdbmsMapperBundles(
        rdbmsMapperBundles);
  }

  /**
   * Per-scope session store lookup consumed by CSL's {@code ScopedSecurityChainRegistrar}
   * (ADR-0029): each scoped chain gets a {@link SessionStorePort} bound to its physical tenant, so
   * a scope's persistent sessions route to that tenant's store structurally at commit time.
   *
   * <p>Declared with the concrete type (not the {@link ScopedSessionStorePortProvider} interface)
   * so {@link #sessionStorePort} can share this exact instance for the default tenant — it is still
   * discoverable by CSL via the interface. Spring still injects it wherever {@code
   * ScopedSessionStorePortProvider} is required.
   */
  @Bean
  public PhysicalTenantScopedSessionStorePortProvider scopedSessionStorePortProvider(
      final PhysicalTenantScoped<PersistentWebSessionClient> sessionClientProvider) {
    return new PhysicalTenantScopedSessionStorePortProvider(sessionClientProvider);
  }

  /**
   * The {@link SessionStorePort} for the default/unprefixed surface's session filter — bound to the
   * default physical tenant's store. It is a single-store adapter like every other; there is no
   * cross-tenant routing/fan-out adapter (ADR-0029).
   *
   * <p>Resolved through {@link #scopedSessionStorePortProvider} (rather than building a separate
   * adapter) so the default surface and the {@code default} scope share the same store instance —
   * required for CSL's expiry-sweep dedup to actually collapse the duplicate (ADR-0029 §4), and for
   * the default surface's webapp and API chains to share one {@code SessionRepositoryFilter}
   * instead of a separately registered global filter (CSL ADR-0031).
   */
  @Bean
  public SessionStorePort sessionStorePort(
      final PhysicalTenantScopedSessionStorePortProvider scopedSessionStorePortProvider) {
    return scopedSessionStorePortProvider.forPhysicalTenant(
        PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Bean("webSessionDeletionUncaughtExceptionHandler")
  public UncaughtExceptionHandler webSessionDeletionUncaughtExceptionHandler() {
    return FatalErrorHandler.uncaughtExceptionHandler(WEB_SESSION_DELETION_LOGGER);
  }
}
