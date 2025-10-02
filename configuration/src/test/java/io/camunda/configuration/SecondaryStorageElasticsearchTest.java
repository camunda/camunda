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
import io.camunda.configuration.beanoverrides.SearchEngineIndexPropertiesOverride;
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.beans.SearchEngineConnectProperties;
import io.camunda.configuration.beans.SearchEngineIndexProperties;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.operate.conditions.DatabaseType;
import io.camunda.operate.property.OperateProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles({"broker", "tasklist", "operate"})
@SpringBootTest(
    classes = {
      UnifiedConfiguration.class,
      TasklistPropertiesOverride.class,
      OperatePropertiesOverride.class,
      BrokerBasedPropertiesOverride.class,
      SearchEngineConnectPropertiesOverride.class,
      SearchEngineIndexPropertiesOverride.class,
    })
public class SecondaryStorageElasticsearchTest {
  private static final String EXPECTED_CLUSTER_NAME = "sample-cluster";
  private static final String EXPECTED_INDEX_PREFIX = "sample-index-prefix";

  private static final String EXPECTED_USERNAME = "testUsername";
  private static final String EXPECTED_PASSWORD = "testPassword";

  private static final int EXPECTED_NUMBER_OF_SHARDS = 3;

  private static ExporterConfiguration fromArgs(final Map<String, Object> args) {
    return new io.camunda.zeebe.broker.exporter.context.ExporterConfiguration(
            "camundaexporter", args)
        .instantiate(ExporterConfiguration.class);
  }

  private static final boolean EXPECTED_HISTORY_PROCESS_INSTANCE_ENABLED = false;

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=elasticsearch",
        "camunda.data.secondary-storage.elasticsearch.url=http://expected-url:4321",
        "camunda.data.secondary-storage.elasticsearch.username=" + EXPECTED_USERNAME,
        "camunda.data.secondary-storage.elasticsearch.password=" + EXPECTED_PASSWORD,
        "camunda.data.secondary-storage.elasticsearch.cluster-name=" + EXPECTED_CLUSTER_NAME,
        "camunda.data.secondary-storage.elasticsearch.index-prefix=" + EXPECTED_INDEX_PREFIX,
        "camunda.data.secondary-storage.elasticsearch.number-of-shards="
            + EXPECTED_NUMBER_OF_SHARDS,
        "camunda.data.secondary-storage.elasticsearch.history.process-instance-enabled="
            + EXPECTED_HISTORY_PROCESS_INSTANCE_ENABLED,
      })
  class WithOnlyUnifiedConfigSet {
    final OperateProperties operateProperties;
    final TasklistProperties tasklistProperties;
    final BrokerBasedProperties brokerBasedProperties;
    final SearchEngineConnectProperties searchEngineConnectProperties;
    final SearchEngineIndexProperties searchEngineIndexProperties;

    WithOnlyUnifiedConfigSet(
        @Autowired final OperateProperties operateProperties,
        @Autowired final TasklistProperties tasklistProperties,
        @Autowired final BrokerBasedProperties brokerBasedProperties,
        @Autowired final SearchEngineConnectProperties searchEngineConnectProperties,
        @Autowired final SearchEngineIndexProperties searchEngineIndexProperties) {
      this.operateProperties = operateProperties;
      this.tasklistProperties = tasklistProperties;
      this.brokerBasedProperties = brokerBasedProperties;
      this.searchEngineConnectProperties = searchEngineConnectProperties;
      this.searchEngineIndexProperties = searchEngineIndexProperties;
    }

    @Test
    void testCamundaDataSecondaryStorageOperateProperties() {
      final DatabaseType expectedOperateDatabaseType = DatabaseType.Elasticsearch;
      final String expectedUrl = "http://expected-url:4321";

      assertThat(operateProperties.getDatabase()).isEqualTo(expectedOperateDatabaseType);
      assertThat(operateProperties.getElasticsearch().getUrl()).isEqualTo(expectedUrl);
      assertThat(operateProperties.getElasticsearch().getUsername()).isEqualTo(EXPECTED_USERNAME);
      assertThat(operateProperties.getElasticsearch().getPassword()).isEqualTo(EXPECTED_PASSWORD);
      assertThat(operateProperties.getElasticsearch().getClusterName())
          .isEqualTo(EXPECTED_CLUSTER_NAME);
      assertThat(operateProperties.getElasticsearch().getIndexPrefix())
          .isEqualTo(EXPECTED_INDEX_PREFIX);
    }

    @Test
    void testCamundaDataSecondaryStorageTasklistProperties() {
      final String expectedTasklistDatabaseType = "elasticsearch";
      final String expectedUrl = "http://expected-url:4321";

      assertThat(tasklistProperties.getDatabase()).isEqualTo(expectedTasklistDatabaseType);
      assertThat(tasklistProperties.getElasticsearch().getUrl()).isEqualTo(expectedUrl);
      assertThat(tasklistProperties.getElasticsearch().getUsername()).isEqualTo(EXPECTED_USERNAME);
      assertThat(tasklistProperties.getElasticsearch().getPassword()).isEqualTo(EXPECTED_PASSWORD);
      assertThat(tasklistProperties.getElasticsearch().getIndexPrefix())
          .isEqualTo(EXPECTED_INDEX_PREFIX);
    }

    @Test
    void testCamundaDataSecondaryStorageCamundaExporterProperties() {
      final String expectedType = "elasticsearch";
      final String expectedUrl = "http://expected-url:4321";

      final ExporterCfg camundaExporter = brokerBasedProperties.getCamundaExporter();
      assertThat(camundaExporter).isNotNull();
      final Map<String, Object> args = camundaExporter.getArgs();
      assertThat(args).isNotNull();

      final ExporterConfiguration exporterConfiguration = fromArgs(args);
      assertThat(exporterConfiguration.getConnect().getType()).isEqualTo(expectedType);
      assertThat(exporterConfiguration.getConnect().getUrl()).isEqualTo(expectedUrl);
      assertThat(exporterConfiguration.getConnect().getUsername()).isEqualTo(EXPECTED_USERNAME);
      assertThat(exporterConfiguration.getConnect().getPassword()).isEqualTo(EXPECTED_PASSWORD);
      assertThat(exporterConfiguration.getConnect().getIndexPrefix())
          .isEqualTo(EXPECTED_INDEX_PREFIX);
      assertThat(exporterConfiguration.getIndex().getNumberOfShards())
          .isEqualTo(EXPECTED_NUMBER_OF_SHARDS);
      assertThat(exporterConfiguration.getHistory().isProcessInstanceEnabled())
          .isEqualTo(EXPECTED_HISTORY_PROCESS_INSTANCE_ENABLED);
    }

    @Test
    void testCamundaSearchEngineConnectProperties() {
      assertThat(searchEngineConnectProperties.getType().toLowerCase()).isEqualTo("elasticsearch");
      assertThat(searchEngineConnectProperties.getUrl()).isEqualTo("http://expected-url:4321");
      assertThat(searchEngineConnectProperties.getIndexPrefix()).isEqualTo(EXPECTED_INDEX_PREFIX);
    }

    @Test
    void testCamundaSearchEngineIndexProperties() {
      assertThat(searchEngineIndexProperties.getNumberOfShards())
          .isEqualTo(EXPECTED_NUMBER_OF_SHARDS);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // type
        "camunda.data.secondary-storage.type=elasticsearch",
        "camunda.database.type=elasticsearch",
        "camunda.operate.database=elasticsearch",
        "camunda.tasklist.database=elasticsearch",
        // url
        "camunda.data.secondary-storage.elasticsearch.url=http://matching-url:4321",
        "camunda.database.url=http://matching-url:4321",
        "camunda.tasklist.elasticsearch.url=http://matching-url:4321",
        "camunda.operate.elasticsearch.url=http://matching-url:4321",
        // username
        "camunda.data.secondary-storage.elasticsearch.username=" + EXPECTED_USERNAME,
        "camunda.database.username=" + EXPECTED_USERNAME,
        "camunda.operate.elasticsearch.username=" + EXPECTED_USERNAME,
        "camunda.tasklist.elasticsearch.username=" + EXPECTED_USERNAME,
        // password
        "camunda.data.secondary-storage.elasticsearch.password=" + EXPECTED_PASSWORD,
        "camunda.database.password=" + EXPECTED_PASSWORD,
        "camunda.operate.elasticsearch.password=" + EXPECTED_PASSWORD,
        "camunda.tasklist.elasticsearch.password=" + EXPECTED_PASSWORD,
        // NOTE: In the following blocks, the camundaExporter doesn't have to be configured, as
        //  it is default with StandaloneCamunda. Any attempt of configuration will fail unless
        //  the className is also configured.

        // cluster name
        "camunda.data.secondary-storage.elasticsearch.cluster-name=" + EXPECTED_CLUSTER_NAME,
        "camunda.data.clusterName=" + EXPECTED_CLUSTER_NAME,
        "camunda.tasklist.elasticsearch.clusterName=" + EXPECTED_CLUSTER_NAME,
        "camunda.operate.elasticsearch.clusterName=" + EXPECTED_CLUSTER_NAME,
        "camunda.operate.elasticsearch.url=http://matching-url:4321",

        // NOTE: In the following blocks, the camundaExporter doesn't have to be configured, as
        //  it is default with StandaloneCamunda. Any attempt of configuration will fail unless
        //  the className is also configured.

        // index prefix
        "camunda.data.secondary-storage.elasticsearch.index-prefix=" + EXPECTED_INDEX_PREFIX,
        "camunda.database.indexPrefix=" + EXPECTED_INDEX_PREFIX,
        "camunda.tasklist.elasticsearch.indexPrefix=" + EXPECTED_INDEX_PREFIX,
        "camunda.operate.elasticsearch.indexPrefix=" + EXPECTED_INDEX_PREFIX,

        // number of shards
        "camunda.data.secondary-storage.elasticsearch.number-of-shards="
            + EXPECTED_NUMBER_OF_SHARDS,
        "camunda.database.index.numberOfShards=" + EXPECTED_NUMBER_OF_SHARDS,
      })
  class WithNewAndLegacySet {
    final OperateProperties operateProperties;
    final TasklistProperties tasklistProperties;
    final BrokerBasedProperties brokerBasedProperties;
    final SearchEngineConnectProperties searchEngineConnectProperties;
    final SearchEngineIndexProperties searchEngineIndexProperties;

    WithNewAndLegacySet(
        @Autowired final OperateProperties operateProperties,
        @Autowired final TasklistProperties tasklistProperties,
        @Autowired final BrokerBasedProperties brokerBasedProperties,
        @Autowired final SearchEngineConnectProperties searchEngineConnectProperties,
        @Autowired final SearchEngineIndexProperties searchEngineIndexProperties) {
      this.operateProperties = operateProperties;
      this.tasklistProperties = tasklistProperties;
      this.brokerBasedProperties = brokerBasedProperties;
      this.searchEngineConnectProperties = searchEngineConnectProperties;
      this.searchEngineIndexProperties = searchEngineIndexProperties;
    }

    @Test
    void testCamundaDataSecondaryStorageOperateProperties() {
      final DatabaseType expectedOperateDatabaseType = DatabaseType.Elasticsearch;
      final String expectedUrl = "http://matching-url:4321";

      assertThat(operateProperties.getDatabase()).isEqualTo(expectedOperateDatabaseType);
      assertThat(operateProperties.getElasticsearch().getUrl()).isEqualTo(expectedUrl);
      assertThat(operateProperties.getElasticsearch().getClusterName())
          .isEqualTo(EXPECTED_CLUSTER_NAME);
      assertThat(operateProperties.getElasticsearch().getIndexPrefix())
          .isEqualTo(EXPECTED_INDEX_PREFIX);
      assertThat(operateProperties.getElasticsearch().getUsername()).isEqualTo(EXPECTED_USERNAME);
      assertThat(operateProperties.getElasticsearch().getPassword()).isEqualTo(EXPECTED_PASSWORD);
    }

    @Test
    void testCamundaDataSecondaryStorageTasklistProperties() {
      final String expectedTasklistDatabaseType = "elasticsearch";
      final String expectedUrl = "http://matching-url:4321";

      assertThat(tasklistProperties.getDatabase()).isEqualTo(expectedTasklistDatabaseType);
      assertThat(tasklistProperties.getElasticsearch().getUrl()).isEqualTo(expectedUrl);
      assertThat(tasklistProperties.getElasticsearch().getUsername()).isEqualTo(EXPECTED_USERNAME);
      assertThat(tasklistProperties.getElasticsearch().getPassword()).isEqualTo(EXPECTED_PASSWORD);
      assertThat(tasklistProperties.getElasticsearch().getIndexPrefix())
          .isEqualTo(EXPECTED_INDEX_PREFIX);
      assertThat(tasklistProperties.getElasticsearch().getClusterName())
          .isEqualTo(EXPECTED_CLUSTER_NAME);
    }

    @Test
    void testCamundaDataSecondaryStorageCamundaExporterProperties() {
      final String expectedType = "elasticsearch";
      final String expectedUrl = "http://matching-url:4321";

      final ExporterCfg camundaExporter = brokerBasedProperties.getCamundaExporter();
      assertThat(camundaExporter).isNotNull();
      final Map<String, Object> args = camundaExporter.getArgs();
      assertThat(args).isNotNull();

      final ExporterConfiguration exporterConfiguration = fromArgs(args);
      assertThat(exporterConfiguration.getConnect().getType()).isEqualTo(expectedType);
      assertThat(exporterConfiguration.getConnect().getUrl()).isEqualTo(expectedUrl);
      assertThat(exporterConfiguration.getConnect().getUsername()).isEqualTo(EXPECTED_USERNAME);
      assertThat(exporterConfiguration.getConnect().getPassword()).isEqualTo(EXPECTED_PASSWORD);
      assertThat(exporterConfiguration.getConnect().getIndexPrefix())
          .isEqualTo(EXPECTED_INDEX_PREFIX);
      assertThat(exporterConfiguration.getConnect().getClusterName())
          .isEqualTo(EXPECTED_CLUSTER_NAME);
      assertThat(exporterConfiguration.getIndex().getNumberOfShards())
          .isEqualTo(EXPECTED_NUMBER_OF_SHARDS);
    }

    @Test
    void testCamundaSearchEngineConnectProperties() {
      assertThat(searchEngineConnectProperties.getType().toLowerCase()).isEqualTo("elasticsearch");
      assertThat(searchEngineConnectProperties.getUrl()).isEqualTo("http://matching-url:4321");
      assertThat(searchEngineConnectProperties.getIndexPrefix()).isEqualTo(EXPECTED_INDEX_PREFIX);
      assertThat(searchEngineConnectProperties.getClusterName()).isEqualTo(EXPECTED_CLUSTER_NAME);
      assertThat(searchEngineConnectProperties.getUsername()).isEqualTo(EXPECTED_USERNAME);
      assertThat(searchEngineConnectProperties.getPassword()).isEqualTo(EXPECTED_PASSWORD);
    }

    @Test
    void testCamundaSearchEngineIndexProperties() {
      assertThat(searchEngineIndexProperties.getNumberOfShards())
          .isEqualTo(EXPECTED_NUMBER_OF_SHARDS);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=elasticsearch",
        "camunda.data.secondary-storage.elasticsearch.url=http://matching-url:4321",
        "zeebe.broker.exporters.camundaexporter.class-name=io.camunda.exporter.CamundaExporter"
      })
  class ExporterTestWithoutArgs {
    final OperateProperties operateProperties;
    final TasklistProperties tasklistProperties;
    final BrokerBasedProperties brokerBasedProperties;
    final SearchEngineConnectProperties searchEngineConnectProperties;

    ExporterTestWithoutArgs(
        @Autowired final OperateProperties operateProperties,
        @Autowired final TasklistProperties tasklistProperties,
        @Autowired final BrokerBasedProperties brokerBasedProperties,
        @Autowired final SearchEngineConnectProperties searchEngineConnectProperties) {
      this.operateProperties = operateProperties;
      this.tasklistProperties = tasklistProperties;
      this.brokerBasedProperties = brokerBasedProperties;
      this.searchEngineConnectProperties = searchEngineConnectProperties;
    }

    // https://github.com/camunda/camunda/issues/37880
    // it is possible to have an exporter with no args defined
    @Test
    void testSecondaryStorageExporterCanWorkWithoutArgs() {
      final ExporterCfg camundaExporter = brokerBasedProperties.getCamundaExporter();
      assertThat(camundaExporter).isNotNull();

      final Map<String, Object> args = camundaExporter.getArgs();
      assertThat(args).isNotNull();

      final ExporterConfiguration exporterConfiguration =
          UnifiedConfigurationHelper.argsToCamundaExporterConfiguration(args);
      assertThat(exporterConfiguration.getConnect().getUrl()).isEqualTo("http://matching-url:4321");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.autoconfigure-camunda-exporter=false",
        "camunda.data.secondary-storage.elasticsearch.url=http://unwanted-url:4321",
      })
  class ExporterAutoconfigurationDisabled {
    final BrokerBasedProperties brokerBasedProperties;

    ExporterAutoconfigurationDisabled(
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void testExporterAutoconfigurationDisabled() {
      final ExporterCfg camundaExporter = brokerBasedProperties.getCamundaExporter();
      assertThat(camundaExporter).isNull();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.autoconfigure-camunda-exporter=true",
        "camunda.data.secondary-storage.elasticsearch.url=http://wanted-url:4321",
      })
  class ExporterAutoconfigurationEnabled {
    final BrokerBasedProperties brokerBasedProperties;

    ExporterAutoconfigurationEnabled(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void testExporterAutoconfigurationEnabled() {
      final ExporterCfg camundaExporter = brokerBasedProperties.getCamundaExporter();
      assertThat(camundaExporter).isNotNull();

      final Map<String, Object> args = camundaExporter.getArgs();
      assertThat(args).isNotNull();

      final ExporterConfiguration exporterConfiguration = fromArgs(args);

      assertThat(exporterConfiguration.getConnect().getUrl()).isEqualTo("http://wanted-url:4321");
    }
  }
}
