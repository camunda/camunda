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
import io.camunda.operate.property.OperateProperties;
import io.camunda.search.connect.plugin.PluginConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import java.nio.file.Path;
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
  OperatePropertiesOverride.class,
  TasklistPropertiesOverride.class,
  SearchEngineConnectPropertiesOverride.class,
  BrokerBasedPropertiesOverride.class
})
public class InterceptorPluginsElasticsearchTest {

  private static final PluginConfiguration EXPECTED_PLUGIN_CONFIGURATION_0 =
      new PluginConfiguration("0Id", "0ClassName", Path.of("0JarPath"));
  private static final PluginConfiguration EXPECTED_PLUGIN_CONFIGURATION_1 =
      new PluginConfiguration("1Id", "1ClassName", Path.of("1JarPath"));

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=elasticsearch",
        "camunda.data.secondary-storage.elasticsearch.interceptor-plugins.0.id=0Id",
        "camunda.data.secondary-storage.elasticsearch.interceptor-plugins.0.class-name=0ClassName",
        "camunda.data.secondary-storage.elasticsearch.interceptor-plugins.0.jar-path=0JarPath",
        "camunda.data.secondary-storage.elasticsearch.interceptor-plugins.1.id=1Id",
        "camunda.data.secondary-storage.elasticsearch.interceptor-plugins.1.class-name=1ClassName",
        "camunda.data.secondary-storage.elasticsearch.interceptor-plugins.1.jar-path=1JarPath"
      })
  class WithOnlyUnifiedConfigSet {
    final OperateProperties operateProperties;
    final TasklistProperties tasklistProperties;
    final SearchEngineConnectProperties searchEngineConnectProperties;
    final BrokerBasedProperties brokerBasedProperties;

    WithOnlyUnifiedConfigSet(
        @Autowired final OperateProperties operateProperties,
        @Autowired final TasklistProperties tasklistProperties,
        @Autowired final SearchEngineConnectProperties searchEngineConnectProperties,
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.operateProperties = operateProperties;
      this.tasklistProperties = tasklistProperties;
      this.searchEngineConnectProperties = searchEngineConnectProperties;
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void testCamundaDataSecondaryStorageOperateProperties() {
      assertThat(operateProperties.getElasticsearch().getInterceptorPlugins())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactly(EXPECTED_PLUGIN_CONFIGURATION_0, EXPECTED_PLUGIN_CONFIGURATION_1);
    }

    @Test
    void testCamundaDataSecondaryStorageTasklistProperties() {
      assertThat(tasklistProperties.getElasticsearch().getInterceptorPlugins())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactly(EXPECTED_PLUGIN_CONFIGURATION_0, EXPECTED_PLUGIN_CONFIGURATION_1);
    }

    @Test
    void testCamundaDataSecondaryStorageSearchEngineConnectProperties() {
      assertThat(searchEngineConnectProperties.getInterceptorPlugins())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactly(EXPECTED_PLUGIN_CONFIGURATION_0, EXPECTED_PLUGIN_CONFIGURATION_1);
    }

    @Test
    void testCamundaDataSecondaryStorageCamundaExporterProperties() {
      final ExporterCfg camundaExporter = brokerBasedProperties.getCamundaExporter();
      assertThat(camundaExporter).isNotNull();
      final Map<String, Object> args = camundaExporter.getArgs();
      assertThat(args).isNotNull();

      final ExporterConfiguration exporterConfiguration =
          UnifiedConfigurationHelper.argsToCamundaExporterConfiguration(args);

      assertThat(exporterConfiguration.getConnect().getInterceptorPlugins())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactly(EXPECTED_PLUGIN_CONFIGURATION_0, EXPECTED_PLUGIN_CONFIGURATION_1);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=elasticsearch",
        // legacy operate
        "camunda.operate.elasticsearch.interceptorPlugins.0.id=0Id",
        "camunda.operate.elasticsearch.interceptorPlugins.0.className=0ClassName",
        "camunda.operate.elasticsearch.interceptorPlugins.0.jarPath=0JarPath",
        "camunda.operate.elasticsearch.interceptorPlugins.1.id=1Id",
        "camunda.operate.elasticsearch.interceptorPlugins.1.className=1ClassName",
        "camunda.operate.elasticsearch.interceptorPlugins.1.jarPath=1JarPath",
        // legacy tasklist
        "camunda.tasklist.elasticsearch.interceptorPlugins.0.id=0Id",
        "camunda.tasklist.elasticsearch.interceptorPlugins.0.className=0ClassName",
        "camunda.tasklist.elasticsearch.interceptorPlugins.0.jarPath=0JarPath",
        "camunda.tasklist.elasticsearch.interceptorPlugins.1.id=1Id",
        "camunda.tasklist.elasticsearch.interceptorPlugins.1.className=1ClassName",
        "camunda.tasklist.elasticsearch.interceptorPlugins.1.jarPath=1JarPath",
        // legacy search engine database configuration
        "camunda.database.interceptorPlugins.0.id=0Id",
        "camunda.database.interceptorPlugins.0.className=0ClassName",
        "camunda.database.interceptorPlugins.0.jarPath=0JarPath",
        "camunda.database.interceptorPlugins.1.id=1Id",
        "camunda.database.interceptorPlugins.1.className=1ClassName",
        "camunda.database.interceptorPlugins.1.jarPath=1JarPath",
        // legacy exporter
        "zeebe.broker.exporters.camundaexporter.className=io.camunda.exporter.CamundaExporter",
        "zeebe.broker.exporters.camundaexporter.args.connect.interceptorPlugins.0.id=0Id",
        "zeebe.broker.exporters.camundaexporter.args.connect.interceptorPlugins.0.className=0ClassName",
        "zeebe.broker.exporters.camundaexporter.args.connect.interceptorPlugins.0.jarPath=0JarPath",
        "zeebe.broker.exporters.camundaexporter.args.connect.interceptorPlugins.1.id=1Id",
        "zeebe.broker.exporters.camundaexporter.args.connect.interceptorPlugins.1.className=1ClassName",
        "zeebe.broker.exporters.camundaexporter.args.connect.interceptorPlugins.1.jarPath=1JarPath",
      })
  class WithOnlyLegacySet {
    final OperateProperties operateProperties;
    final TasklistProperties tasklistProperties;
    final SearchEngineConnectProperties searchEngineConnectProperties;
    final BrokerBasedProperties brokerBasedProperties;

    WithOnlyLegacySet(
        @Autowired final OperateProperties operateProperties,
        @Autowired final TasklistProperties tasklistProperties,
        @Autowired final SearchEngineConnectProperties searchEngineConnectProperties,
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.operateProperties = operateProperties;
      this.tasklistProperties = tasklistProperties;
      this.searchEngineConnectProperties = searchEngineConnectProperties;
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void testCamundaDataSecondaryStorageOperateProperties() {
      assertThat(operateProperties.getElasticsearch().getInterceptorPlugins())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactly(EXPECTED_PLUGIN_CONFIGURATION_0, EXPECTED_PLUGIN_CONFIGURATION_1);
    }

    @Test
    void testCamundaDataSecondaryStorageTasklistProperties() {
      assertThat(tasklistProperties.getElasticsearch().getInterceptorPlugins())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactly(EXPECTED_PLUGIN_CONFIGURATION_0, EXPECTED_PLUGIN_CONFIGURATION_1);
    }

    @Test
    void testCamundaDataSecondaryStorageSearchEngineConnectProperties() {
      assertThat(searchEngineConnectProperties.getInterceptorPlugins())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactly(EXPECTED_PLUGIN_CONFIGURATION_0, EXPECTED_PLUGIN_CONFIGURATION_1);
    }

    @Test
    void testCamundaDataSecondaryStorageCamundaExporterProperties() {
      final ExporterCfg camundaExporter = brokerBasedProperties.getCamundaExporter();
      assertThat(camundaExporter).isNotNull();
      final Map<String, Object> args = camundaExporter.getArgs();
      assertThat(args).isNotNull();

      final ExporterConfiguration exporterConfiguration =
          UnifiedConfigurationHelper.argsToCamundaExporterConfiguration(args);

      assertThat(exporterConfiguration.getConnect().getInterceptorPlugins())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactly(EXPECTED_PLUGIN_CONFIGURATION_0, EXPECTED_PLUGIN_CONFIGURATION_1);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=elasticsearch",
        // new
        "camunda.data.secondary-storage.elasticsearch.interceptor-plugins.0.id=0Id",
        "camunda.data.secondary-storage.elasticsearch.interceptor-plugins.0.class-name=0ClassName",
        "camunda.data.secondary-storage.elasticsearch.interceptor-plugins.0.jar-path=0JarPath",
        "camunda.data.secondary-storage.elasticsearch.interceptor-plugins.1.id=1Id",
        "camunda.data.secondary-storage.elasticsearch.interceptor-plugins.1.class-name=1ClassName",
        "camunda.data.secondary-storage.elasticsearch.interceptor-plugins.1.jar-path=1JarPath",
        // legacy operate
        "camunda.operate.elasticsearch.interceptorPlugins.0.id=0IdOperate",
        "camunda.operate.elasticsearch.interceptorPlugins.0.className=0ClassNameOperate",
        "camunda.operate.elasticsearch.interceptorPlugins.0.jarPath=0JarPathOperate",
        "camunda.operate.elasticsearch.interceptorPlugins.1.id=1IdOperate",
        "camunda.operate.elasticsearch.interceptorPlugins.1.className=1ClassNameOperate",
        "camunda.operate.elasticsearch.interceptorPlugins.1.jarPath=1JarPathOperate",
        // legacy tasklist
        "camunda.tasklist.elasticsearch.interceptorPlugins.0.id=0IdTasklist",
        "camunda.tasklist.elasticsearch.interceptorPlugins.0.className=0ClassNameTasklist",
        "camunda.tasklist.elasticsearch.interceptorPlugins.0.jarPath=0JarPathTasklist",
        "camunda.tasklist.elasticsearch.interceptorPlugins.1.id=1IdTasklist",
        "camunda.tasklist.elasticsearch.interceptorPlugins.1.className=1ClassNameTasklist",
        "camunda.tasklist.elasticsearch.interceptorPlugins.1.jarPath=1JarPathTasklist",
        // legacy search engine database configuration
        "camunda.database.interceptorPlugins.0.id=0IdSearchEngine",
        "camunda.database.interceptorPlugins.0.className=0ClassNameSearchEngine",
        "camunda.database.interceptorPlugins.0.jarPath=0JarPathSearchEngine",
        "camunda.database.interceptorPlugins.1.id=1IdSearhEngine",
        "camunda.database.interceptorPlugins.1.className=1ClassNameSearchEngine",
        "camunda.database.interceptorPlugins.1.jarPath=1JarPathSearchEngine",
        // legacy exporter
        "zeebe.broker.exporters.camundaexporter.className=io.camunda.exporter.CamundaExporter",
        "zeebe.broker.exporters.camundaexporter.args.connect.interceptorPlugins.0.id=0IdExporter",
        "zeebe.broker.exporters.camundaexporter.args.connect.interceptorPlugins.0.className=0ClassNameExporter",
        "zeebe.broker.exporters.camundaexporter.args.connect.interceptorPlugins.0.jarPath=0JarPathExporter",
        "zeebe.broker.exporters.camundaexporter.args.connect.interceptorPlugins.1.id=1IdExporter",
        "zeebe.broker.exporters.camundaexporter.args.connect.interceptorPlugins.1.className=1ClassNameExporter",
        "zeebe.broker.exporters.camundaexporter.args.connect.interceptorPlugins.1.jarPath=1JarPathExporter",
      })
  class WithNewAndLegacySet {
    final OperateProperties operateProperties;
    final TasklistProperties tasklistProperties;
    final SearchEngineConnectProperties searchEngineConnectProperties;
    final BrokerBasedProperties brokerBasedProperties;

    WithNewAndLegacySet(
        @Autowired final OperateProperties operateProperties,
        @Autowired final TasklistProperties tasklistProperties,
        @Autowired final SearchEngineConnectProperties searchEngineConnectProperties,
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.operateProperties = operateProperties;
      this.tasklistProperties = tasklistProperties;
      this.searchEngineConnectProperties = searchEngineConnectProperties;
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void testCamundaDataSecondaryStorageOperateProperties() {
      assertThat(operateProperties.getElasticsearch().getInterceptorPlugins())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactly(EXPECTED_PLUGIN_CONFIGURATION_0, EXPECTED_PLUGIN_CONFIGURATION_1);
    }

    @Test
    void testCamundaDataSecondaryStorageTasklistProperties() {
      assertThat(tasklistProperties.getElasticsearch().getInterceptorPlugins())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactly(EXPECTED_PLUGIN_CONFIGURATION_0, EXPECTED_PLUGIN_CONFIGURATION_1);
    }

    @Test
    void testCamundaDataSecondaryStorageSearchEngineConnectProperties() {
      assertThat(searchEngineConnectProperties.getInterceptorPlugins())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactly(EXPECTED_PLUGIN_CONFIGURATION_0, EXPECTED_PLUGIN_CONFIGURATION_1);
    }

    @Test
    void testCamundaDataSecondaryStorageCamundaExporterProperties() {
      final ExporterCfg camundaExporter = brokerBasedProperties.getCamundaExporter();
      assertThat(camundaExporter).isNotNull();
      final Map<String, Object> args = camundaExporter.getArgs();
      assertThat(args).isNotNull();

      final ExporterConfiguration exporterConfiguration =
          UnifiedConfigurationHelper.argsToCamundaExporterConfiguration(args);

      assertThat(exporterConfiguration.getConnect().getInterceptorPlugins())
          .hasSize(2)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactly(EXPECTED_PLUGIN_CONFIGURATION_0, EXPECTED_PLUGIN_CONFIGURATION_1);
    }
  }
}
