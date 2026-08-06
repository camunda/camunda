/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.Secrets;
import io.camunda.configuration.UnifiedConfigurationHelper;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class PhysicalTenantSecretsOverlayTest {

  private MockEnvironment environment;

  @BeforeEach
  void setUp() {
    environment = new MockEnvironment();
    environment.setProperty("camunda.data.secondary-storage.type", "none");
    UnifiedConfigurationHelper.setCustomEnvironment(environment);
  }

  @AfterEach
  void tearDown() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  private PhysicalTenantResolver newResolver() {
    final Camunda camunda = new Camunda();
    Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));
    return PhysicalTenantResolver.of(environment, camunda);
  }

  /**
   * Sets {@code properties} and, for each given tenant, adds the admin user that {@code
   * PhysicalTenantRequiredOverrideValidation} requires.
   */
  private void setProperties(final Map<String, Object> properties, final String... tenantIds) {
    final Map<String, Object> all = new HashMap<>(properties);
    for (final String tenantId : tenantIds) {
      all.put(
          "camunda.physical-tenants."
              + tenantId
              + ".security.initialization.default-roles.admin.users[0]",
          tenantId + "-admin");
    }
    environment.getPropertySources().addFirst(new MapPropertySource("test", all));
  }

  @Test
  void shouldOverlayFileStorePathPerTenant() {
    // given a root file store and a tenant that overrides its path
    setProperties(
        new HashMap<>(
            Map.of(
                "camunda.secrets.stores.file.shared.path", "/root/secrets.txt",
                "camunda.physical-tenants.tenanta.secrets.stores.file.shared.path",
                    "/tenanta/secrets.txt")),
        "tenanta");

    // when the resolver produces a Camunda per tenant
    final PhysicalTenantResolver resolver = newResolver();

    // then tenanta gets its own path and the synthesized default keeps the root path
    assertThat(
            resolver
                .forPhysicalTenant("tenanta")
                .getSecrets()
                .getStores()
                .getFile()
                .get("shared")
                .getPath())
        .isEqualTo("/tenanta/secrets.txt");
    assertThat(
            resolver
                .forPhysicalTenant(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .getSecrets()
                .getStores()
                .getFile()
                .get("shared")
                .getPath())
        .isEqualTo("/root/secrets.txt");
  }

  @Test
  void shouldInheritRootFileStoreWhenTenantHasNoSecretsOverride() {
    // given only a root file store; the tenant overrides no secrets field
    setProperties(
        new HashMap<>(Map.of("camunda.secrets.stores.file.shared.path", "/root/secrets.txt")),
        "tenanta");

    // when the resolver produces a Camunda per tenant
    final PhysicalTenantResolver resolver = newResolver();

    // then tenanta inherits the root file store (non-overridden -> seeded from root bind)
    assertThat(
            resolver
                .forPhysicalTenant("tenanta")
                .getSecrets()
                .getStores()
                .getFile()
                .get("shared")
                .getPath())
        .isEqualTo("/root/secrets.txt");
  }

  @Test
  void shouldExposePerTenantSecretsViaRegistry() {
    // given a root and a tenant override, verify
    // PhysicalTenantResolver.mapValues(Camunda::getSecrets)
    setProperties(
        new HashMap<>(
            Map.of(
                "camunda.secrets.stores.file.shared.path", "/root/secrets.txt",
                "camunda.physical-tenants.tenanta.secrets.stores.file.shared.path",
                    "/tenanta/secrets.txt")),
        "tenanta");

    // when the registry is built from the resolver
    final Map<String, Secrets> registry = newResolver().mapValues(Camunda::getSecrets);

    // then it contains a Secrets per tenant with the correct per-tenant path
    assertThat(registry).containsKeys("tenanta", PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(registry.get("tenanta").getStores().getFile().get("shared").getPath())
        .isEqualTo("/tenanta/secrets.txt");
    assertThat(
            registry
                .get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .getStores()
                .getFile()
                .get("shared")
                .getPath())
        .isEqualTo("/root/secrets.txt");
  }

  @Test
  void shouldOverlayAwsSecretsManagerStorePerTenant() {
    // given a root aws store and a tenant that overrides its path-prefix
    setProperties(
        new HashMap<>(
            Map.of(
                "camunda.secrets.stores.aws.shared.path-prefix", "camunda/",
                "camunda.physical-tenants.tenanta.secrets.stores.aws.shared.path-prefix",
                    "tenanta/")),
        "tenanta");

    // when the resolver produces a Camunda per tenant
    final PhysicalTenantResolver resolver = newResolver();

    // then tenanta gets its own path-prefix and the synthesized default keeps the root one
    assertThat(
            resolver
                .forPhysicalTenant("tenanta")
                .getSecrets()
                .getStores()
                .getAws()
                .get("shared")
                .getPathPrefix())
        .isEqualTo("tenanta/");
    assertThat(
            resolver
                .forPhysicalTenant(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .getSecrets()
                .getStores()
                .getAws()
                .get("shared")
                .getPathPrefix())
        .isEqualTo("camunda/");
  }

  @Test
  void shouldInheritRootAwsSecretsManagerStoreWhenTenantHasNoSecretsOverride() {
    // given only a root aws store; the tenant overrides no secrets field
    setProperties(
        new HashMap<>(Map.of("camunda.secrets.stores.aws.shared.path-prefix", "camunda/")),
        "tenanta");

    // when the resolver produces a Camunda per tenant
    final PhysicalTenantResolver resolver = newResolver();

    // then tenanta inherits the root store (non-overridden -> seeded from root bind)
    assertThat(
            resolver
                .forPhysicalTenant("tenanta")
                .getSecrets()
                .getStores()
                .getAws()
                .get("shared")
                .getPathPrefix())
        .isEqualTo("camunda/");
  }

  @Test
  void shouldOverrideOnlyTheFieldTenantSetsForAwsSecretsManagerStore() {
    // given root sets both region and path-prefix; tenant overrides only region
    setProperties(
        new HashMap<>(
            Map.of(
                "camunda.secrets.stores.aws.shared.region", "eu-west-1",
                "camunda.secrets.stores.aws.shared.path-prefix", "camunda/",
                "camunda.physical-tenants.tenanta.secrets.stores.aws.shared.region", "us-east-1")),
        "tenanta");

    // when the resolver produces a Camunda per tenant
    final PhysicalTenantResolver resolver = newResolver();

    // then tenanta's region is overridden but path-prefix survives the deep merge from root
    final var store =
        resolver.forPhysicalTenant("tenanta").getSecrets().getStores().getAws().get("shared");
    assertThat(store.getRegion()).isEqualTo("us-east-1");
    assertThat(store.getPathPrefix()).isEqualTo("camunda/");
  }

  @Test
  void shouldOverrideBatchEnabledPerTenantWhileInheritingPathPrefix() {
    // given root has batching off and a path-prefix; tenant opts into batching only
    setProperties(
        new HashMap<>(
            Map.of(
                "camunda.secrets.stores.aws.shared.path-prefix", "camunda/",
                "camunda.physical-tenants.tenanta.secrets.stores.aws.shared.batch-enabled",
                    "true")),
        "tenanta");

    // when the resolver produces a Camunda per tenant
    final PhysicalTenantResolver resolver = newResolver();

    // then tenanta's batch-enabled is overridden but path-prefix survives the deep merge
    final var store =
        resolver.forPhysicalTenant("tenanta").getSecrets().getStores().getAws().get("shared");
    assertThat(store.isBatchEnabled()).isTrue();
    assertThat(store.getPathPrefix()).isEqualTo("camunda/");
    assertThat(
            resolver
                .forPhysicalTenant(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .getSecrets()
                .getStores()
                .getAws()
                .get("shared")
                .isBatchEnabled())
        .isFalse();
  }

  @Test
  void shouldExposePerTenantAwsSecretsManagerStoreViaRegistry() {
    // given a root and a tenant override, verify
    // PhysicalTenantResolver.mapValues(Camunda::getSecrets)
    setProperties(
        new HashMap<>(
            Map.of(
                "camunda.secrets.stores.aws.shared.path-prefix", "camunda/",
                "camunda.physical-tenants.tenanta.secrets.stores.aws.shared.path-prefix",
                    "tenanta/")),
        "tenanta");

    // when the registry is built from the resolver
    final Map<String, Secrets> registry = newResolver().mapValues(Camunda::getSecrets);

    // then it contains a Secrets per tenant with the correct per-tenant path-prefix
    assertThat(registry).containsKeys("tenanta", PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(registry.get("tenanta").getStores().getAws().get("shared").getPathPrefix())
        .isEqualTo("tenanta/");
    assertThat(
            registry
                .get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .getStores()
                .getAws()
                .get("shared")
                .getPathPrefix())
        .isEqualTo("camunda/");
  }

  @Test
  void shouldOverlayGcpStorePerTenant() {
    // given a root gcp store and a tenant that overrides its path-prefix
    setProperties(
        new HashMap<>(
            Map.of(
                "camunda.secrets.stores.gcp.shared.project-id", "my-gcp-project",
                "camunda.secrets.stores.gcp.shared.path-prefix", "camunda-",
                "camunda.physical-tenants.tenanta.secrets.stores.gcp.shared.path-prefix",
                    "tenanta-")),
        "tenanta");

    // when the resolver produces a Camunda per tenant
    final PhysicalTenantResolver resolver = newResolver();

    // then tenanta gets its own path-prefix and the synthesized default keeps the root one
    assertThat(
            resolver
                .forPhysicalTenant("tenanta")
                .getSecrets()
                .getStores()
                .getGcp()
                .get("shared")
                .getPathPrefix())
        .isEqualTo("tenanta-");
    assertThat(
            resolver
                .forPhysicalTenant(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .getSecrets()
                .getStores()
                .getGcp()
                .get("shared")
                .getPathPrefix())
        .isEqualTo("camunda-");
  }

  @Test
  void shouldInheritRootGcpStoreWhenTenantHasNoSecretsOverride() {
    // given only a root gcp store; the tenant overrides no secrets field
    setProperties(
        new HashMap<>(Map.of("camunda.secrets.stores.gcp.shared.project-id", "my-gcp-project")),
        "tenanta");

    // when the resolver produces a Camunda per tenant
    final PhysicalTenantResolver resolver = newResolver();

    // then tenanta inherits the root store (non-overridden -> seeded from root bind)
    assertThat(
            resolver
                .forPhysicalTenant("tenanta")
                .getSecrets()
                .getStores()
                .getGcp()
                .get("shared")
                .getProjectId())
        .isEqualTo("my-gcp-project");
  }

  @Test
  void shouldOverrideOnlyTheFieldTenantSetsForGcpStore() {
    // given root sets both project-id and path-prefix; tenant overrides only path-prefix
    setProperties(
        new HashMap<>(
            Map.of(
                "camunda.secrets.stores.gcp.shared.project-id", "my-gcp-project",
                "camunda.secrets.stores.gcp.shared.path-prefix", "camunda-",
                "camunda.physical-tenants.tenanta.secrets.stores.gcp.shared.path-prefix",
                    "tenanta-")),
        "tenanta");

    // when the resolver produces a Camunda per tenant
    final PhysicalTenantResolver resolver = newResolver();

    // then tenanta's path-prefix is overridden but project-id survives the deep merge from root
    final var store =
        resolver.forPhysicalTenant("tenanta").getSecrets().getStores().getGcp().get("shared");
    assertThat(store.getPathPrefix()).isEqualTo("tenanta-");
    assertThat(store.getProjectId()).isEqualTo("my-gcp-project");
  }

  @Test
  void shouldOverlayCacheTtlPerTenant() {
    // given a root cache ttl and a tenant that overrides it
    setProperties(
        new HashMap<>(
            Map.of(
                "camunda.secrets.cache.ttl", "10m",
                "camunda.physical-tenants.tenanta.secrets.cache.ttl", "5m")),
        "tenanta");

    // when the resolver produces a Camunda per tenant
    final PhysicalTenantResolver resolver = newResolver();

    // then tenanta gets its own ttl and the synthesized default keeps the root one
    assertThat(cacheOf(resolver, "tenanta").getTtl()).isEqualTo(Duration.ofMinutes(5));
    assertThat(cacheOf(resolver, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID).getTtl())
        .isEqualTo(Duration.ofMinutes(10));
  }

  @Test
  void shouldOverlayCacheMaxSizePerTenant() {
    // given a root cache max size and a tenant that overrides it
    setProperties(
        new HashMap<>(
            Map.of(
                "camunda.secrets.cache.max-size", "500",
                "camunda.physical-tenants.tenanta.secrets.cache.max-size", "50")),
        "tenanta");

    // when the resolver produces a Camunda per tenant
    final PhysicalTenantResolver resolver = newResolver();

    // then tenanta gets its own max size and the synthesized default keeps the root one
    assertThat(cacheOf(resolver, "tenanta").getMaxSize()).isEqualTo(50);
    assertThat(cacheOf(resolver, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID).getMaxSize())
        .isEqualTo(500);
  }

  @Test
  void shouldInheritRootCacheWhenTenantHasNoCacheOverride() {
    // given only a root cache configuration; the tenant overrides no cache field
    setProperties(
        new HashMap<>(
            Map.of(
                "camunda.secrets.cache.ttl", "10m",
                "camunda.secrets.cache.max-size", "500")),
        "tenanta");

    // when the resolver produces a Camunda per tenant
    final PhysicalTenantResolver resolver = newResolver();

    // then tenanta inherits both root values
    assertThat(cacheOf(resolver, "tenanta").getTtl()).isEqualTo(Duration.ofMinutes(10));
    assertThat(cacheOf(resolver, "tenanta").getMaxSize()).isEqualTo(500);
  }

  @Test
  void shouldOverrideOnlyTheCacheFieldTenantSets() {
    // given root sets both cache fields; tenant overrides only the ttl
    setProperties(
        new HashMap<>(
            Map.of(
                "camunda.secrets.cache.ttl", "10m",
                "camunda.secrets.cache.max-size", "500",
                "camunda.physical-tenants.tenanta.secrets.cache.ttl", "5m")),
        "tenanta");

    // when the resolver produces a Camunda per tenant
    final PhysicalTenantResolver resolver = newResolver();

    // then tenanta's ttl is overridden but the root max size survives: the overlay engine only
    // repairs maps, so this pins that a scalar section is layered onto the root instance rather
    // than rebuilt from the tenant's sub-keys
    assertThat(cacheOf(resolver, "tenanta").getTtl()).isEqualTo(Duration.ofMinutes(5));
    assertThat(cacheOf(resolver, "tenanta").getMaxSize()).isEqualTo(500);
  }

  @Test
  void shouldFallBackToTheDefaultCacheWhenNeitherRootNorTenantConfiguresIt() {
    // given a tenant declared without any camunda.secrets.* property
    setProperties(new HashMap<>(), "tenanta");

    // when the resolver produces a Camunda per tenant
    final PhysicalTenantResolver resolver = newResolver();

    // then both tenants get the documented defaults
    for (final String tenantId :
        new String[] {"tenanta", PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID}) {
      assertThat(cacheOf(resolver, tenantId).getTtl()).isEqualTo(Duration.ofMinutes(20));
      assertThat(cacheOf(resolver, tenantId).getMaxSize()).isEqualTo(1000);
    }
  }

  private Secrets.Cache cacheOf(final PhysicalTenantResolver resolver, final String tenantId) {
    return resolver.forPhysicalTenant(tenantId).getSecrets().getCache();
  }
}
