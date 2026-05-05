/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RdbmsWriterConfigTest {

  @Test
  void shouldDefaultPhysicalTenantIdToDefault() {
    // when
    final var config = RdbmsWriterConfig.builder().build();

    // then
    assertThat(config.physicalTenantId()).isEqualTo(RdbmsWriterConfig.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(config.physicalTenantId()).isEqualTo("default");
  }

  @Test
  void shouldUseExplicitPhysicalTenantId() {
    // when
    final var config = RdbmsWriterConfig.builder().physicalTenantId("tenant-a").build();

    // then
    assertThat(config.physicalTenantId()).isEqualTo("tenant-a");
  }
}
