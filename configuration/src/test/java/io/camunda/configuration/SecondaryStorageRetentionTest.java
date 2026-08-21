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
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
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

  @Test
  void shouldFailWhenUnifiedAndLegacyUsageMetricsPolicyNameMismatch() {
    // given the unified and legacy usage-metrics-policy-name properties set to different values,
    // exercising getUsageMetricsPolicyName()'s SUPPORTED_ONLY_IF_VALUES_MATCH semantics end-to-end
    final ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withUserConfiguration(
                UnifiedConfiguration.class,
                UnifiedConfigurationHelper.class,
                BrokerBasedPropertiesOverride.class,
                SearchEngineRetentionPropertiesOverride.class)
            .withPropertyValues(
                "spring.profiles.active=broker",
                "camunda.data.secondary-storage.type=elasticsearch",
                "camunda.data.secondary-storage.elasticsearch.history.usage-metrics-policy-name=unified-usage-metrics-policy",
                "camunda.database.retention.usageMetricsPolicyName=legacy-usage-metrics-policy");

    // when / then the context fails to start because the two values conflict
    runner.run(
        context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseInstanceOf(UnifiedConfigurationException.class);
        });
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=elasticsearch",
        "camunda.data.secondary-storage.retention.enabled=true",
        "camunda.data.secondary-storage.retention.minimum-age=60d",
        "camunda.data.secondary-storage.retention.usage-metrics-minimum-age=90d",
        "camunda.data.secondary-storage.elasticsearch.history.usage-metrics-policy-name=custom-usage-metrics-policy",
        "camunda.data.secondary-storage.elasticsearch.history.usage-metrics-rollover-interval=2w",
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
          .returns("60d", RetentionConfiguration::getMinimumAge)
          .returns("90d", RetentionConfiguration::getUsageMetricsMinimumAge)
          .returns(
              "custom-usage-metrics-policy", RetentionConfiguration::getUsageMetricsPolicyName);
      assertThat(exporterConfiguration.getHistory().getUsageMetricsRolloverInterval())
          .isEqualTo("2w");
    }

    @Test
    void testCamundaSearchEngineRetentionProperties() {
      assertThat(searchEngineRetentionProperties)
          .returns(true, SearchEngineRetentionProperties::isEnabled)
          .returns("60d", SearchEngineRetentionProperties::getMinimumAge)
          .returns("90d", SearchEngineRetentionProperties::getUsageMetricsMinimumAge)
          .returns(
              "custom-usage-metrics-policy",
              SearchEngineRetentionProperties::getUsageMetricsPolicyName);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=elasticsearch",
        // enabled
        "camunda.data.secondary-storage.retention.enabled=true",
        "camunda.database.retention.enabled=true",
        // minimum-age
        "camunda.data.secondary-storage.retention.minimum-age=60d",
        "camunda.database.retention.minimumAge=60d",
        // usage-metrics-minimum-age
        "camunda.data.secondary-storage.retention.usage-metrics-minimum-age=90d",
        "camunda.database.retention.usageMetricsMinimumAge=90d",
        // usage-metrics-policy-name
        "camunda.data.secondary-storage.elasticsearch.history.usage-metrics-policy-name=custom-usage-metrics-policy",
        "camunda.database.retention.usageMetricsPolicyName=custom-usage-metrics-policy",
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
          .returns("60d", RetentionConfiguration::getMinimumAge)
          .returns("90d", RetentionConfiguration::getUsageMetricsMinimumAge)
          .returns(
              "custom-usage-metrics-policy", RetentionConfiguration::getUsageMetricsPolicyName);
    }

    @Test
    void testCamundaSearchEngineRetentionProperties() {
      assertThat(searchEngineRetentionProperties)
          .returns(true, SearchEngineRetentionProperties::isEnabled)
          .returns("60d", SearchEngineRetentionProperties::getMinimumAge)
          .returns("90d", SearchEngineRetentionProperties::getUsageMetricsMinimumAge)
          .returns(
              "custom-usage-metrics-policy",
              SearchEngineRetentionProperties::getUsageMetricsPolicyName);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=elasticsearch",
        "camunda.database.retention.usageMetricsMinimumAge=180d",
      })
  class WithOnlyDatabaseRetentionLegacySet {
    final BrokerBasedProperties brokerBasedProperties;
    final SearchEngineRetentionProperties searchEngineRetentionProperties;

    WithOnlyDatabaseRetentionLegacySet(
        @Autowired final BrokerBasedProperties brokerBasedProperties,
        @Autowired final SearchEngineRetentionProperties searchEngineRetentionProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
      this.searchEngineRetentionProperties = searchEngineRetentionProperties;
    }

    @Test
    void testCamundaDataSecondaryStorageCamundaExporterProperties() {
      final ExporterCfg camundaExporter = brokerBasedProperties.getCamundaExporter();
      final Map<String, Object> args = camundaExporter.getArgs();

      final ExporterConfiguration exporterConfiguration =
          UnifiedConfigurationHelper.argsToCamundaExporterConfiguration(args);
      assertThat(exporterConfiguration.getHistory().getRetention())
          .returns("180d", RetentionConfiguration::getUsageMetricsMinimumAge);
    }

    @Test
    void testCamundaSearchEngineRetentionProperties() {
      assertThat(searchEngineRetentionProperties)
          .returns("180d", SearchEngineRetentionProperties::getUsageMetricsMinimumAge);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=elasticsearch",
        "zeebe.broker.exporters.camundaexporter.args.history.retention.usageMetricsMinimumAge=180d",
        "zeebe.broker.exporters.camundaexporter.args.history.usageMetricsRolloverInterval=2w",
      })
  class WithOnlyExporterArgsLegacySet {
    final BrokerBasedProperties brokerBasedProperties;
    final SearchEngineRetentionProperties searchEngineRetentionProperties;

    WithOnlyExporterArgsLegacySet(
        @Autowired final BrokerBasedProperties brokerBasedProperties,
        @Autowired final SearchEngineRetentionProperties searchEngineRetentionProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
      this.searchEngineRetentionProperties = searchEngineRetentionProperties;
    }

    @Test
    void testCamundaDataSecondaryStorageCamundaExporterProperties() {
      final ExporterCfg camundaExporter = brokerBasedProperties.getCamundaExporter();
      final Map<String, Object> args = camundaExporter.getArgs();

      final ExporterConfiguration exporterConfiguration =
          UnifiedConfigurationHelper.argsToCamundaExporterConfiguration(args);
      assertThat(exporterConfiguration.getHistory().getRetention())
          .returns("180d", RetentionConfiguration::getUsageMetricsMinimumAge);
      assertThat(exporterConfiguration.getHistory().getUsageMetricsRolloverInterval())
          .isEqualTo("2w");
    }

    @Test
    void testCamundaSearchEngineRetentionProperties() {
      assertThat(searchEngineRetentionProperties)
          .returns("180d", SearchEngineRetentionProperties::getUsageMetricsMinimumAge);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=elasticsearch",
      })
  class WithDefaultValues {
    final BrokerBasedProperties brokerBasedProperties;
    final SearchEngineRetentionProperties searchEngineRetentionProperties;

    WithDefaultValues(
        @Autowired final BrokerBasedProperties brokerBasedProperties,
        @Autowired final SearchEngineRetentionProperties searchEngineRetentionProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
      this.searchEngineRetentionProperties = searchEngineRetentionProperties;
    }

    @Test
    void testCamundaDataSecondaryStorageCamundaExporterProperties() {
      final ExporterCfg camundaExporter = brokerBasedProperties.getCamundaExporter();
      final Map<String, Object> args = camundaExporter.getArgs();

      final ExporterConfiguration exporterConfiguration =
          UnifiedConfigurationHelper.argsToCamundaExporterConfiguration(args);
      assertThat(exporterConfiguration.getHistory().getRetention())
          .returns("730d", RetentionConfiguration::getUsageMetricsMinimumAge)
          .returns(
              "camunda-usage-metrics-retention-policy",
              RetentionConfiguration::getUsageMetricsPolicyName);
      assertThat(exporterConfiguration.getHistory().getUsageMetricsRolloverInterval())
          .isEqualTo("1M");
    }

    @Test
    void testCamundaSearchEngineRetentionProperties() {
      assertThat(searchEngineRetentionProperties)
          .returns("730d", SearchEngineRetentionProperties::getUsageMetricsMinimumAge)
          .returns(
              "camunda-usage-metrics-retention-policy",
              SearchEngineRetentionProperties::getUsageMetricsPolicyName);
    }
  }
}
