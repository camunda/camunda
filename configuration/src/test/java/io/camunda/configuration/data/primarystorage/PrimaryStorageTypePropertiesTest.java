/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.data.primarystorage;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.system.configuration.DataCfg.PrimaryStorageType;
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
public class PrimaryStorageTypePropertiesTest {

  @Nested
  class WithNothingSet {
    final BrokerBasedProperties brokerCfg;

    WithNothingSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldKeepTheStateInRocksDb() {
      assertThat(brokerCfg.getData().getPrimaryStorageType()).isEqualTo(PrimaryStorageType.ROCKSDB);
    }
  }

  @Nested
  @TestPropertySource(properties = "camunda.data.primary-storage.type=rdbms")
  class WithUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetType() {
      assertThat(brokerCfg.getData().getPrimaryStorageType()).isEqualTo(PrimaryStorageType.RDBMS);
    }
  }
}
