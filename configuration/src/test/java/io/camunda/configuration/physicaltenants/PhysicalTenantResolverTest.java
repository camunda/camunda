/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.configuration.UnifiedConfigurationHelper;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class PhysicalTenantResolverTest {

  private MockEnvironment environment;

  @BeforeEach
  void setUp() {
    environment = new MockEnvironment();
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

  private void setProperties(final Map<String, Object> properties) {
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
  }

  private static String indexPrefixOf(final Camunda camunda) {
    return camunda.getData().getSecondaryStorage().getElasticsearch().getIndexPrefix();
  }

  @Test
  void shouldResolveOneCamundaPerDiscoveredTenant() {
    // given two tenants under the physical-tenants prefix, each overriding an overridable property
    // (the index prefix) with a distinct value so they also pass cross-tenant isolation
    setProperties(
        Map.of(
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
            "tenanta",
            "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.index-prefix",
            "tenantb"));

    // when
    final Map<String, Camunda> resolved = newResolver().getAll();

    // then both declared tenants are present (alongside the synthesized 'default')
    assertThat(indexPrefixOf(resolved.get("tenanta"))).isEqualTo("tenanta");
    assertThat(indexPrefixOf(resolved.get("tenantb"))).isEqualTo("tenantb");
  }

  @Test
  void shouldSeedTenantWithRootValuesForNonOverriddenProperties() {
    // given root cluster values plus a tenant override on an overridable cluster field
    // (partition-count) and a distinct storage location so it does not collide with 'default'
    setProperties(
        Map.of(
            "camunda.cluster.size", 5,
            "camunda.cluster.replication-factor", 3,
            "camunda.physical-tenants.tenanta.cluster.partition-count", 7,
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta"));

    // when
    final Camunda tenantA = newResolver().forPhysicalTenant("tenanta");

    // then non-overridden cluster fields equal the root, the overridden field equals the tenant
    assertThat(tenantA.getCluster().getSize()).isEqualTo(5);
    assertThat(tenantA.getCluster().getReplicationFactor()).isEqualTo(3);
    assertThat(tenantA.getCluster().getPartitionCount()).isEqualTo(7);
  }

  @Test
  void shouldFallBackToLegacyPropertiesForNonOverriddenProperties() {
    // given a legacy broker property is set, the unified property is not set,
    // and the tenant overrides only an unrelated, overridable field
    setProperties(
        Map.of(
            "zeebe.broker.cluster.partitionsCount",
            9,
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
            "tenanta"));

    // when
    final Camunda tenantA = newResolver().forPhysicalTenant("tenanta");

    // then the legacy fallback applies for the non-overridden property at getter time
    assertThat(tenantA.getCluster().getPartitionCount()).isEqualTo(9);
    assertThat(indexPrefixOf(tenantA)).isEqualTo("tenanta");
  }

  @Test
  void shouldResolveDocumentConfigurationPerTenant() {
    // given root document configuration and a tenant override for one store property
    // (each tenant also gets a distinct index-prefix so they pass cross-tenant isolation)
    setProperties(
        Map.of(
            "camunda.document.default-store-id", "aws1",
            "camunda.document.aws.aws1.bucket-name", "root-bucket",
            "camunda.data.secondary-storage.elasticsearch.index-prefix", "default",
            "camunda.physical-tenants.tenanta.document.aws.aws1.bucket-name", "tenant-bucket",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta"));

    // when
    final PhysicalTenantResolver resolver = newResolver();
    final Camunda defaultTenant =
        resolver.forPhysicalTenant(PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID);
    final Camunda tenantA = resolver.forPhysicalTenant("tenanta");

    // then the default tenant keeps root values and tenant configuration is overlaid
    assertThat(defaultTenant.getDocument().getDefaultStoreId()).isEqualTo("aws1");
    assertThat(defaultTenant.getDocument().getAws().get("aws1").getBucketName())
        .isEqualTo("root-bucket");
    assertThat(tenantA.getDocument().getDefaultStoreId()).isEqualTo("aws1");
    assertThat(tenantA.getDocument().getAws().get("aws1").getBucketName())
        .isEqualTo("tenant-bucket");
  }

  @Test
  void shouldSynthesizeDefaultTenantFromRootWhenNoTenantsAreDeclared() {
    // given only root configuration is set
    setProperties(Map.of("camunda.cluster.size", 5));

    // when
    final PhysicalTenantResolver resolver = newResolver();

    // then a default tenant is synthesized from the root so consumers can always look it up
    assertThat(resolver.getAll())
        .containsOnlyKeys(PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(
            resolver
                .forPhysicalTenant(PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID)
                .getCluster()
                .getSize())
        .isEqualTo(5);
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenPhysicalTenantIdIsNotFound() {
    // when
    final PhysicalTenantResolver resolver = newResolver();

    // then
    assertThatThrownBy(() -> resolver.forPhysicalTenant("missing"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown physical tenant id 'missing'");
  }

  @Test
  void shouldSynthesizeDefaultTenantFromRootWhenOtherTenantsAreDeclared() {
    // given a root (cluster-wide) value plus a tenant declared with an overridable override and a
    // distinct storage location so it does not collide with the synthesized 'default'
    setProperties(
        Map.of(
            "camunda.cluster.size",
            5,
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
            "tenanta"));

    // when
    final PhysicalTenantResolver resolver = newResolver();

    // then a default tenant is added alongside the declared one, carrying the root values
    assertThat(resolver.getAll())
        .containsOnlyKeys("tenanta", PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(
            resolver
                .forPhysicalTenant(PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID)
                .getCluster()
                .getSize())
        .isEqualTo(5);
    // the declared tenant inherits the root cluster size and carries its own storage override
    assertThat(resolver.forPhysicalTenant("tenanta").getCluster().getSize()).isEqualTo(5);
    assertThat(indexPrefixOf(resolver.forPhysicalTenant("tenanta"))).isEqualTo("tenanta");
  }

  @Test
  void shouldHonorExplicitlyDeclaredDefaultTenantOverrides() {
    // given the user explicitly declares a 'default' tenant with an overridable override
    setProperties(
        Map.of(
            "camunda.physical-tenants.default.data.secondary-storage.elasticsearch.index-prefix",
            "custom"));

    // when
    final PhysicalTenantResolver resolver = newResolver();

    // then the explicit declaration wins and is not clobbered by synthesis
    assertThat(
            indexPrefixOf(
                resolver.forPhysicalTenant(PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID)))
        .isEqualTo("custom");
  }

  @Test
  void shouldRejectTenantsResolvingToTheSameSecondaryStorage() {
    // given two tenants explicitly pointing at the same Elasticsearch url with the same (empty)
    // index prefix — they would write into the same database
    setProperties(
        Map.of(
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.url",
                "http://shared:9200",
            "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.url",
                "http://shared:9200"));

    // when / then resolution fails fast at boot
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(this::newResolver)
        .withMessageContaining("secondary-storage location");
  }

  @Test
  void shouldRejectTenantsWithIncompatibleSecondaryStorageTypes() {
    // given one tenant on Elasticsearch and one on RDBMS (incompatible compatibility classes),
    // each with a distinct storage location so the isolation rule passes first
    setProperties(
        Map.of(
            "camunda.physical-tenants.tenanta.data.secondary-storage.type", "elasticsearch",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta",
            "camunda.physical-tenants.tenantb.data.secondary-storage.type", "rdbms",
            "camunda.physical-tenants.tenantb.data.secondary-storage.rdbms.url",
                "jdbc:h2:mem:tenantb"));

    // when / then resolution fails fast at boot
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(this::newResolver)
        .withMessageContaining("compatible secondary-storage type");
  }

  @Test
  void shouldResolveWhenTenantsUseDistinctSecondaryStorage() {
    // given two tenants on the same Elasticsearch cluster but with distinct index prefixes
    // (and the synthesized 'default' keeps the empty prefix) — no collision
    setProperties(
        Map.of(
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                "tenanta",
            "camunda.physical-tenants.tenantb.data.secondary-storage.elasticsearch.index-prefix",
                "tenantb"));

    // when / then resolution succeeds
    final PhysicalTenantResolver resolver = newResolver();
    assertThat(resolver.getAll())
        .containsOnlyKeys("tenanta", "tenantb", PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Test
  void shouldRejectInvalidTenantIds() {
    // tenant ids must be lowercase alphanumeric — no underscores, no uppercase, no dashes
    // (dashes would make yaml and env-var forms address two different tenants).
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantResolver.validateTenantId("Tenant_A"))
        .withMessageContaining("Invalid physical tenant id");
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantResolver.validateTenantId("-leading-dash"))
        .withMessageContaining("Invalid physical tenant id");
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantResolver.validateTenantId("tenant-a"))
        .withMessageContaining("Invalid physical tenant id");
  }

  @Test
  void shouldRejectTenantIdExceeding64Characters() {
    // given a tenant id that is exactly one character over the limit
    final String tooLong = "a".repeat(PhysicalTenantResolver.MAX_TENANT_ID_LENGTH + 1);

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> PhysicalTenantResolver.validateTenantId(tooLong))
        .withMessageContaining("Invalid physical tenant id")
        .withMessageContaining("must not exceed " + PhysicalTenantResolver.MAX_TENANT_ID_LENGTH);
  }

  @Test
  void shouldAcceptTenantIdOfExactly64Characters() {
    // given a tenant id at exactly the maximum allowed length — must not throw
    final String maxLength = "a".repeat(PhysicalTenantResolver.MAX_TENANT_ID_LENGTH);

    // when / then no exception
    PhysicalTenantResolver.validateTenantId(maxLength);
  }

  @Test
  void shouldExposeRootExportersOnEachResolvedTenant() {
    // given root has an exporter; tenanta only overrides storage, not the exporter
    setProperties(
        Map.of(
            "camunda.data.exporters.exp1.class-name",
            "com.Exp1",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
            "tenanta"));

    // when
    final PhysicalTenantResolver resolver = newResolver();

    // then the root exporter is present on the declared tenant and the synthesized default
    assertThat(
            resolver
                .forPhysicalTenant("tenanta")
                .getData()
                .getExporters()
                .get("exp1")
                .getClassName())
        .isEqualTo("com.Exp1");
    assertThat(
            resolver
                .forPhysicalTenant(PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID)
                .getData()
                .getExporters()
                .get("exp1")
                .getClassName())
        .isEqualTo("com.Exp1");
  }

  @Test
  void shouldRejectTenantThatReclassesRootExporter() {
    // given root declares a className for an exporter; a tenant overrides it with a different value
    setProperties(
        Map.of(
            "camunda.data.exporters.exp1.class-name",
            "com.Root",
            "camunda.physical-tenants.tenanta.data.exporters.exp1.class-name",
            "com.Different",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
            "tenanta"));

    // when / then resolution fails fast naming tenant, exporter id, and field
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(this::newResolver)
        .withMessageContaining("tenanta")
        .withMessageContaining("exp1")
        .withMessageContaining("className");
  }
}
