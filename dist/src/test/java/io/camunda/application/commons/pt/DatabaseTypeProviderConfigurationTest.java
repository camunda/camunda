/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.search.connect.configuration.DatabaseType;
import java.util.function.Function;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DatabaseTypeProviderConfigurationTest {

  private static final String TENANT_A = "tenanta";

  @BeforeAll
  @AfterAll
  static void clearStaticEnvironment() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldResolveElasticsearchType() {
    assertThat(
            databaseTypeProviderFor(SecondaryStorageType.elasticsearch)
                .apply(DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(DatabaseType.ELASTICSEARCH);
  }

  @Test
  void shouldResolveOpensearchType() {
    assertThat(
            databaseTypeProviderFor(SecondaryStorageType.opensearch)
                .apply(DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(DatabaseType.OPENSEARCH);
  }

  @Test
  void shouldResolveRdbmsType() {
    assertThat(
            databaseTypeProviderFor(SecondaryStorageType.rdbms).apply(DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(DatabaseType.RDBMS);
  }

  @Test
  void shouldResolveNoneType() {
    assertThat(databaseTypeProviderFor(SecondaryStorageType.none).apply(DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(DatabaseType.NONE);
  }

  @Test
  void shouldResolveDatabaseTypePerPhysicalTenant() {
    // given the default tenant on elasticsearch and a second, explicitly-declared tenant on
    // opensearch (both document-store types, so the cross-tenant homogeneity validation permits
    // the mix)
    final Camunda camunda = new Camunda();
    camunda.getData().getSecondaryStorage().setType(SecondaryStorageType.elasticsearch);
    final MockEnvironment environment = new MockEnvironment();
    final String base = "camunda.physical-tenants." + TENANT_A + ".";
    environment.setProperty(base + "data.secondary-storage.type", "opensearch");
    environment.setProperty(
        base + "security.initialization.default-roles.admin.users[0]", TENANT_A + "-admin");
    final PhysicalTenantResolver resolver = PhysicalTenantResolver.of(environment, camunda);

    // when
    final var databaseTypeProvider =
        new DatabaseTypeProviderConfiguration().databaseTypeProvider(resolver);

    // then each tenant resolves its own configured type
    assertThat(databaseTypeProvider.apply(DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(DatabaseType.ELASTICSEARCH);
    assertThat(databaseTypeProvider.apply(TENANT_A)).isEqualTo(DatabaseType.OPENSEARCH);
  }

  @Test
  void shouldThrowForUnknownPhysicalTenant() {
    final var databaseTypeProvider = databaseTypeProviderFor(SecondaryStorageType.elasticsearch);

    assertThatThrownBy(() -> databaseTypeProvider.apply("unknown-tenant"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static Function<String, DatabaseType> databaseTypeProviderFor(
      final SecondaryStorageType secondaryStorageType) {
    final Camunda camunda = new Camunda();
    camunda.getData().getSecondaryStorage().setType(secondaryStorageType);
    final PhysicalTenantResolver resolver =
        PhysicalTenantResolver.of(new MockEnvironment(), camunda);
    return new DatabaseTypeProviderConfiguration().databaseTypeProvider(resolver);
  }
}
