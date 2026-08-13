/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.camunda.application.commons.search.SearchEngineSchemaInitializer.TerminalSchemaInitializationException;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride.Converter;
import io.camunda.configuration.beanoverrides.SearchEngineIndexPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineRetentionPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineSchemaManagerPropertiesOverride;
import io.camunda.configuration.beans.SearchEngineIndexProperties;
import io.camunda.configuration.beans.SearchEngineRetentionProperties;
import io.camunda.configuration.beans.SearchEngineSchemaManagerProperties;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.exceptions.IncompatibleVersionException;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Covers the storage-specific half of schema initialization — which failures are terminal, what one
 * attempt does, and how per-tenant state is reported. The settle barrier and the retry loop belong
 * to {@code PerTenantSchemaInitializationTest}.
 *
 * <p>No Elasticsearch is involved: building a search client performs no I/O, and an unreachable one
 * fails within its connect timeout, which is the situation the design exists to survive.
 */
@Timeout(60)
class SearchEngineSchemaInitializerTest {

  private static final String DEFAULT_TENANT = "default";
  private static final String TENANT_B = "tenantb";

  /** Nothing listens here, so an attempt fails on connect instead of hanging. */
  private static final String UNREACHABLE_URL = "http://localhost:1";

  private SearchEngineSchemaInitializer initializer;

  @BeforeAll
  @AfterAll
  static void clearStaticEnvironment() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @AfterEach
  void tearDown() {
    if (initializer != null) {
      // stops the background retry loop, which would otherwise keep hammering the unreachable URL
      // for the rest of the suite
      initializer.destroy();
      initializer = null;
    }
  }

  @Test
  void shouldNotFailStartupWhenStorageIsUnreachable() {
    // given
    initializer = initializerFor(tenants(camunda -> {}, Map.of()));

    // when / then - the node comes up, the tenant is simply not ready
    assertThatCode(() -> initializer.afterPropertiesSet()).doesNotThrowAnyException();
    assertThat(initializer.isInitialized(DEFAULT_TENANT)).isFalse();
    assertThat(initializer.isInitialized()).isFalse();
  }

  @Test
  void shouldNotFailStartupWhenNoTenantCanBeInitialized() {
    // given - two tenants, neither reachable: the case that used to abort the context
    initializer = initializerFor(tenants(camunda -> {}, unreachableTenantB()));

    // when / then
    assertThatCode(() -> initializer.afterPropertiesSet()).doesNotThrowAnyException();
    assertThat(initializer.isInitialized(DEFAULT_TENANT)).isFalse();
    assertThat(initializer.isInitialized(TENANT_B)).isFalse();
  }

  @Test
  void shouldReportReadyImmediatelyWhenSchemaCreationIsDisabled() {
    // given - with createSchema=false there is nothing to apply, so the tenant must neither hold
    // the barrier nor be reported as degraded, even though its storage is unreachable
    initializer =
        initializerFor(tenants(SearchEngineSchemaInitializerTest::noSchemaCreation, Map.of()));

    // when
    initializer.afterPropertiesSet();

    // then
    assertThat(initializer.isInitialized(DEFAULT_TENANT)).isTrue();
    assertThat(initializer.isInitialized()).isTrue();
  }

  @Test
  void shouldReportPerTenantReadinessIndependently() {
    // given - one tenant with nothing to apply, one whose storage is unreachable
    initializer =
        initializerFor(
            tenants(SearchEngineSchemaInitializerTest::noSchemaCreation, unreachableTenantB()));

    // when
    initializer.afterPropertiesSet();

    // then - one tenant's failure does not withhold the other
    assertThat(initializer.isInitialized(DEFAULT_TENANT)).isTrue();
    assertThat(initializer.isInitialized(TENANT_B)).isFalse();
    assertThat(initializer.isInitialized()).isFalse();
  }

  @Test
  void shouldSynthesizeDefaultTenantWhenNoneDeclared() {
    // given
    initializer = initializerFor(tenants(camunda -> {}, Map.of()));

    // when / then
    assertThat(initializer.isInitialized(DEFAULT_TENANT)).isFalse();
    assertThat(initializer.isInitialized()).isFalse();
  }

  @Test
  void shouldTreatAnUnbuildableSearchClientAsTerminal() {
    // given - connect settings that no search client can be built from. Retrying a static
    // misconfiguration like this would never succeed, so the tenant is left degraded instead
    final PhysicalTenantResolver resolver = tenants(camunda -> {}, Map.of());
    final Map<String, SearchEngineConfiguration> configs = configsFor(resolver);
    configs.get(DEFAULT_TENANT).connect().setType(DatabaseType.RDBMS.toString());
    initializer =
        new SearchEngineSchemaInitializer(
            configs, descriptorsFor(resolver), new SimpleMeterRegistry());

    // when / then
    assertThatThrownBy(() -> initializer.initializeTenant(DEFAULT_TENANT))
        .isInstanceOf(TerminalSchemaInitializationException.class)
        .cause()
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldClassifyFailuresThatRetryingCannotRepair() {
    // given / when / then
    assertThat(
            SearchEngineSchemaInitializer.isTerminal(
                new IncompatibleVersionException("schema is from an incompatible version")))
        .isTrue();
    assertThat(
            SearchEngineSchemaInitializer.isTerminal(
                new TerminalSchemaInitializationException("no client", new RuntimeException())))
        .isTrue();
  }

  @Test
  void shouldClassifyStorageFailuresAsRetryable() {
    // given / when / then - an unreachable cluster, a rejected request and a mapping the attempt
    // could not validate are all repairable without restarting the node
    assertThat(SearchEngineSchemaInitializer.isTerminal(new IOException("connection refused")))
        .isFalse();
    assertThat(
            SearchEngineSchemaInitializer.isTerminal(new IllegalStateException("strict mapping")))
        .isFalse();
  }

  @Test
  void shouldNotFailTheTenantWhenTheSearchClientCannotBeClosed() throws Exception {
    // given - the client is only released once the schema has been applied
    final var clientAdapter = mock(ClientAdapter.class);
    doThrow(new IOException("connection reset")).when(clientAdapter).close();

    // when / then - failing here would retry an initialization that already succeeded
    assertThatCode(() -> SearchEngineSchemaInitializer.closeQuietly(clientAdapter, DEFAULT_TENANT))
        .doesNotThrowAnyException();
  }

  private SearchEngineSchemaInitializer initializerFor(final PhysicalTenantResolver resolver) {
    return new SearchEngineSchemaInitializer(
        configsFor(resolver), descriptorsFor(resolver), new SimpleMeterRegistry());
  }

  /**
   * The default tenant is the {@link Camunda} instance itself (the resolver synthesizes it when no
   * {@code default} is declared), while declared tenants bind from the environment — hence the two
   * ways of configuring one.
   */
  private static PhysicalTenantResolver tenants(
      final Consumer<Camunda> defaultTenant, final Map<String, String> declaredTenants) {
    final var environment = new MockEnvironment();
    if (!declaredTenants.isEmpty()) {
      environment
          .getPropertySources()
          .addFirst(new MapPropertySource("test", Map.copyOf(declaredTenants)));
    }
    final Camunda camunda = new Camunda();
    camunda.getData().getSecondaryStorage().setType(SecondaryStorageType.elasticsearch);
    camunda.getData().getSecondaryStorage().getElasticsearch().setUrl(UNREACHABLE_URL);
    defaultTenant.accept(camunda);
    return PhysicalTenantResolver.of(environment, camunda);
  }

  private static void noSchemaCreation(final Camunda camunda) {
    camunda.getData().getSecondaryStorage().getElasticsearch().setCreateSchema(false);
  }

  /** A second tenant on its own index prefix, also pointed at an unreachable cluster. */
  private static Map<String, String> unreachableTenantB() {
    return Map.of(
        "camunda.physical-tenants.tenantb.data.secondary-storage.type",
        "elasticsearch",
        "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.url",
        UNREACHABLE_URL,
        "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.index-prefix",
        TENANT_B,
        // a declared tenant with authorization on would have to bring its own initialization block
        "camunda.physical-tenants.tenantb.security.authorization.enabled",
        "false");
  }

  private static Map<String, IndexDescriptors> descriptorsFor(
      final PhysicalTenantResolver resolver) {
    return new SearchClientReaderConfiguration().physicalTenantScopedIndexDescriptors(resolver);
  }

  private static Map<String, SearchEngineConfiguration> configsFor(
      final PhysicalTenantResolver resolver) {
    return resolver.mapValues(
        tenantCamunda -> {
          final var index = new SearchEngineIndexProperties();
          SearchEngineIndexPropertiesOverride.applyTo(tenantCamunda, index);
          final var retention = new SearchEngineRetentionProperties();
          SearchEngineRetentionPropertiesOverride.applyTo(tenantCamunda, retention);
          final var schemaManager = new SearchEngineSchemaManagerProperties();
          SearchEngineSchemaManagerPropertiesOverride.applyTo(tenantCamunda, schemaManager);
          return SearchEngineConfiguration.of(
              b ->
                  b.connect(new Converter(tenantCamunda).convert())
                      .index(index)
                      .retention(retention)
                      .schemaManager(schemaManager));
        });
  }
}
}
