/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.utils;

import static io.camunda.spring.utils.PhysicalTenantConfigUtil.MAX_TENANT_ID_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

class PhysicalTenantConfigUtilTest {

  @Test
  void shouldReturnEmptySetWhenPrefixAbsent() {
    // given
    final MockEnvironment environment = new MockEnvironment();

    // when
    final Set<String> tenantIds = PhysicalTenantConfigUtil.discover(environment);

    // then
    assertThat(tenantIds).isEmpty();
  }

  @Test
  void shouldDiscoverTenantIdsAcrossMultiplePropertySources() {
    // given two separate property sources, each declaring a different tenant
    final MockEnvironment environment = new MockEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "first", Map.of("camunda.physical-tenants.tenanta.cluster.size", 4)));
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "second", Map.of("camunda.physical-tenants.tenantb.cluster.size", 8)));

    // when
    final Set<String> tenantIds = PhysicalTenantConfigUtil.discover(environment);

    // then
    assertThat(tenantIds).containsExactlyInAnyOrder("tenanta", "tenantb");
  }

  @Test
  void shouldRejectInvalidTenantIds() {
    // tenant ids must be lowercase alphanumeric — no underscores, no uppercase, no dashes
    // (dashes would make yaml and env-var forms address two different tenants).
    assertThatExceptionOfType(InvalidPhysicalTenantIdException.class)
        .isThrownBy(() -> PhysicalTenantConfigUtil.validateTenantId("Tenant_A"))
        .withMessageContaining("Invalid physical tenant id");
    assertThatExceptionOfType(InvalidPhysicalTenantIdException.class)
        .isThrownBy(() -> PhysicalTenantConfigUtil.validateTenantId("-leading-dash"))
        .withMessageContaining("Invalid physical tenant id");
    assertThatExceptionOfType(InvalidPhysicalTenantIdException.class)
        .isThrownBy(() -> PhysicalTenantConfigUtil.validateTenantId("tenant-a"))
        .withMessageContaining("Invalid physical tenant id");
  }

  @Test
  void shouldRejectNullTenantIdWithoutNpe() {
    // given a null tenant id — must fail with the documented exception, not a NullPointerException
    assertThatExceptionOfType(InvalidPhysicalTenantIdException.class)
        .isThrownBy(() -> PhysicalTenantConfigUtil.validateTenantId(null))
        .withMessageContaining("Invalid physical tenant id");
  }

  @Test
  void shouldRejectTenantIdExceeding64Characters() {
    // given a tenant id that is exactly one character over the limit
    final String tooLong = "a".repeat(MAX_TENANT_ID_LENGTH + 1);

    // when / then
    assertThatExceptionOfType(InvalidPhysicalTenantIdException.class)
        .isThrownBy(() -> PhysicalTenantConfigUtil.validateTenantId(tooLong))
        .withMessageContaining("Invalid physical tenant id")
        .withMessageContaining("must not exceed " + MAX_TENANT_ID_LENGTH)
        .withMessageNotContaining(tooLong);
  }

  @Test
  void shouldAcceptTenantIdOfExactly64Characters() {
    // given a tenant id at exactly the maximum allowed length — must not throw
    final String maxLength = "a".repeat(MAX_TENANT_ID_LENGTH);

    // when / then no exception
    PhysicalTenantConfigUtil.validateTenantId(maxLength);
  }

  @Test
  void shouldRejectDiscoveredTenantIdThatFailsValidation() {
    // given a property source declaring a tenant id over the 64-character limit
    final MockEnvironment environment = new MockEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.physical-tenants."
                        + "a".repeat(MAX_TENANT_ID_LENGTH + 1)
                        + ".cluster.size",
                    4)));

    // when / then
    assertThatExceptionOfType(InvalidPhysicalTenantIdException.class)
        .isThrownBy(() -> PhysicalTenantConfigUtil.discover(environment))
        .withMessageContaining("Invalid physical tenant id");
  }

  @TestFactory
  Stream<DynamicTest> shouldRejectDiscoveredTenantIdWithDashOrCapitalAsWritten() {
    // tenant ids that are only valid once dashes are stripped and letters lowercased
    return Stream.of("tenant-a", "TenantA", "tenant_a")
        .map(
            tenantId ->
                DynamicTest.dynamicTest(
                    tenantId,
                    () -> {
                      // given
                      final MockEnvironment environment =
                          environmentWithTenantProperty(tenantId + ".cluster.size");

                      // when / then the id is rejected as written, not registered as "tenanta"
                      assertThatExceptionOfType(InvalidPhysicalTenantIdException.class)
                          .isThrownBy(() -> PhysicalTenantConfigUtil.discover(environment))
                          .withMessageContaining("Invalid physical tenant id '" + tenantId + "'")
                          .withMessageContaining("Rename it to 'tenanta'");
                    }));
  }

  @Test
  void shouldNameNormalizedIdWhenItIsAValidTenantId() {
    // given a declared id whose normalized form is exactly at the length limit
    final String uniformId = "a".repeat(MAX_TENANT_ID_LENGTH);
    final MockEnvironment environment = environmentWithTenantProperty(uniformId + "-.cluster.size");

    // when / then
    assertThatExceptionOfType(InvalidPhysicalTenantIdException.class)
        .isThrownBy(() -> PhysicalTenantConfigUtil.discover(environment))
        .withMessageContaining("Rename it to '" + uniformId + "'");
  }

  @Test
  void shouldNotNameNormalizedIdWhenItIsNotAValidTenantId() {
    // given a declared id whose normalized form is one character over the length limit
    final MockEnvironment environment =
        environmentWithTenantProperty("a".repeat(MAX_TENANT_ID_LENGTH + 1) + "-.cluster.size");

    // when / then
    assertThatExceptionOfType(InvalidPhysicalTenantIdException.class)
        .isThrownBy(() -> PhysicalTenantConfigUtil.discover(environment))
        .withMessageContaining("Invalid physical tenant id")
        .withMessageNotContaining("Rename it to");
  }

  @Test
  void shouldDiscoverOneTenantDeclaredInYamlAndAsEnvironmentVariable() {
    // given the same tenant declared in yaml form and as an environment variable
    final MockEnvironment environment = environmentWithTenantProperty("tenanta.cluster.size");
    environment
        .getPropertySources()
        .addFirst(
            new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("CAMUNDA_PHYSICALTENANTS_TENANTA_CLUSTER_NAME", "n")));

    // when
    final Set<String> tenantIds = PhysicalTenantConfigUtil.discover(environment);

    // then
    assertThat(tenantIds).containsExactly("tenanta");
  }

  @Test
  void shouldDiscoverTenantIdFromEnvironmentVariable() {
    // given a tenant declared only through an environment variable
    final MockEnvironment environment = new MockEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("CAMUNDA_PHYSICALTENANTS_TENANTA_CLUSTER_SIZE", "4")));

    // when
    final Set<String> tenantIds = PhysicalTenantConfigUtil.discover(environment);

    // then
    assertThat(tenantIds).containsExactly("tenanta");
  }

  @Test
  void shouldInvokeConsumerPerTenantPropertyWithRelativeName() {
    // given a tenant declaring two properties
    final MockEnvironment environment = new MockEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.physical-tenants.tenanta.cluster.size",
                    4,
                    "camunda.physical-tenants.tenanta.cluster.name",
                    "n")));

    // when
    final Map<String, List<String>> relativesByTenant = new LinkedHashMap<>();
    PhysicalTenantConfigUtil.forEachTenantProperty(
        environment,
        (tenantId, relative) ->
            relativesByTenant
                .computeIfAbsent(tenantId, k -> new ArrayList<>())
                .add(relative.toString()));

    // then the consumer receives the id and each property relative to the tenant prefix
    assertThat(relativesByTenant).containsOnlyKeys("tenanta");
    assertThat(relativesByTenant.get("tenanta"))
        .containsExactlyInAnyOrder("cluster.size", "cluster.name");
  }

  @Test
  void shouldPassEmptyRelativeNameWhenKeyIsOnlyTheTenantIdSegment() {
    // given a value bound directly to the tenant-id segment, with no property beneath it
    final MockEnvironment environment = new MockEnvironment();
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("test", Map.of("camunda.physical-tenants.tenanta", "v")));

    // when
    final Map<String, Boolean> relativeEmptyByTenant = new LinkedHashMap<>();
    PhysicalTenantConfigUtil.forEachTenantProperty(
        environment,
        (tenantId, relative) -> relativeEmptyByTenant.put(tenantId, relative.isEmpty()));

    // then the consumer fires once for the id with an empty relative name — the boundary both
    // validators rely on to skip the id-only key
    assertThat(relativeEmptyByTenant).containsOnlyKeys("tenanta");
    assertThat(relativeEmptyByTenant.get("tenanta")).isTrue();
  }

  @Test
  void shouldValidateIdsDuringForEachTenantProperty() {
    // given a tenant id over the length limit
    final MockEnvironment environment = new MockEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.physical-tenants."
                        + "a".repeat(MAX_TENANT_ID_LENGTH + 1)
                        + ".cluster.size",
                    4)));

    // when / then the walk validates each surfaced id and fails fast
    assertThatExceptionOfType(InvalidPhysicalTenantIdException.class)
        .isThrownBy(
            () -> PhysicalTenantConfigUtil.forEachTenantProperty(environment, (id, relative) -> {}))
        .withMessageContaining("Invalid physical tenant id");
  }

  @Test
  void shouldNameMapEntriesInTheFormTheyAreDeclared() {
    // given entries declared dotted with an underscore and in brackets with capital letters or a
    // dot; Spring keys the map by these spellings as written
    final MockEnvironment environment = new MockEnvironment();
    environment.setProperty("camunda.providers.azure_ad.client-id", "underscore");
    environment.setProperty("camunda.providers[AzureAd].client-id", "camel-bracket");
    environment.setProperty("camunda.providers[azure-ad].client-id", "dashed-bracket");
    environment.setProperty("camunda.providers[a.b].client-id", "dotted-bracket");
    final Binder binder = Binder.get(environment);

    // when / then each key reads its own entry, never another spelling of a similar key
    assertThat(bindClientIds(environment, binder, "azure_ad")).containsExactly("underscore");
    assertThat(bindClientIds(environment, binder, "AzureAd")).containsExactly("camel-bracket");
    assertThat(bindClientIds(environment, binder, "azure-ad")).containsExactly("dashed-bracket");
    assertThat(bindClientIds(environment, binder, "a.b")).containsExactly("dotted-bracket");
  }

  @Test
  void shouldNameMapEntryInTheFormsDeclaredUnderRelatedMap() {
    // given an entry the cluster declares in brackets and a tenant overrides in the dotted form
    final MockEnvironment environment = new MockEnvironment();
    environment.setProperty("camunda.providers[AzureAd].client-id", "cluster");
    environment.setProperty("camunda.tenant.providers.AzureAd.client-secret", "tenant");

    // when
    final List<ConfigurationPropertyName> names =
        PhysicalTenantConfigUtil.mapEntryNames(
            environment, "camunda.tenant.providers", "AzureAd", "camunda.providers");

    // then the tenant entry is named in both forms, so the tenant's own declaration is found
    assertThat(names)
        .containsExactlyInAnyOrder(
            ConfigurationPropertyName.adapt("camunda.tenant.providers.AzureAd", '.'),
            ConfigurationPropertyName.of("camunda.tenant.providers").append("[AzureAd]"));
    assertThat(
            names.stream()
                .map(
                    name ->
                        Binder.get(environment)
                            .bind(name.append("client-secret"), Bindable.of(String.class))
                            .orElse(null))
                .filter(secret -> secret != null))
        .containsExactly("tenant");
  }

  @Test
  void shouldReturnNoNameForUndeclaredMapEntry() {
    // given
    final MockEnvironment environment = new MockEnvironment();
    environment.setProperty("camunda.providers.other.client-id", "c");

    // when / then
    assertThat(PhysicalTenantConfigUtil.mapEntryNames(environment, "camunda.providers", "missing"))
        .isEmpty();
  }

  private static List<String> bindClientIds(
      final MockEnvironment environment, final Binder binder, final String key) {
    return PhysicalTenantConfigUtil.mapEntryNames(environment, "camunda.providers", key).stream()
        .map(name -> binder.bind(name.append("client-id"), Bindable.of(String.class)).orElse(null))
        .toList();
  }

  private static MockEnvironment environmentWithTenantProperty(final String relativeName) {
    final MockEnvironment environment = new MockEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource("test", Map.of("camunda.physical-tenants." + relativeName, "4")));
    return environment;
  }
}
