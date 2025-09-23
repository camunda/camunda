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
import io.camunda.exporter.rdbms.ExporterConfiguration;
import io.camunda.operate.property.OperateProperties;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import java.time.Duration;
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
  SearchEngineConnectPropertiesOverride.class,
})
public class SecondaryStorageRdbmsTest {
  public static final String EXPECTED_FLUSH_INTERVAL = "PT0.5S";
  public static final int EXPECTED_QUEUE_SIZE = 1000;
  private static final String EXPECTED_INDEX_PREFIX = "sample-index-prefix";
  private static final String EXPECTED_USERNAME = "testUsername";
  private static final String EXPECTED_PASSWORD = "testPassword";

  private static final String DEFAULT_HISTORY_TTL = "PT1M";
  private static final String DEFAULT_BATCH_OPERATION_HISTORY_TTL = "PT168H"; // 7 days
  private static final String BATCH_OPERATION_CANCEL_PROCESS_INSTANCE_HISTORY_TTL =
      "PT24H"; // 1 day
  private static final String BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE_HISTORY_TTL =
      "PT240H"; // 10 days
  private static final String BATCH_OPERATION_MODIFY_PROCESS_INSTANCE_HISTORY_TTL =
      "PT168H"; // 7 days
  private static final String BATCH_OPERATION_RESOLVE_INCIDENT_HISTORY_TTL = "PT120H"; // 5 days
  private static final String MIN_HISTORY_CLEANUP_INTERVAL = "PT1S";
  private static final String MAX_HISTORY_CLEANUP_INTERVAL = "PT1H";
  private static final int HISTORY_CLEANUP_BATCH_SIZE = 1000;

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=rdbms",
        "camunda.data.secondary-storage.rdbms.url=http://expected-url:4321",
        "camunda.data.secondary-storage.rdbms.username=" + EXPECTED_USERNAME,
        "camunda.data.secondary-storage.rdbms.password=" + EXPECTED_PASSWORD,
        "camunda.data.secondary-storage.rdbms.index-prefix=" + EXPECTED_INDEX_PREFIX,
        "camunda.data.secondary-storage.rdbms.flushInterval=" + EXPECTED_FLUSH_INTERVAL,
        "camunda.data.secondary-storage.rdbms.queueSize=" + EXPECTED_QUEUE_SIZE,
        "camunda.data.secondary-storage.rdbms.history.defaultHistoryTTL=" + DEFAULT_HISTORY_TTL,
        "camunda.data.secondary-storage.rdbms.history.defaultBatchOperationHistoryTTL="
            + DEFAULT_BATCH_OPERATION_HISTORY_TTL,
        "camunda.data.secondary-storage.rdbms.history.batchOperationCancelProcessInstanceHistoryTTL="
            + BATCH_OPERATION_CANCEL_PROCESS_INSTANCE_HISTORY_TTL,
        "camunda.data.secondary-storage.rdbms.history.batchOperationMigrateProcessInstanceHistoryTTL="
            + BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE_HISTORY_TTL,
        "camunda.data.secondary-storage.rdbms.history.batchOperationModifyProcessInstanceHistoryTTL="
            + BATCH_OPERATION_MODIFY_PROCESS_INSTANCE_HISTORY_TTL,
        "camunda.data.secondary-storage.rdbms.history.batchOperationResolveIncidentHistoryTTL="
            + BATCH_OPERATION_RESOLVE_INCIDENT_HISTORY_TTL,
        "camunda.data.secondary-storage.rdbms.history.minHistoryCleanupInterval="
            + MIN_HISTORY_CLEANUP_INTERVAL,
        "camunda.data.secondary-storage.rdbms.history.maxHistoryCleanupInterval="
            + MAX_HISTORY_CLEANUP_INTERVAL,
        "camunda.data.secondary-storage.rdbms.history.historyCleanupBatchSize="
            + HISTORY_CLEANUP_BATCH_SIZE,
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
    void testCamundaDataSecondaryStorageRdbmsExporterProperties() {
      final ExporterCfg exporter = brokerBasedProperties.getRdbmsExporter();
      assertThat(exporter).isNotNull();

      final Map<String, Object> args = exporter.getArgs();
      assertThat(args).isNotNull();

      final ExporterConfiguration exporterConfiguration =
          UnifiedConfigurationHelper.argsToRdbmsExporterConfiguration(args);

      assertThat(exporterConfiguration.getFlushInterval())
          .isEqualTo(Duration.parse(EXPECTED_FLUSH_INTERVAL));
      assertThat(exporterConfiguration.getQueueSize()).isEqualTo(EXPECTED_QUEUE_SIZE);
      assertThat(exporterConfiguration.getHistory().getDefaultHistoryTTL())
          .isEqualTo(Duration.parse(DEFAULT_HISTORY_TTL));
      assertThat(exporterConfiguration.getHistory().getDefaultHistoryTTL())
          .isEqualTo(Duration.parse(DEFAULT_HISTORY_TTL));
      assertThat(exporterConfiguration.getHistory().getDefaultBatchOperationHistoryTTL())
          .isEqualTo(Duration.parse(DEFAULT_BATCH_OPERATION_HISTORY_TTL));
      assertThat(
              exporterConfiguration.getHistory().getBatchOperationCancelProcessInstanceHistoryTTL())
          .isEqualTo(Duration.parse(BATCH_OPERATION_CANCEL_PROCESS_INSTANCE_HISTORY_TTL));
      assertThat(
              exporterConfiguration
                  .getHistory()
                  .getBatchOperationMigrateProcessInstanceHistoryTTL())
          .isEqualTo(Duration.parse(BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE_HISTORY_TTL));
      assertThat(
              exporterConfiguration.getHistory().getBatchOperationModifyProcessInstanceHistoryTTL())
          .isEqualTo(Duration.parse(BATCH_OPERATION_MODIFY_PROCESS_INSTANCE_HISTORY_TTL));
      assertThat(exporterConfiguration.getHistory().getBatchOperationResolveIncidentHistoryTTL())
          .isEqualTo(Duration.parse(BATCH_OPERATION_RESOLVE_INCIDENT_HISTORY_TTL));
      assertThat(exporterConfiguration.getHistory().getMinHistoryCleanupInterval())
          .isEqualTo(Duration.parse(MIN_HISTORY_CLEANUP_INTERVAL));
      assertThat(exporterConfiguration.getHistory().getMaxHistoryCleanupInterval())
          .isEqualTo(Duration.parse(MAX_HISTORY_CLEANUP_INTERVAL));
      assertThat(exporterConfiguration.getHistory().getHistoryCleanupBatchSize())
          .isEqualTo(HISTORY_CLEANUP_BATCH_SIZE);
    }

    @Test
    void testCamundaSearchEngineConnectProperties() {
      assertThat(searchEngineConnectProperties.getTypeEnum()).isEqualTo(DatabaseType.RDBMS);
      assertThat(searchEngineConnectProperties.getUrl()).isEqualTo("http://expected-url:4321");
      assertThat(searchEngineConnectProperties.getIndexPrefix()).isEqualTo(EXPECTED_INDEX_PREFIX);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=rdbms",
        "zeebe.broker.exporters.rdbms.class-name=io.camunda.exporter.rdbms.RdbmsExporter"
      })
  class ExporterTestWithoutArgs {
    final BrokerBasedProperties brokerBasedProperties;

    ExporterTestWithoutArgs(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void testSecondaryStorageExporterCanWorkWithoutArgs() {
      final ExporterCfg camundaExporter = brokerBasedProperties.getRdbmsExporter();
      assertThat(camundaExporter).isNotNull();
      final Map<String, Object> args = camundaExporter.getArgs();
      assertThat(args).isNull();
    }
  }
}
