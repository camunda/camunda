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
import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.beans.SearchEngineConnectProperties;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.operate.conditions.DatabaseType;
import io.camunda.operate.property.OperateProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@ActiveProfiles({"broker", "tasklist", "operate"})
@SpringJUnitConfig({
  UnifiedConfiguration.class,
  UnifiedConfigurationHelper.class,
  TasklistPropertiesOverride.class,
  OperatePropertiesOverride.class,
  BrokerBasedPropertiesOverride.class,
  SearchEngineConnectPropertiesOverride.class
})
public class SecondaryStorageOpensearchTest {
  private static final String EXPECTED_CLUSTER_NAME = "sample-cluster";
  private static final String EXPECTED_INDEX_PREFIX = "sample-index-prefix";

  private static final String EXPECTED_USERNAME = "testUsername";
  private static final String EXPECTED_PASSWORD = "testPassword";

  private static final boolean EXPECTED_HISTORY_PROCESS_INSTANCE_ENABLED = false;

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=opensearch",
        "camunda.data.secondary-storage.opensearch.url=http://expected-url:4321",
        "camunda.data.secondary-storage.opensearch.username=" + EXPECTED_USERNAME,
        "camunda.data.secondary-storage.opensearch.password=" + EXPECTED_PASSWORD,
        "camunda.data.secondary-storage.opensearch.cluster-name=" + EXPECTED_CLUSTER_NAME,
        "camunda.data.secondary-storage.opensearch.index-prefix=" + EXPECTED_INDEX_PREFIX,
        "camunda.data.secondary-storage.opensearch.history.process-instance-enabled="
            + EXPECTED_HISTORY_PROCESS_INSTANCE_ENABLED,
      })
  class WithOnlyUnifiedConfigSet {
    final OperateProperties operateProperties;
    final TasklistProperties tasklistProperties;
    final BrokerBasedProperties brokerBasedProperties;
    final SearchEngineConnectProperties searchEngineConnectProperties;

    WithOnlyUnifiedConfigSet(
        @Autowired final OperateProperties operateProperties,
        @Autowired final TasklistProperties tasklistProperties,
        @Autowired final BrokerBasedProperties brokerBasedProperties,
        @Autowired final SearchEngineConnectProperties searchEngineConnectProperties) {
      this.operateProperties = operateProperties;
      this.tasklistProperties = tasklistProperties;
      this.brokerBasedProperties = brokerBasedProperties;
      this.searchEngineConnectProperties = searchEngineConnectProperties;
    }

    @Test
    void testCamundaDataSecondaryStorageOperateProperties() {
      final DatabaseType expectedOperateDatabaseType = DatabaseType.Opensearch;
      final String expectedUrl = "http://expected-url:4321";

      assertThat(operateProperties.getDatabase()).isEqualTo(expectedOperateDatabaseType);
      assertThat(operateProperties.getOpensearch().getUrl()).isEqualTo(expectedUrl);
      assertThat(operateProperties.getOpensearch().getUsername()).isEqualTo(EXPECTED_USERNAME);
      assertThat(operateProperties.getOpensearch().getPassword()).isEqualTo(EXPECTED_PASSWORD);
      assertThat(operateProperties.getOpensearch().getClusterName())
          .isEqualTo(EXPECTED_CLUSTER_NAME);
      assertThat(operateProperties.getOpensearch().getIndexPrefix())
          .isEqualTo(EXPECTED_INDEX_PREFIX);
    }

    @Test
    void testCamundaDataSecondaryStorageTasklistProperties() {
      final String expectedTasklistDatabaseType = DatabaseType.Opensearch.name().toLowerCase();
      final String expectedUrl = "http://expected-url:4321";

      assertThat(tasklistProperties.getDatabase()).isEqualTo(expectedTasklistDatabaseType);
      assertThat(tasklistProperties.getOpenSearch().getUrl()).isEqualTo(expectedUrl);
      assertThat(tasklistProperties.getOpenSearch().getUsername()).isEqualTo(EXPECTED_USERNAME);
      assertThat(tasklistProperties.getOpenSearch().getPassword()).isEqualTo(EXPECTED_PASSWORD);
      assertThat(tasklistProperties.getOpenSearch().getIndexPrefix())
          .isEqualTo(EXPECTED_INDEX_PREFIX);
    }

    @Test
    void testCamundaDataSecondaryStorageCamundaExporterProperties() {
      final String expectedType = DatabaseType.Opensearch.name().toLowerCase();
      final String expectedUrl = "http://expected-url:4321";

      final ExporterCfg camundaExporter = brokerBasedProperties.getCamundaExporter();
      assertThat(camundaExporter).isNotNull();
      final Map<String, Object> args = camundaExporter.getArgs();
      assertThat(args).isNotNull();

      final ExporterConfiguration exporterConfiguration =
          UnifiedConfigurationHelper.argsToCamundaExporterConfiguration(args);
      assertThat(exporterConfiguration.getConnect().getType()).isEqualTo(expectedType);
      assertThat(exporterConfiguration.getConnect().getUrl()).isEqualTo(expectedUrl);
      assertThat(exporterConfiguration.getConnect().getUsername()).isEqualTo(EXPECTED_USERNAME);
      assertThat(exporterConfiguration.getConnect().getPassword()).isEqualTo(EXPECTED_PASSWORD);
      assertThat(exporterConfiguration.getConnect().getIndexPrefix())
          .isEqualTo(EXPECTED_INDEX_PREFIX);
      assertThat(exporterConfiguration.getHistory().isProcessInstanceEnabled())
          .isEqualTo(EXPECTED_HISTORY_PROCESS_INSTANCE_ENABLED);
    }

    @Test
    void testCamundaSearchEngineConnectProperties() {
      final String expectedType = DatabaseType.Opensearch.name().toLowerCase();

      assertThat(searchEngineConnectProperties.getType().toLowerCase()).isEqualTo(expectedType);
      assertThat(searchEngineConnectProperties.getUrl()).isEqualTo("http://expected-url:4321");
      assertThat(searchEngineConnectProperties.getIndexPrefix()).isEqualTo(EXPECTED_INDEX_PREFIX);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // type
        "camunda.data.secondary-storage.type=opensearch",
        "camunda.database.type=opensearch",
        "camunda.operate.database=opensearch",
        "camunda.tasklist.database=opensearch",
        // url
        "camunda.data.secondary-storage.opensearch.url=http://matching-url:4321",
        "camunda.database.url=http://matching-url:4321",
        "camunda.tasklist.opensearch.url=http://matching-url:4321",
        "camunda.operate.opensearch.url=http://matching-url:4321",
        // username
        "camunda.data.secondary-storage.opensearch.username=" + EXPECTED_USERNAME,
        "camunda.database.username=" + EXPECTED_USERNAME,
        "camunda.operate.opensearch.username=" + EXPECTED_USERNAME,
        "camunda.tasklist.opensearch.username=" + EXPECTED_USERNAME,
        // password
        "camunda.data.secondary-storage.opensearch.password=" + EXPECTED_PASSWORD,
        "camunda.database.password=" + EXPECTED_PASSWORD,
        "camunda.operate.opensearch.password=" + EXPECTED_PASSWORD,
        "camunda.tasklist.opensearch.password=" + EXPECTED_PASSWORD,
        // NOTE: In the following blocks, the camundaExporter doesn't have to be configured, as
        //  it is default with StandaloneCamunda. Any attempt of configuration will fail unless
        //  the className is also configured.

        // cluster name
        "camunda.data.secondary-storage.opensearch.cluster-name=" + EXPECTED_CLUSTER_NAME,
        "camunda.data.clusterName=" + EXPECTED_CLUSTER_NAME,
        "camunda.tasklist.opensearch.clusterName=" + EXPECTED_CLUSTER_NAME,
        "camunda.operate.opensearch.clusterName=" + EXPECTED_CLUSTER_NAME,
        "camunda.operate.opensearch.url=http://matching-url:4321",

        // NOTE: In the following blocks, the camundaExporter doesn't have to be configured, as
        //  it is default with StandaloneCamunda. Any attempt of configuration will fail unless
        //  the className is also configured.

        // index prefix
        "camunda.data.secondary-storage.opensearch.index-prefix=" + EXPECTED_INDEX_PREFIX,
        "camunda.database.indexPrefix=" + EXPECTED_INDEX_PREFIX,
        "camunda.tasklist.opensearch.indexPrefix=" + EXPECTED_INDEX_PREFIX,
        "camunda.operate.opensearch.indexPrefix=" + EXPECTED_INDEX_PREFIX,
      })
  class WithNewAndLegacySet {
    final OperateProperties operateProperties;
    final TasklistProperties tasklistProperties;
    final BrokerBasedProperties brokerBasedProperties;
    final SearchEngineConnectProperties searchEngineConnectProperties;

    WithNewAndLegacySet(
        @Autowired final OperateProperties operateProperties,
        @Autowired final TasklistProperties tasklistProperties,
        @Autowired final BrokerBasedProperties brokerBasedProperties,
        @Autowired final SearchEngineConnectProperties searchEngineConnectProperties) {
      this.operateProperties = operateProperties;
      this.tasklistProperties = tasklistProperties;
      this.brokerBasedProperties = brokerBasedProperties;
      this.searchEngineConnectProperties = searchEngineConnectProperties;
    }

    @Test
    void testCamundaDataSecondaryStorageOperateProperties() {
      final DatabaseType expectedOperateDatabaseType = DatabaseType.Opensearch;
      final String expectedUrl = "http://matching-url:4321";

      assertThat(operateProperties.getDatabase()).isEqualTo(expectedOperateDatabaseType);
      assertThat(operateProperties.getOpensearch().getUrl()).isEqualTo(expectedUrl);
      assertThat(operateProperties.getOpensearch().getClusterName())
          .isEqualTo(EXPECTED_CLUSTER_NAME);
      assertThat(operateProperties.getOpensearch().getIndexPrefix())
          .isEqualTo(EXPECTED_INDEX_PREFIX);
      assertThat(operateProperties.getOpensearch().getUsername()).isEqualTo(EXPECTED_USERNAME);
      assertThat(operateProperties.getOpensearch().getPassword()).isEqualTo(EXPECTED_PASSWORD);
    }

    @Test
    void testCamundaDataSecondaryStorageTasklistProperties() {
      final String expectedTasklistDatabaseType = DatabaseType.Opensearch.name().toLowerCase();
      final String expectedUrl = "http://matching-url:4321";

      assertThat(tasklistProperties.getDatabase()).isEqualTo(expectedTasklistDatabaseType);
      assertThat(tasklistProperties.getOpenSearch().getUrl()).isEqualTo(expectedUrl);
      assertThat(tasklistProperties.getOpenSearch().getUsername()).isEqualTo(EXPECTED_USERNAME);
      assertThat(tasklistProperties.getOpenSearch().getPassword()).isEqualTo(EXPECTED_PASSWORD);
      assertThat(tasklistProperties.getOpenSearch().getIndexPrefix())
          .isEqualTo(EXPECTED_INDEX_PREFIX);
      assertThat(tasklistProperties.getOpenSearch().getClusterName())
          .isEqualTo(EXPECTED_CLUSTER_NAME);
    }

    @Test
    void testCamundaDataSecondaryStorageCamundaExporterProperties() {
      final String expectedType = DatabaseType.Opensearch.name().toLowerCase();
      final String expectedUrl = "http://matching-url:4321";

      final ExporterCfg camundaExporter = brokerBasedProperties.getCamundaExporter();
      assertThat(camundaExporter).isNotNull();
      final Map<String, Object> args = camundaExporter.getArgs();
      assertThat(args).isNotNull();

      final ExporterConfiguration exporterConfiguration =
          UnifiedConfigurationHelper.argsToCamundaExporterConfiguration(args);
      assertThat(exporterConfiguration.getConnect().getType()).isEqualTo(expectedType);
      assertThat(exporterConfiguration.getConnect().getUrl()).isEqualTo(expectedUrl);
      assertThat(exporterConfiguration.getConnect().getUsername()).isEqualTo(EXPECTED_USERNAME);
      assertThat(exporterConfiguration.getConnect().getPassword()).isEqualTo(EXPECTED_PASSWORD);
      assertThat(exporterConfiguration.getConnect().getIndexPrefix())
          .isEqualTo(EXPECTED_INDEX_PREFIX);
      assertThat(exporterConfiguration.getConnect().getClusterName())
          .isEqualTo(EXPECTED_CLUSTER_NAME);
    }

    @Test
    void testCamundaSearchEngineConnectProperties() {
      final String expectedType = DatabaseType.Opensearch.name().toLowerCase();

      assertThat(searchEngineConnectProperties.getType().toLowerCase()).isEqualTo(expectedType);
      assertThat(searchEngineConnectProperties.getUrl()).isEqualTo("http://matching-url:4321");
      assertThat(searchEngineConnectProperties.getIndexPrefix()).isEqualTo(EXPECTED_INDEX_PREFIX);
      assertThat(searchEngineConnectProperties.getClusterName()).isEqualTo(EXPECTED_CLUSTER_NAME);
      assertThat(searchEngineConnectProperties.getUsername()).isEqualTo(EXPECTED_USERNAME);
      assertThat(searchEngineConnectProperties.getPassword()).isEqualTo(EXPECTED_PASSWORD);
    }
  }
}
