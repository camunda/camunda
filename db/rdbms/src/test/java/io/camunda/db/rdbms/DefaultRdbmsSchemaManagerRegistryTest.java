/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** Unit tests for the multi-tenant orchestration in {@link DefaultRdbmsSchemaManagerRegistry}. */
class DefaultRdbmsSchemaManagerRegistryTest {

  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  @Test
  void shouldInitializeEveryTenantSchema() throws Exception {
    // given
    final var managerA = mock(RdbmsSchemaManager.class);
    final var managerB = mock(RdbmsSchemaManager.class);
    final var registry =
        new DefaultRdbmsSchemaManagerRegistry(Map.of(TENANT_A, managerA, TENANT_B, managerB));

    // when
    registry.afterPropertiesSet();

    // then
    verify(managerA).initialize();
    verify(managerB).initialize();
  }

  @Test
  void shouldDelegateIsInitializedToPerTenantManager() {
    // given
    final var managerA = mock(RdbmsSchemaManager.class);
    when(managerA.isInitialized()).thenReturn(true);
    final var managerB = mock(RdbmsSchemaManager.class);
    when(managerB.isInitialized()).thenReturn(false);
    final var registry =
        new DefaultRdbmsSchemaManagerRegistry(Map.of(TENANT_A, managerA, TENANT_B, managerB));

    // then
    assertThat(registry.isInitialized(TENANT_A)).isTrue();
    assertThat(registry.isInitialized(TENANT_B)).isFalse();
  }

  @Test
  void shouldReturnFalseFromIsInitializedForUnknownTenant() {
    // given
    final var registry =
        new DefaultRdbmsSchemaManagerRegistry(Map.of(TENANT_A, mock(RdbmsSchemaManager.class)));

    // then
    assertThat(registry.isInitialized("unknown")).isFalse();
  }

  @Test
  void shouldFailFastWhenAnyTenantInitializationFails() throws Exception {
    // given - A succeeds, B fails. LinkedHashMap preserves iteration order (A then B).
    final var managerA = mock(RdbmsSchemaManager.class);
    when(managerA.isInitialized()).thenReturn(true);
    final var managerB = mock(RdbmsSchemaManager.class);
    doThrow(new RuntimeException("init failed for B")).when(managerB).initialize();

    final var managers = new LinkedHashMap<String, RdbmsSchemaManager>();
    managers.put(TENANT_A, managerA);
    managers.put(TENANT_B, managerB);
    final var registry = new DefaultRdbmsSchemaManagerRegistry(managers);

    // when / then
    assertThatThrownBy(registry::afterPropertiesSet)
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("init failed for B");

    // and - A was initialized, B was not
    assertThat(registry.isInitialized(TENANT_A)).isTrue();
    assertThat(registry.isInitialized(TENANT_B)).isFalse();
  }

  @Test
  void shouldBuildNoopManagerWhenAutoDdlDisabled() {
    // given - auto-ddl=false → NoopSchemaManager → reports initialized even before startup runs
    final var registry =
        DefaultRdbmsSchemaManagerRegistry.fromConfigs(Map.of(TENANT_A, config(false)), "8.10.0");

    // then
    assertThat(registry.isInitialized(TENANT_A)).isTrue();
  }

  @Test
  void shouldBuildLiquibaseManagerWhenAutoDdlEnabled() {
    // given - auto-ddl=true → LiquibaseSchemaManager → not initialized until migration runs
    final var registry =
        DefaultRdbmsSchemaManagerRegistry.fromConfigs(Map.of(TENANT_A, config(true)), "8.10.0");

    // then - no migration has run yet, so the tenant is not initialized
    assertThat(registry.isInitialized(TENANT_A)).isFalse();
  }

  // ---- helpers ----

  private static PerTenantSchemaConfig config(final boolean autoDdl) {
    return new PerTenantSchemaConfig(mock(DataSource.class), h2Properties(), "", autoDdl, null);
  }

  private static VendorDatabaseProperties h2Properties() {
    final var props = new Properties();
    props.put(VendorDatabaseProperties.DATABASE_ID, "h2");
    props.put("variableValue.previewSize", "8191");
    props.put("userCharColumn.size", "256");
    props.put("errorMessage.size", "4000");
    props.put("treePath.size", "8191");
    props.put("disableFkBeforeTruncate", "false");
    return new VendorDatabaseProperties(props);
  }
}
