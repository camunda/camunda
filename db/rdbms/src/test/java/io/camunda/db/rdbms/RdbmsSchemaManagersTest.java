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
import static org.mockito.Mockito.mock;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RdbmsSchemaManagers}, which only chooses each physical tenant's schema
 * manager. Running them is {@code RdbmsSchemaInitializerTest}'s subject in the distribution.
 */
class RdbmsSchemaManagersTest {

  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  @Test
  void shouldBuildNoopManagerWhenAutoDdlDisabled() {
    // given / when - the schema is managed externally, so there is nothing to migrate
    final var managers = RdbmsSchemaManagers.fromConfigs(Map.of(TENANT_A, config(false)), "8.10.0");

    // then
    assertThat(managers.get(TENANT_A)).isInstanceOf(NoopSchemaManager.class);
  }

  @Test
  void shouldBuildLiquibaseManagerWhenAutoDdlEnabled() {
    // given / when
    final var managers = RdbmsSchemaManagers.fromConfigs(Map.of(TENANT_A, config(true)), "8.10.0");

    // then
    assertThat(managers.get(TENANT_A)).isInstanceOf(LiquibaseSchemaManager.class);
  }

  @Test
  void shouldChooseEachTenantsManagerIndependently() {
    // given - two tenants that disagree about who owns their schema
    final var configs = new LinkedHashMap<String, PerTenantSchemaConfig>();
    configs.put(TENANT_A, config(true));
    configs.put(TENANT_B, config(false));

    // when
    final var managers = RdbmsSchemaManagers.fromConfigs(configs, "8.10.0");

    // then
    assertThat(managers.get(TENANT_A)).isInstanceOf(LiquibaseSchemaManager.class);
    assertThat(managers.get(TENANT_B)).isInstanceOf(NoopSchemaManager.class);
  }

  @Test
  void shouldPreserveConfigurationOrder() {
    // given - the caller initializes and logs tenants in this order, so it has to be the
    // operator's, not a hash order that changes between restarts
    final var configs = new LinkedHashMap<String, PerTenantSchemaConfig>();
    configs.put(TENANT_B, config(true));
    configs.put(TENANT_A, config(true));

    // when / then
    assertThat(RdbmsSchemaManagers.fromConfigs(configs, "8.10.0").keySet())
        .containsExactly(TENANT_B, TENANT_A);
  }

  @Test
  void shouldRejectMutationOfTheReturnedManagers() {
    // given - the caller holds this map for the lifetime of the node
    final var managers = RdbmsSchemaManagers.fromConfigs(Map.of(TENANT_A, config(true)), "8.10.0");

    // when / then
    assertThatThrownBy(() -> managers.put(TENANT_B, new NoopSchemaManager()))
        .isInstanceOf(UnsupportedOperationException.class);
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
