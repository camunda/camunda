/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RdbmsWriterConfigTest {

  @Test
  void shouldDefaultPhysicalTenantToDefault() {
    final var config = new RdbmsWriterConfig.Builder().partitionId(1).build();

    assertThat(config.physicalTenantId())
        .isEqualTo(RdbmsWriterConfig.DEFAULT_PHYSICAL_TENANT_ID)
        .isEqualTo("default");
  }

  @Test
  void shouldUseExplicitPhysicalTenantId() {
    final var config =
        new RdbmsWriterConfig.Builder().partitionId(1).physicalTenantId("tenantA").build();

    assertThat(config.physicalTenantId()).isEqualTo("tenantA");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\t"})
  void shouldRejectBlankPhysicalTenantId(final String blank) {
    final var builder = new RdbmsWriterConfig.Builder().partitionId(1).physicalTenantId(blank);

    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("physicalTenantId must not be null or blank");
  }

  @Test
  void shouldRejectNullPhysicalTenantId() {
    final var builder = new RdbmsWriterConfig.Builder().partitionId(1).physicalTenantId(null);

    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("physicalTenantId must not be null or blank");
  }
}
