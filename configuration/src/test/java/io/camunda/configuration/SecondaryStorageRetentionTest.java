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
import io.camunda.configuration.beanoverrides.SearchEngineRetentionPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.beans.SearchEngineRetentionProperties;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import java.util.Map;
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
  SearchEngineRetentionPropertiesOverride.class
})
public class SecondaryStorageRetentionTest {

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.retention.enabled=true",
        "camunda.data.secondary-storage.retention.minimum-age=60d",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerBasedProperties;
    final SearchEngineRetentionProperties searchEngineRetentionProperties;

    WithOnlyUnifiedConfigSet(
        @Autowired final BrokerBasedProperties brokerBasedProperties,
        @Autowired final SearchEngineRetentionProperties searchEngineRetentionProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
      this.searchEngineRetentionProperties = searchEngineRetentionProperties;
    }

    @Test
    void testCamundaDataSecondaryStorageCamundaExporterProperties() {
      final ExporterCfg camundaExporter = brokerBasedProperties.getCamundaExporter();
      assertThat(camundaExporter).isNotNull();

      final Map<String, Object> args = camundaExporter.getArgs();
      assertThat(args).isNotNull();

      final ExporterConfiguration exporterConfiguration =
          UnifiedConfigurationHelper.argsToCamundaExporterConfiguration(args);
      assertThat(exporterConfiguration.getHistory().getRetention())
          .returns(true, RetentionConfiguration::isEnabled)
          .returns("60d", RetentionConfiguration::getMinimumAge);
    }

    @Test
    void testCamundaSearchEngineRetentionProperties() {
      assertThat(searchEngineRetentionProperties)
          .returns(true, SearchEngineRetentionProperties::isEnabled)
          .returns("60d", SearchEngineRetentionProperties::getMinimumAge);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // enabled
        "camunda.data.secondary-storage.retention.enabled=true",
        "camunda.database.retention.enabled=true",
        // minimum-age
        "camunda.data.secondary-storage.retention.minimum-age=60d",
        "camunda.database.retention.minimumAge=60d",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerBasedProperties;
    final SearchEngineRetentionProperties searchEngineRetentionProperties;

    WithNewAndLegacySet(
        @Autowired final BrokerBasedProperties brokerBasedProperties,
        @Autowired final SearchEngineRetentionProperties searchEngineRetentionProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
      this.searchEngineRetentionProperties = searchEngineRetentionProperties;
    }

    @Test
    void testCamundaDataSecondaryStorageCamundaExporterProperties() {
      final ExporterCfg camundaExporter = brokerBasedProperties.getCamundaExporter();
      assertThat(camundaExporter).isNotNull();

      final Map<String, Object> args = camundaExporter.getArgs();
      assertThat(args).isNotNull();

      final ExporterConfiguration exporterConfiguration =
          UnifiedConfigurationHelper.argsToCamundaExporterConfiguration(args);
      assertThat(exporterConfiguration.getHistory().getRetention())
          .returns(true, RetentionConfiguration::isEnabled)
          .returns("60d", RetentionConfiguration::getMinimumAge);
    }

    @Test
    void testCamundaSearchEngineRetentionProperties() {
      assertThat(searchEngineRetentionProperties)
          .returns(true, SearchEngineRetentionProperties::isEnabled)
          .returns("60d", SearchEngineRetentionProperties::getMinimumAge);
    }
  }
}
