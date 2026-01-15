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
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration.ProcessInstanceRetentionMode;
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
  SearchEngineRetentionPropertiesOverride.class,
  BrokerBasedPropertiesOverride.class,
})
public class HistoryElasticsearchTest {

  private static final boolean EXPECTED_HISTORY_PROCESS_INSTANCE_ENABLED = false;
  private static final String EXPECTED_HISTORY_POLICY_NAME = "policy-name-foo";
  private static final String EXPECTED_HISTORY_PROCESS_INSTANCE_RETENTION_MODE = "PI";

  private ExporterConfiguration getExporterConfiguration(
      final BrokerBasedProperties brokerBasedProperties) {

    final ExporterCfg camundaExporter = brokerBasedProperties.getCamundaExporter();
    assertThat(camundaExporter).isNotNull();

    final Map<String, Object> args = camundaExporter.getArgs();
    assertThat(args).isNotNull();

    return UnifiedConfigurationHelper.argsToCamundaExporterConfiguration(args);
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=elasticsearch",
        "camunda.data.secondary-storage.elasticsearch.history.process-instance-enabled="
            + EXPECTED_HISTORY_PROCESS_INSTANCE_ENABLED,
        "camunda.data.secondary-storage.elasticsearch.history.policy-name="
            + EXPECTED_HISTORY_POLICY_NAME,
        "camunda.data.secondary-storage.elasticsearch.history.process-instance-retention-mode="
            + EXPECTED_HISTORY_PROCESS_INSTANCE_RETENTION_MODE
      })
  class WithOnlyUnifiedConfigSet {
    final SearchEngineRetentionProperties searchEngineRetentionProperties;
    final BrokerBasedProperties brokerBasedProperties;

    WithOnlyUnifiedConfigSet(
        @Autowired final SearchEngineRetentionProperties searchEngineRetentionProperties,
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.searchEngineRetentionProperties = searchEngineRetentionProperties;
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void testCamundaSearchEngineRetentionProperties() {
      assertThat(searchEngineRetentionProperties)
          .returns(EXPECTED_HISTORY_POLICY_NAME, SearchEngineRetentionProperties::getPolicyName);
    }

    @Test
    void testCamundaExporterProperties() {
      final ExporterConfiguration exporterConfiguration =
          getExporterConfiguration(brokerBasedProperties);

      assertThat(exporterConfiguration.getHistory().isProcessInstanceEnabled())
          .isEqualTo(EXPECTED_HISTORY_PROCESS_INSTANCE_ENABLED);
      assertThat(exporterConfiguration.getHistory().getRetention())
          .returns(EXPECTED_HISTORY_POLICY_NAME, RetentionConfiguration::getPolicyName);
      assertThat(exporterConfiguration.getHistory().getProcessInstanceRetentionMode())
          .isEqualTo(ProcessInstanceRetentionMode.PI);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=elasticsearch",
        // policy name
        "camunda.data.secondary-storage.elasticsearch.history.policy-name="
            + EXPECTED_HISTORY_POLICY_NAME,
        "camunda.database.retention.policyName=" + EXPECTED_HISTORY_POLICY_NAME,
        // process instance retention mode
        "camunda.data.secondary-storage.elasticsearch.history.process-instance-retention-mode="
            + EXPECTED_HISTORY_PROCESS_INSTANCE_RETENTION_MODE,
        "zeebe.broker.exporters.camundaexporter.args.history.processInstanceRetentionMode="
            + EXPECTED_HISTORY_PROCESS_INSTANCE_RETENTION_MODE
      })
  class WithNewAndLegacySet {
    final SearchEngineRetentionProperties searchEngineRetentionProperties;
    final BrokerBasedProperties brokerBasedProperties;

    WithNewAndLegacySet(
        @Autowired final SearchEngineRetentionProperties searchEngineRetentionProperties,
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.searchEngineRetentionProperties = searchEngineRetentionProperties;
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void testCamundaSearchEngineRetentionProperties() {
      assertThat(searchEngineRetentionProperties)
          .returns(EXPECTED_HISTORY_POLICY_NAME, SearchEngineRetentionProperties::getPolicyName);
    }

    @Test
    void testCamundaExporterProperties() {
      final ExporterConfiguration exporterConfiguration =
          getExporterConfiguration(brokerBasedProperties);

      assertThat(exporterConfiguration.getHistory().getRetention().getPolicyName())
          .isEqualTo(EXPECTED_HISTORY_POLICY_NAME);
      assertThat(exporterConfiguration.getHistory().getProcessInstanceRetentionMode())
          .isEqualTo(ProcessInstanceRetentionMode.PI);
    }
  }
}
