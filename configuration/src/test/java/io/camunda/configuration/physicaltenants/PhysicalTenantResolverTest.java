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

  @Test
  void shouldResolveOneCamundaPerDiscoveredTenant() {
    // given two tenants under the physical-tenants prefix
    setProperties(
        Map.of(
            "camunda.physical-tenants.tenanta.cluster.size", 2,
            "camunda.physical-tenants.tenantb.cluster.size", 4));

    // when
    final Map<String, Camunda> resolved = newResolver().getAll();

    // then both declared tenants are present (alongside the synthesized 'default')
    assertThat(resolved.get("tenanta").getCluster().getSize()).isEqualTo(2);
    assertThat(resolved.get("tenantb").getCluster().getSize()).isEqualTo(4);
  }

  @Test
  void shouldSeedTenantWithRootValuesForNonOverriddenProperties() {
    // given a root value plus a tenant override on a sibling field
    setProperties(
        Map.of(
            "camunda.cluster.size", 5,
            "camunda.cluster.replication-factor", 3,
            "camunda.physical-tenants.tenanta.cluster.partition-count", 7));

    // when
    final Camunda tenantA = newResolver().forPhysicalTenant("tenanta");

    // then non-overridden fields equal the root, overridden fields equal the tenant value
    assertThat(tenantA.getCluster().getSize()).isEqualTo(5);
    assertThat(tenantA.getCluster().getReplicationFactor()).isEqualTo(3);
    assertThat(tenantA.getCluster().getPartitionCount()).isEqualTo(7);
  }

  @Test
  void shouldFallBackToLegacyPropertiesForNonOverriddenProperties() {
    // given a legacy broker property is set, the unified property is not set,
    // and the tenant overrides only an unrelated field
    setProperties(
        Map.of(
            "zeebe.broker.cluster.partitionsCount", 9,
            "camunda.physical-tenants.tenanta.cluster.size", 4));

    // when
    final Camunda tenantA = newResolver().forPhysicalTenant("tenanta");

    // then the legacy fallback applies for the non-overridden property at getter time
    assertThat(tenantA.getCluster().getPartitionCount()).isEqualTo(9);
    assertThat(tenantA.getCluster().getSize()).isEqualTo(4);
  }

  @Test
  void shouldResolveDocumentConfigurationPerTenant() {
    // given root document configuration and a tenant override for one store property
    setProperties(
        Map.of(
            "camunda.document.default-store-id", "aws1",
            "camunda.document.aws.aws1.bucket-name", "root-bucket",
            "camunda.physical-tenants.tenanta.document.aws.aws1.bucket-name", "tenant-bucket"));

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
    // given a tenant other than 'default' is declared with an override
    setProperties(
        Map.of(
            "camunda.cluster.size", 5,
            "camunda.physical-tenants.tenanta.cluster.size", 2));

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
    assertThat(resolver.forPhysicalTenant("tenanta").getCluster().getSize()).isEqualTo(2);
  }

  @Test
  void shouldHonorExplicitlyDeclaredDefaultTenantOverrides() {
    // given the user explicitly declares a 'default' tenant with an override
    setProperties(
        Map.of(
            "camunda.cluster.size", 5,
            "camunda.physical-tenants.default.cluster.size", 9));

    // when
    final PhysicalTenantResolver resolver = newResolver();

    // then the explicit declaration wins and is not clobbered by synthesis
    assertThat(
            resolver
                .forPhysicalTenant(PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID)
                .getCluster()
                .getSize())
        .isEqualTo(9);
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
}
