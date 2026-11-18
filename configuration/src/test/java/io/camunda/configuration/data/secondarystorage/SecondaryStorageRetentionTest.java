/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.data.secondarystorage;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineRetentionPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.beans.SearchEngineRetentionProperties;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.search.schema.config.DeleterConfiguration;
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

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=elasticsearch",
        "camunda.data.secondary-storage.retention.deleter.document-batch-size=1000",
        "camunda.data.secondary-storage.retention.deleter.entity-batch-size=200",
        "camunda.data.secondary-storage.retention.deleter.max-retry-attempts=5",
        "camunda.data.secondary-storage.retention.deleter.retry-delay=3s",
        "camunda.data.secondary-storage.retention.deleter.delay-between-runs=5s",
        "camunda.data.secondary-storage.retention.deleter.max-delay-between-runs=2m",
      })
  class WithDeleterSet {
    final BrokerBasedProperties brokerBasedProperties;

    WithDeleterSet(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldApplyDeleterToCamundaExporter() {
      // given
      final Map<String, Object> args = brokerBasedProperties.getCamundaExporter().getArgs();

      // when
      final ExporterConfiguration exporterConfiguration =
          UnifiedConfigurationHelper.argsToCamundaExporterConfiguration(args);

      // then
      assertThat(exporterConfiguration.getHistory().getRetention().getDeleter())
          .returns(1000, DeleterConfiguration::getDocumentBatchSize)
          .returns(200, DeleterConfiguration::getEntityBatchSize)
          .returns(5, DeleterConfiguration::getMaxRetryAttempts)
          .returns(3000, DeleterConfiguration::getRetryDelayMs)
          .returns(5000, DeleterConfiguration::getDelayBetweenRuns)
          .returns(120000, DeleterConfiguration::getMaxDelayBetweenRuns);
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.data.secondary-storage.type=elasticsearch"})
  class WithDeleterNotSet {
    final BrokerBasedProperties brokerBasedProperties;

    WithDeleterNotSet(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldApplyDeleterDefaultsToCamundaExporter() {
      // given
      final Map<String, Object> args = brokerBasedProperties.getCamundaExporter().getArgs();

      // when
      final ExporterConfiguration exporterConfiguration =
          UnifiedConfigurationHelper.argsToCamundaExporterConfiguration(args);

      // then
      assertThat(exporterConfiguration.getHistory().getRetention().getDeleter())
          .returns(2500, DeleterConfiguration::getDocumentBatchSize)
          .returns(500, DeleterConfiguration::getEntityBatchSize)
          .returns(3, DeleterConfiguration::getMaxRetryAttempts)
          .returns(1000, DeleterConfiguration::getRetryDelayMs)
          .returns(2000, DeleterConfiguration::getDelayBetweenRuns)
          .returns(60000, DeleterConfiguration::getMaxDelayBetweenRuns);
    }
  }
}
