/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.configuration.UnifiedConfigurationHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

final class PhysicalTenantEnvironmentsTest {

  private static final String INDEX_PREFIX = "data.secondary-storage.elasticsearch.index-prefix";
  private static final String ADMIN_USER = "security.initialization.default-roles.admin.users[0]";

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

  @Test
  void shouldResolveAnAddedTenantAlongsideTheBootTenants() {
    // given
    declare(tenant("tenanta", "prefixa"));

    // when
    final var derived =
        PhysicalTenantEnvironments.withTenants(
            environment, Map.of("tenantb", subtree("prefixb")), Set.of());

    // then
    final var resolver = resolve(derived);
    assertThat(resolver.known()).containsExactlyInAnyOrder("tenanta", "tenantb", "default");
    assertThat(indexPrefixOf(resolver.forPhysicalTenant("tenantb"))).isEqualTo("prefixb");
    assertThat(resolve(environment).known()).doesNotContain("tenantb");
  }

  @Test
  void shouldReplaceTheWholeSubtreeOfAChangedTenant() {
    // given
    declare(
        Map.of(
            "camunda.physical-tenants.tenantc." + INDEX_PREFIX,
            "prefixc",
            "camunda.physical-tenants.tenantc." + ADMIN_USER,
            "admin",
            "camunda.physical-tenants.tenantc.cluster.partition-count",
            5));

    // when
    final var derived =
        PhysicalTenantEnvironments.withTenants(
            environment, Map.of("tenantc", subtree("prefixc2")), Set.of());

    // then
    final var tenant = resolve(derived).forPhysicalTenant("tenantc");
    assertThat(indexPrefixOf(tenant)).isEqualTo("prefixc2");
    assertThat(tenant.getCluster().getPartitionCount()).isEqualTo(1);
  }

  @Test
  void shouldHideAChangedTenantDeclaredThroughEnvironmentVariables() {
    // given
    environment
        .getPropertySources()
        .addFirst(
            new SystemEnvironmentPropertySource(
                "systemEnvironment",
                Map.of(
                    "CAMUNDA_PHYSICALTENANTS_TENANTD_DATA_SECONDARYSTORAGE_ELASTICSEARCH_INDEXPREFIX",
                    "fromenv",
                    "CAMUNDA_PHYSICALTENANTS_TENANTD_SECURITY_INITIALIZATION_DEFAULTROLES_ADMIN_USERS_0",
                    "admin")));

    // when
    final var derived =
        PhysicalTenantEnvironments.withTenants(
            environment, Map.of("tenantd", subtree("fromapi")), Set.of());

    // then
    assertThat(indexPrefixOf(resolve(derived).forPhysicalTenant("tenantd"))).isEqualTo("fromapi");
  }

  @Test
  void shouldDropARemovedTenant() {
    // given
    declare(tenant("tenante", "prefixe"));

    // when
    final var derived =
        PhysicalTenantEnvironments.withTenants(environment, Map.of(), Set.of("tenante"));

    // then
    assertThat(resolve(derived).known()).containsExactly("default");
  }

  @Test
  void shouldKeepEarlierRuntimeChangesWhenDerivingAgain() {
    // given
    final var first =
        PhysicalTenantEnvironments.withTenants(
            environment, Map.of("tenantf", subtree("prefixf")), Set.of());

    // when
    final var second =
        PhysicalTenantEnvironments.withTenants(
            first, Map.of("tenantg", subtree("prefixg")), Set.of());

    // then
    assertThat(resolve(second).known()).containsExactlyInAnyOrder("tenantf", "tenantg", "default");
  }

  @Test
  void shouldApplyTheBootCrossTenantValidationToARuntimeChange() {
    // given
    declare(tenant("tenanth", "shared"));
    final var derived =
        PhysicalTenantEnvironments.withTenants(
            environment, Map.of("tenanti", subtree("shared")), Set.of());

    // when
    final var thrown = catchThrowable(() -> resolve(derived));

    // then
    assertThat(thrown)
        .isInstanceOf(UnifiedConfigurationException.class)
        .hasMessageContaining("tenanth")
        .hasMessageContaining("tenanti");
  }

  @Test
  void shouldResolveTheDisplayNameOfATenant() {
    // given
    final var subtree = new HashMap<>(subtree("prefixj"));
    subtree.put("name", "Tenant J");

    // when
    final var derived =
        PhysicalTenantEnvironments.withTenants(environment, Map.of("tenantj", subtree), Set.of());

    // then
    final var resolver = resolve(derived);
    assertThat(resolver.nameOf("tenantj")).contains("Tenant J");
    assertThat(resolver.nameOf("default")).isEmpty();
  }

  @Test
  void shouldReadTheSubtreeATenantDeclares() {
    // given
    declare(tenant("tenante", "prefixe"));
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "override",
                Map.of("camunda.physical-tenants.tenante." + INDEX_PREFIX, "overridden")));

    // when
    final var subtree = PhysicalTenantEnvironments.subtreeOf(environment, "tenante");

    // then
    assertThat(subtree)
        .containsEntry(INDEX_PREFIX, "overridden")
        .containsEntry(ADMIN_USER, "tenante-admin")
        .doesNotContainKey("camunda.physical-tenants.tenanta." + INDEX_PREFIX);
  }

  private void declare(final Map<String, Object> properties) {
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
  }

  private static Map<String, Object> tenant(final String tenantId, final String indexPrefix) {
    return Map.of(
        "camunda.physical-tenants." + tenantId + "." + INDEX_PREFIX,
        indexPrefix,
        "camunda.physical-tenants." + tenantId + "." + ADMIN_USER,
        tenantId + "-admin");
  }

  private static Map<String, Object> subtree(final String indexPrefix) {
    return Map.of(INDEX_PREFIX, indexPrefix, ADMIN_USER, "admin");
  }

  private static PhysicalTenantResolver resolve(final ConfigurableEnvironment env) {
    final Camunda camunda = new Camunda();
    Binder.get(env).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));
    return PhysicalTenantResolver.of(env, camunda);
  }

  private static String indexPrefixOf(final Camunda camunda) {
    return camunda.getData().getSecondaryStorage().getElasticsearch().getIndexPrefix();
  }
}
