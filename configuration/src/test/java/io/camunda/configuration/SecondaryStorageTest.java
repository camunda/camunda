/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.configuration.beans.SearchEngineConnectProperties;
import io.camunda.operate.conditions.DatabaseType;
import io.camunda.operate.property.OperateProperties;
import io.camunda.tasklist.property.TasklistProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  UnifiedConfigurationHelper.class,
  TasklistPropertiesOverride.class,
  OperatePropertiesOverride.class,
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
    final SearchEngineConnectProperties searchEngineConnectProperties;

    WithOnlyUnifiedConfigSet(
        @Autowired final OperateProperties operateProperties,
        @Autowired final TasklistProperties tasklistProperties,
        @Autowired final SearchEngineConnectProperties searchEngineConnectProperties) {
      this.operateProperties = operateProperties;
      this.tasklistProperties = tasklistProperties;
      this.searchEngineConnectProperties = searchEngineConnectProperties;
    }

    @Test
    void testCamundaDataSecondaryStorageType() {
      final DatabaseType expectedOperateDatabaseType = DatabaseType.Elasticsearch;
      assertThat(operateProperties.getDatabase()).isEqualTo(expectedOperateDatabaseType);

      final String expectedTasklistDatabaseType = "elasticsearch";
      assertThat(tasklistProperties.getDatabase()).isEqualTo(expectedTasklistDatabaseType);
    }

    @Test
    void testCamundaDataSecondaryStorageElasticsearchUrl() {
      final String expectedUrl = "http://expected-url:4321";
      assertThat(operateProperties.getElasticsearch().getUrl()).isEqualTo(expectedUrl);
      assertThat(tasklistProperties.getElasticsearch().getUrl()).isEqualTo(expectedUrl);
      assertThat(searchEngineConnectProperties.getUrl()).isEqualTo(expectedUrl);
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
    final SearchEngineConnectProperties searchEngineConnectProperties;

    WithNewAndLegacySet(
        @Autowired final OperateProperties operateProperties,
        @Autowired final TasklistProperties tasklistProperties,
        @Autowired final SearchEngineConnectProperties searchEngineConnectProperties) {
      this.operateProperties = operateProperties;
      this.tasklistProperties = tasklistProperties;
      this.searchEngineConnectProperties = searchEngineConnectProperties;
    }

    @Test
    void testCamundaDataSecondaryStorageType() {
      final DatabaseType expectedOperateDatabaseType = DatabaseType.Elasticsearch;
      assertThat(operateProperties.getDatabase()).isEqualTo(expectedOperateDatabaseType);

      final String expectedTasklistDatabaseType = "elasticsearch";
      assertThat(tasklistProperties.getDatabase()).isEqualTo(expectedTasklistDatabaseType);

      assertThat(searchEngineConnectProperties.getType())
          .isEqualTo(io.camunda.search.connect.configuration.DatabaseType.ELASTICSEARCH.toString());
    }

    @Test
    void testCamundaDataSecondaryStorageElasticsearchUrl() {
      final String expectedUrl = "http://matching-url:4321";
      assertThat(operateProperties.getElasticsearch().getUrl()).isEqualTo(expectedUrl);
      assertThat(tasklistProperties.getElasticsearch().getUrl()).isEqualTo(expectedUrl);
      assertThat(searchEngineConnectProperties.getUrl()).isEqualTo(expectedUrl);
    }
  }
}
