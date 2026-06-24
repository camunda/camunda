/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.backup.azure.SasTokenType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
public class DataBackupAzureSasTokenTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.backup.azure.sas-token.type=delegation",
        "camunda.data.backup.azure.sas-token.value=valueNew",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetType() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getSasToken().type())
          .isEqualTo(SasTokenType.DELEGATION);
    }

    @Test
    void shouldSetValue() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getSasToken().value())
          .isEqualTo("valueNew");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.data.backup.azure.sasToken.type=delegation",
        "zeebe.broker.data.backup.azure.sasToken.value=valueLegacy",
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetType() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getSasToken().type())
          .isEqualTo(SasTokenType.DELEGATION);
    }

    @Test
    void shouldSetValue() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getSasToken().value())
          .isEqualTo("valueLegacy");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.data.backup.azure.sas-token.type=delegation",
        "camunda.data.backup.azure.sas-token.value=valueNew",
        // legacy
        "zeebe.broker.data.backup.azure.sasToken.type=service",
        "zeebe.broker.data.backup.azure.sasToken.value=valueLegacy",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetTypeFromNew() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getSasToken().type())
          .isEqualTo(SasTokenType.DELEGATION);
    }

    @Test
    void shouldSetValueFromNew() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getSasToken().value())
          .isEqualTo("valueNew");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.data.primary-storage.backup.azure.sas-token.type=delegation",
        "camunda.data.primary-storage.backup.azure.sas-token.value=valueNew",
        // old unified
        "camunda.data.backup.azure.sas-token.type=delegation",
        "camunda.data.backup.azure.sas-token.value=valueOld",
      })
  class WithNewAndLegacyUnifiedSet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacyUnifiedSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetTypeFromNew() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getSasToken().type())
          .isEqualTo(SasTokenType.DELEGATION);
    }

    @Test
    void shouldSetValueFromNew() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getSasToken().value())
          .isEqualTo("valueNew");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.data.primary-storage.backup.azure.sas-token.type=delegation",
        "camunda.data.primary-storage.backup.azure.sas-token.value=valueNew",
        // old unified
        "camunda.data.backup.azure.sas-token.type=delegation",
        "camunda.data.backup.azure.sas-token.value=valueOld",
        // legacy
        "camunda.data.backup.azure.sas-token.type=service",
        "camunda.data.backup.azure.sas-token.value=valueLegacy",
      })
  class WithNewAndBothLegacyUnifiedSet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndBothLegacyUnifiedSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetTypeFromNew() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getSasToken().type())
          .isEqualTo(SasTokenType.DELEGATION);
    }

    @Test
    void shouldSetValueFromNew() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getSasToken().value())
          .isEqualTo("valueNew");
    }
  }
}
