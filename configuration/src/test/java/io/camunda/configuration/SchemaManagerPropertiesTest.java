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
import io.camunda.configuration.beanoverrides.SearchEngineSchemaManagerPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.beans.SearchEngineSchemaManagerProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@ActiveProfiles({"broker"})
@SpringJUnitConfig({
  UnifiedConfiguration.class,
  UnifiedConfigurationHelper.class,
  BrokerBasedPropertiesOverride.class,
  SearchEngineSchemaManagerPropertiesOverride.class
})
public class SchemaManagerPropertiesTest {

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.system.upgrade.enable-version-check=false",
      })
  class WithUnifiedConfigSet {
    final BrokerBasedProperties brokerBasedProperties;
    final SearchEngineSchemaManagerProperties searchEngineSchemaManagerProperties;

    WithUnifiedConfigSet(
        @Autowired final BrokerBasedProperties brokerBasedProperties,
        @Autowired final SearchEngineSchemaManagerProperties searchEngineSchemaManagerProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
      this.searchEngineSchemaManagerProperties = searchEngineSchemaManagerProperties;
    }

    @Test
    void shouldSetVersionCheckRestrictionEnabled() {
      assertThat(searchEngineSchemaManagerProperties.isVersionCheckRestrictionEnabled()).isFalse();
    }

    @Test
    void shouldSetBrokerVersionCheckRestrictionEnabled() {
      assertThat(brokerBasedProperties.getExperimental().isVersionCheckRestrictionEnabled())
          .isFalse();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.database.schema-manager.versionCheckRestrictionEnabled=false",
      })
  class WithLegacySchemaManagerPropertySet {
    final BrokerBasedProperties brokerBasedProperties;
    final SearchEngineSchemaManagerProperties searchEngineSchemaManagerProperties;

    WithLegacySchemaManagerPropertySet(
        @Autowired final BrokerBasedProperties brokerBasedProperties,
        @Autowired final SearchEngineSchemaManagerProperties searchEngineSchemaManagerProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
      this.searchEngineSchemaManagerProperties = searchEngineSchemaManagerProperties;
    }

    @Test
    void shouldSetVersionCheckRestrictionEnabledFromLegacySchemaManager() {
      assertThat(searchEngineSchemaManagerProperties.isVersionCheckRestrictionEnabled()).isFalse();
    }

    @Test
    void shouldSetBrokerVersionCheckRestrictionEnabledFromLegacySchemaManager() {
      assertThat(brokerBasedProperties.getExperimental().isVersionCheckRestrictionEnabled())
          .isFalse();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // unified config takes precedence
        "camunda.system.upgrade.enable-version-check=false",
        "camunda.database.schema-manager.versionCheckRestrictionEnabled=true",
      })
  class WithUnifiedAndLegacySchemaManagerSet {
    final SearchEngineSchemaManagerProperties searchEngineSchemaManagerProperties;

    WithUnifiedAndLegacySchemaManagerSet(
        @Autowired final SearchEngineSchemaManagerProperties searchEngineSchemaManagerProperties) {
      this.searchEngineSchemaManagerProperties = searchEngineSchemaManagerProperties;
    }

    @Test
    void shouldPreferUnifiedConfigOverLegacySchemaManager() {
      assertThat(searchEngineSchemaManagerProperties.isVersionCheckRestrictionEnabled()).isFalse();
    }
  }
}
