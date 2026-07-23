/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.AtomixCluster;
import io.camunda.application.commons.secrets.SecretStoreRegistries;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.secretstore.NoopSecretStore;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.NodeInstance;
import io.camunda.zeebe.dynamic.nodeid.Version;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Unit tests for {@link SystemContextLoader}, the per-physical-tenant assembly logic extracted from
 * {@link BrokerModuleConfiguration}. Exercised directly with plain objects and mocks — no Spring
 * context — so the per-tenant configuration translation (default-tenant reuse, override conversion,
 * exporter validation) can be verified in isolation.
 */
final class SystemContextLoaderTest {

  @TempDir private Path workingDirectory;

  @BeforeEach
  void setUpEnvironment() {
    // Guard against static-field contamination from Spring-context tests that leave a plain
    // mock(Environment.class), which is not a ConfigurableEnvironment and crashes Binder.get().
    UnifiedConfigurationHelper.setCustomEnvironment(new MockEnvironment());
  }

  @AfterEach
  void resetEnvironment() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldThrowWhenExporterHasNoClassName() {
    // given - a broker config with an exporter that is missing its className
    final var rootCamunda = new Camunda();
    final var rootBrokerCfg = new BrokerCfg();
    final var invalidExporter = new ExporterCfg();
    invalidExporter.setClassName(null);
    rootBrokerCfg.setExporters(Map.of("oops", invalidExporter));

    final var loader =
        newLoader(
            rootCamunda,
            rootBrokerCfg,
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, rootCamunda));

    // when - then
    assertThatCode(loader::createSystemContext)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith(
            "Expected to find a 'className' configured for the exporter. Couldn't find a valid one for the following exporters ");
  }

  @Test
  void shouldReuseRootBrokerConfigForDefaultTenant() {
    // given
    final var rootCamunda = new Camunda();
    final var rootBrokerCfg = new BrokerCfg();
    final var loader =
        newLoader(
            rootCamunda,
            rootBrokerCfg,
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, rootCamunda));

    // when
    final SystemContext systemContext = loader.createSystemContext();

    // then - the default tenant reuses the fully-initialized root broker config as-is
    assertThat(
            systemContext
                .getPhysicalTenantContext(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .config())
        .isSameAs(rootBrokerCfg);
  }

  @Test
  void shouldConvertConfigForNonDefaultTenant() {
    // given - a non-default tenant with its own Camunda instance (not identical to the root)
    final var rootCamunda = new Camunda();
    final var rootBrokerCfg = new BrokerCfg();
    final var tenantCamunda = new Camunda();
    final var loader =
        newLoader(
            rootCamunda,
            rootBrokerCfg,
            Map.of(
                PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
                rootCamunda,
                "tenanta",
                tenantCamunda));

    // when
    final SystemContext systemContext = loader.createSystemContext();

    // then - the non-default tenant gets its own converted config, distinct from the root
    final var tenantConfig = systemContext.getPhysicalTenantContext("tenanta").config();
    assertThat(tenantConfig).isNotSameAs(rootBrokerCfg);
    assertThat(
            systemContext
                .getPhysicalTenantContext(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .config())
        .isSameAs(rootBrokerCfg);
  }

  @Test
  void shouldThreadSecretStoreRegistryOfEachTenant() {
    // given - a registry configured for one of the two tenants
    final var rootCamunda = new Camunda();
    final var tenantCamunda = new Camunda();
    final var tenantRegistry = new SecretStoreRegistry(Map.of("main", new NoopSecretStore()));
    final var loader =
        newLoader(
                rootCamunda,
                new BrokerCfg(),
                Map.of(
                    PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
                    rootCamunda,
                    "tenanta",
                    tenantCamunda))
            .withSecretStoreRegistries(
                new SecretStoreRegistries(Map.of("tenanta", tenantRegistry)));

    // when
    final SystemContext systemContext = loader.createSystemContext();

    // then - the tenant gets its configured registry, the other tenant an empty one
    assertThat(systemContext.getPhysicalTenantContext("tenanta").secretStoreRegistry())
        .isSameAs(tenantRegistry);
    assertThat(
            systemContext
                .getPhysicalTenantContext(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .secretStoreRegistry()
                .getStores())
        .isEmpty();
  }

  @Test
  void shouldDefaultToEmptySecretStoreRegistryWhenNoneProvided() {
    // given
    final var rootCamunda = new Camunda();
    final var loader =
        newLoader(
            rootCamunda,
            new BrokerCfg(),
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, rootCamunda));

    // when
    final SystemContext systemContext = loader.createSystemContext();

    // then
    assertThat(
            systemContext
                .getPhysicalTenantContext(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .secretStoreRegistry()
                .getStores())
        .isEmpty();
  }

  private SystemContextLoader newLoader(
      final Camunda rootCamunda,
      final BrokerCfg rootBrokerCfg,
      final Map<String, Camunda> physicalTenants) {
    final var nodeIdProvider = mock(NodeIdProvider.class);
    when(nodeIdProvider.currentNodeInstance()).thenReturn(new NodeInstance(0, Version.zero()));
    final var physicalTenantResolver = mock(PhysicalTenantResolver.class);
    when(physicalTenantResolver.getAll()).thenReturn(physicalTenants);

    return new SystemContextLoader()
        .withShutdownTimeout(SystemContext.DEFAULT_SHUTDOWN_TIMEOUT)
        .withRootBrokerCfg(rootBrokerCfg)
        .withRootCamunda(rootCamunda)
        .withActorScheduler(mock(ActorScheduler.class))
        .withCluster(mock(AtomixCluster.class))
        .withBrokerClient(mock(BrokerClient.class))
        .withMeterRegistry(new SimpleMeterRegistry())
        .withPhysicalTenantResolver(physicalTenantResolver)
        .withUserServicesForTenant(tenantId -> mock(UserServices.class))
        .withPasswordEncoder(mock(PasswordEncoder.class))
        .withJwtDecoderFactory(authentication -> mock(JwtDecoder.class))
        .withOidcClaimsProviderFactory(
            authentication -> (OidcClaimsProvider) (jwtClaims, tokenValue) -> jwtClaims)
        .withSearchClientsProxy(mock(SearchClientsProxy.class))
        .withNodeIdProvider(nodeIdProvider)
        .withWorkingDirectory(workingDirectory)
        .withExporterDescriptors(null);
  }
}
