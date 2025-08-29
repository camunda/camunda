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
public class SecondaryStorageTest {

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=elasticsearch",
        "camunda.data.secondary-storage.elasticsearch.url=http://expected-url:4321"
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
      final DatabaseType expectedOperateDatabaseType = DatabaseType.Elasticsearch;
      final String expectedUrl = "http://expected-url:4321";

      assertThat(operateProperties.getDatabase()).isEqualTo(expectedOperateDatabaseType);
      assertThat(operateProperties.getElasticsearch().getUrl()).isEqualTo(expectedUrl);
    }

    @Test
    void testCamundaDataSecondaryStorageTasklistProperties() {
      final String expectedTasklistDatabaseType = "elasticsearch";
      final String expectedUrl = "http://expected-url:4321";

      assertThat(tasklistProperties.getDatabase()).isEqualTo(expectedTasklistDatabaseType);
      assertThat(tasklistProperties.getElasticsearch().getUrl()).isEqualTo(expectedUrl);
    }

    @Test
    void testCamundaDataSecondaryStorageCamundaExporterProperties() {
      final String expectedType = "elasticsearch";
      final String expectedUrl = "http://expected-url:4321";

      final ExporterCfg camundaExporter =
          UnifiedConfigurationHelper.getCamundaExporter(brokerBasedProperties);
      assertThat(camundaExporter).isNotNull();
      final Map<String, Object> args = camundaExporter.getArgs();
      assertThat(args).isNotNull();

      final ExporterConfiguration exporterConfiguration =
          UnifiedConfigurationHelper.argsToExporterConfiguration(args);
      assertThat(exporterConfiguration.getConnect().getType()).isEqualTo(expectedType);
      assertThat(exporterConfiguration.getConnect().getUrl()).isEqualTo(expectedUrl);
    }

    @Test
    void testCamundaSearchEngineConnectProperties() {
      assertThat(searchEngineConnectProperties.getType().toLowerCase()).isEqualTo("elasticsearch");
      assertThat(searchEngineConnectProperties.getUrl()).isEqualTo("http://expected-url:4321");
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
        "camunda.operate.elasticsearch.url=http://matching-url:4321"
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
      final DatabaseType expectedOperateDatabaseType = DatabaseType.Elasticsearch;
      final String expectedUrl = "http://matching-url:4321";

      assertThat(operateProperties.getDatabase()).isEqualTo(expectedOperateDatabaseType);
      assertThat(operateProperties.getElasticsearch().getUrl()).isEqualTo(expectedUrl);
    }

    @Test
    void testCamundaDataSecondaryStorageTasklistProperties() {
      final String expectedTasklistDatabaseType = "elasticsearch";
      final String expectedUrl = "http://matching-url:4321";

      assertThat(tasklistProperties.getDatabase()).isEqualTo(expectedTasklistDatabaseType);
      assertThat(tasklistProperties.getElasticsearch().getUrl()).isEqualTo(expectedUrl);
    }

    @Test
    void testCamundaDataSecondaryStorageCamundaExporterProperties() {
      final String expectedType = "elasticsearch";
      final String expectedUrl = "http://matching-url:4321";

      final ExporterCfg camundaExporter =
          UnifiedConfigurationHelper.getCamundaExporter(brokerBasedProperties);
      assertThat(camundaExporter).isNotNull();
      final Map<String, Object> args = camundaExporter.getArgs();
      assertThat(args).isNotNull();

      final ExporterConfiguration exporterConfiguration =
          UnifiedConfigurationHelper.argsToExporterConfiguration(args);
      assertThat(exporterConfiguration.getConnect().getType()).isEqualTo(expectedType);
      assertThat(exporterConfiguration.getConnect().getUrl()).isEqualTo(expectedUrl);
    }

    @Test
    void testCamundaSearchEngineConnectProperties() {
      assertThat(searchEngineConnectProperties.getType().toLowerCase()).isEqualTo("elasticsearch");
      assertThat(searchEngineConnectProperties.getUrl()).isEqualTo("http://matching-url:4321");
    }
  }
}
