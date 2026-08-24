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

import io.camunda.application.commons.pt.EveryTenantTerminallyFailedException;
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
import io.camunda.search.schema.SearchEngineHealthCheckPermissionException;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.exceptions.IncompatibleVersionException;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
 * attempt does, how per-tenant state is reported, and whether this node holds startup at the gate.
 * The gate rule itself and the retry loop belong to {@code PerTenantSchemaInitializationTest}.
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
  void shouldNotHoldStartupWithoutAnHttpGateway() {
    // given - a node whose storage is unreachable and which serves no HTTP surface: its exporter
    // retries per partition, and nothing about its startup benefits from waiting
    initializer = backgroundInitializerFor(tenants(camunda -> {}, Map.of()));

    // when / then - startup carries on, the tenant is simply not ready
    assertThatCode(() -> initializer.afterPropertiesSet()).doesNotThrowAnyException();
    assertThat(initializer.isInitialized(DEFAULT_TENANT)).isFalse();
    assertThat(initializer.isInitialized()).isFalse();
  }

  @Test
  void shouldNotFailStartupWhenNoTenantCanBeInitialized() {
    // given - two tenants, neither reachable: the case that used to abort the context
    initializer = backgroundInitializerFor(tenants(camunda -> {}, unreachableTenantB()));

    // when / then
    assertThatCode(() -> initializer.afterPropertiesSet()).doesNotThrowAnyException();
    assertThat(initializer.isInitialized(DEFAULT_TENANT)).isFalse();
    assertThat(initializer.isInitialized(TENANT_B)).isFalse();
  }

  @Test
  void shouldHoldStartupWithAnHttpGatewayUntilATenantIsServiceable() throws Exception {
    // given - a node that serves HTTP and whose only tenant's storage is unreachable
    initializer = gatewayInitializerFor(tenants(camunda -> {}, Map.of()));
    final var returned = new CountDownLatch(1);
    Thread.ofPlatform()
        .name("test-startup")
        .start(
            () -> {
              initializer.afterPropertiesSet();
              returned.countDown();
            });

    // when / then - the port stays shut rather than opening on the first connect timeout and
    // serving the webapp 503 from every endpoint that needs secondary storage
    assertThat(returned.await(500, TimeUnit.MILLISECONDS)).isFalse();

    // and - shutdown still releases it
    initializer.destroy();
    assertThat(returned.await(10, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void shouldReportReadyImmediatelyWhenSchemaCreationIsDisabled() {
    // given - with createSchema=false there is nothing to apply, so the tenant is serviceable at
    // once and opens the gate, even though its storage is unreachable
    initializer =
        gatewayInitializerFor(
            tenants(SearchEngineSchemaInitializerTest::noSchemaCreation, Map.of()));

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
        gatewayInitializerFor(
            tenants(SearchEngineSchemaInitializerTest::noSchemaCreation, unreachableTenantB()));

    // when - the gate opens on the serviceable tenant
    initializer.afterPropertiesSet();

    // then - one tenant's failure does not withhold the other
    assertThat(initializer.isInitialized(DEFAULT_TENANT)).isTrue();
    assertThat(initializer.isInitialized(TENANT_B)).isFalse();
    assertThat(initializer.isInitialized()).isFalse();
  }

  @Test
  void shouldSynthesizeDefaultTenantWhenNoneDeclared() {
    // given
    initializer = backgroundInitializerFor(tenants(camunda -> {}, Map.of()));

    // when / then
    assertThat(initializer.isInitialized(DEFAULT_TENANT)).isFalse();
    assertThat(initializer.isInitialized()).isFalse();
  }

  @Test
  void shouldTreatAnUnbuildableSearchClientAsTerminal() {
    // given - connect settings that no search client can be built from. Retrying a static
    // misconfiguration like this would never succeed, so the tenant is left degraded instead
    initializer = terminallyMisconfiguredInitializer(false);

    // when / then
    assertThatThrownBy(() -> initializer.initializeTenant(DEFAULT_TENANT))
        .isInstanceOf(TerminalSchemaInitializationException.class)
        .cause()
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldAbortStartupWithAnHttpGatewayWhenEveryTenantFailsTerminally() {
    // given - a node whose only tenant is misconfigured in a way retrying cannot repair
    initializer = terminallyMisconfiguredInitializer(true);

    // when / then - it takes itself down rather than coming up able to serve nobody, never
    // becoming ready, and exporting into the schema the classification just refused
    assertThatThrownBy(() -> initializer.afterPropertiesSet())
        .isInstanceOf(EveryTenantTerminallyFailedException.class)
        .hasMessageContaining(DEFAULT_TENANT);
  }

  @Test
  void shouldNotAbortStartupWithoutAnHttpGatewayWhenEveryTenantFailsTerminally() {
    // given - the same misconfiguration on a node that waits at no gate
    initializer = terminallyMisconfiguredInitializer(false);

    // when / then - the abort belongs to the gate, so a node without one stays up with its tenant
    // degraded and its exporter retrying per partition, as it does today
    assertThatCode(() -> initializer.afterPropertiesSet()).doesNotThrowAnyException();
    assertThat(initializer.isInitialized(DEFAULT_TENANT)).isFalse();
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
    assertThat(
            SearchEngineSchemaInitializer.isTerminal(
                new SearchEngineHealthCheckPermissionException(
                    "missing 'monitor' privilege", new RuntimeException())))
        .as("a missing cluster:monitor privilege will not be granted by retrying")
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
    assertThat(
            SearchEngineSchemaInitializer.isTerminal(
                new SearchEngineSchemaInitializer.SchemaNotReadyException(
                    "cluster health check failed")))
        .as("a cluster still red/yellow right after schema creation may recover shortly after")
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

  /** A node with an HTTP gateway: it holds startup at the gate. */
  private SearchEngineSchemaInitializer gatewayInitializerFor(
      final PhysicalTenantResolver resolver) {
    return initializerFor(resolver, true);
  }

  /** A node without one: it starts the tasks and carries on. */
  private SearchEngineSchemaInitializer backgroundInitializerFor(
      final PhysicalTenantResolver resolver) {
    return initializerFor(resolver, false);
  }

  private SearchEngineSchemaInitializer initializerFor(
      final PhysicalTenantResolver resolver, final boolean holdsStartup) {
    return new SearchEngineSchemaInitializer(
        configsFor(resolver), descriptorsFor(resolver), new SimpleMeterRegistry(), holdsStartup);
  }

  /**
   * A single tenant whose connect settings no search client can be built from, so it is terminal
   * from its first attempt and stays that way — the shortest route to "every tenant terminal"
   * without a container.
   */
  private SearchEngineSchemaInitializer terminallyMisconfiguredInitializer(
      final boolean holdsStartup) {
    final PhysicalTenantResolver resolver = tenants(camunda -> {}, Map.of());
    final Map<String, SearchEngineConfiguration> configs = configsFor(resolver);
    configs.get(DEFAULT_TENANT).connect().setType(DatabaseType.RDBMS.toString());
    return new SearchEngineSchemaInitializer(
        configs, descriptorsFor(resolver), new SimpleMeterRegistry(), holdsStartup);
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
        "camunda.physical-tenants.tenantb.security.initialization.default-roles.admin.users[0]",
        "tenantb-admin");
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
