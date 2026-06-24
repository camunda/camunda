/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.GatewayRestPropertiesOverride;
import io.camunda.configuration.beans.GatewayRestProperties;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  GatewayRestPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
public class ApiRestMaxNameFieldLengthTest {

  private static final int DEFAULT_MAX_NAME_FIELD_LENGTH = 32 * 1024;

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=rdbms",
        "camunda.data.secondary-storage.rdbms.max-varchar-field-length=200",
      })
  class WithRdbmsSecondaryStorage {
    final GatewayRestProperties gatewayRestProperties;

    WithRdbmsSecondaryStorage(@Autowired final GatewayRestProperties gatewayRestProperties) {
      this.gatewayRestProperties = gatewayRestProperties;
    }

    @Test
    void shouldSetMaxNameFieldLengthFromMaxVarcharFieldLength() {
      assertThat(gatewayRestProperties.getMaxNameFieldLength()).isEqualTo(200);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=rdbms",
      })
  class WithRdbmsSecondaryStorageAndDefaultMaxVarcharFieldLength {
    final GatewayRestProperties gatewayRestProperties;

    WithRdbmsSecondaryStorageAndDefaultMaxVarcharFieldLength(
        @Autowired final GatewayRestProperties gatewayRestProperties) {
      this.gatewayRestProperties = gatewayRestProperties;
    }

    @Test
    void shouldSetMaxNameFieldLengthToDefaultMaxVarcharFieldLength() {
      assertThat(gatewayRestProperties.getMaxNameFieldLength())
          .isEqualTo(RdbmsWriterConfig.DEFAULT_MAX_VARCHAR_FIELD_LENGTH);
    }
  }

  @Nested
  class WithNonRdbmsSecondaryStorage {
    final GatewayRestProperties gatewayRestProperties;

    WithNonRdbmsSecondaryStorage(@Autowired final GatewayRestProperties gatewayRestProperties) {
      this.gatewayRestProperties = gatewayRestProperties;
    }

    @Test
    void shouldKeepDefaultMaxNameFieldLength() {
      assertThat(gatewayRestProperties.getMaxNameFieldLength())
          .isEqualTo(DEFAULT_MAX_NAME_FIELD_LENGTH);
    }
  }
}
