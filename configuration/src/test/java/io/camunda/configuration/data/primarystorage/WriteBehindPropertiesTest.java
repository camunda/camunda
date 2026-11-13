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
import io.camunda.zeebe.broker.system.configuration.WriteBehindCfg;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.util.unit.DataSize;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
class WriteBehindPropertiesTest {

  @Nested
  class WithDefaults {
    final WriteBehindCfg writeBehind;

    WithDefaults(@Autowired final BrokerBasedProperties brokerCfg) {
      writeBehind = brokerCfg.getExperimental().getRocksdb().getWriteBehind();
    }

    @Test
    void shouldBeDisabled() {
      assertThat(writeBehind.isEnabled()).isFalse();
    }

    @Test
    void shouldUseTheBrokerDefaults() {
      // the broker defaults are those of the write-behind DB
      assertThat(writeBehind).isEqualTo(new WriteBehindCfg());
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.primary-storage.rocks-db.write-behind.enabled=true",
        "camunda.data.primary-storage.rocks-db.write-behind.flush-delay=5ms",
        "camunda.data.primary-storage.rocks-db.write-behind.flush-threshold=4MB",
        "camunda.data.primary-storage.rocks-db.write-behind.max-pending=8MB",
        "camunda.data.primary-storage.rocks-db.write-behind.cache-size=1MB",
        "camunda.data.primary-storage.rocks-db.write-behind.key-index-max-keys=42",
        "camunda.data.primary-storage.rocks-db.write-behind.key-index.VARIABLES=disable",
        "camunda.data.primary-storage.rocks-db.write-behind.key-index.MESSAGE_SUBSCRIPTION_BY_KEY=KEEP_HIGHEST"
      })
  class WithOverrides {
    final WriteBehindCfg writeBehind;

    WithOverrides(@Autowired final BrokerBasedProperties brokerCfg) {
      writeBehind = brokerCfg.getExperimental().getRocksdb().getWriteBehind();
    }

    @Test
    void shouldSetScalars() {
      assertThat(writeBehind.isEnabled()).isTrue();
      assertThat(writeBehind.getFlushDelay()).isEqualTo(Duration.ofMillis(5));
      assertThat(writeBehind.getFlushThreshold()).isEqualTo(DataSize.ofMegabytes(4));
      assertThat(writeBehind.getMaxPending()).isEqualTo(DataSize.ofMegabytes(8));
      assertThat(writeBehind.getCacheSize()).isEqualTo(DataSize.ofMegabytes(1));
      assertThat(writeBehind.getKeyIndexMaxKeys()).isEqualTo(42);
    }

    @Test
    void shouldOverrideKeyIndexEntriesAndKeepTheOthers() {
      final var expected = new WriteBehindCfg();
      expected.setEnabled(true);
      expected.setFlushDelay(Duration.ofMillis(5));
      expected.setFlushThreshold(DataSize.ofMegabytes(4));
      expected.setMaxPending(DataSize.ofMegabytes(8));
      expected.setCacheSize(DataSize.ofMegabytes(1));
      expected.setKeyIndexMaxKeys(42);
      expected.putKeyIndex("VARIABLES", "DISABLE");
      expected.putKeyIndex("MESSAGE_SUBSCRIPTION_BY_KEY", "KEEP_HIGHEST");
      assertThat(writeBehind).isEqualTo(expected);
    }
  }

  @Nested
  @ContextConfiguration(initializers = WithEnvironmentVariables.Environment.class)
  class WithEnvironmentVariables {
    final WriteBehindCfg writeBehind;

    WithEnvironmentVariables(@Autowired final BrokerBasedProperties brokerCfg) {
      writeBehind = brokerCfg.getExperimental().getRocksdb().getWriteBehind();
    }

    @Test
    void shouldOverrideOneColumnFamilyAndKeepTheOthers() {
      final var expected = new WriteBehindCfg();
      expected.setEnabled(true);
      expected.putKeyIndex("VARIABLES", "DISABLE");
      expected.putKeyIndex("ELEMENT_INSTANCE_KEY", "DISABLE");
      assertThat(writeBehind).isEqualTo(expected);
    }

    static final class Environment
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
      @Override
      public void initialize(final ConfigurableApplicationContext context) {
        context
            .getEnvironment()
            .getPropertySources()
            .addFirst(
                new SystemEnvironmentPropertySource(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    Map.of(
                        "CAMUNDA_DATA_PRIMARYSTORAGE_ROCKSDB_WRITEBEHIND_ENABLED",
                        "true",
                        "CAMUNDA_DATA_PRIMARYSTORAGE_ROCKSDB_WRITEBEHIND_KEYINDEX_VARIABLES",
                        "DISABLE",
                        "CAMUNDA_DATA_PRIMARYSTORAGE_ROCKSDB_WRITEBEHIND_KEYINDEX_ELEMENT_INSTANCE_KEY",
                        "DISABLE")));
      }
    }
  }
}
