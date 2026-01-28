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
  public static final String FLUSH_INTERVAL = "PT10S";
  public static final int QUEUE_SIZE = 2000;
  public static final int QUEUE_MEMORY_LIMIT = 50;
  private static final String USERNAME = "testUsername";
  private static final String PASSWORD = "testPassword";

  private static final String DEFAULT_HISTORY_TTL = "PT2M";
  private static final String DEFAULT_BATCH_OPERATION_HISTORY_TTL = "PT168H"; // 7 days
  private static final String BATCH_OPERATION_CANCEL_PROCESS_INSTANCE_HISTORY_TTL =
      "PT24H"; // 1 day
  private static final String BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE_HISTORY_TTL =
      "PT240H"; // 10 days
  private static final String BATCH_OPERATION_MODIFY_PROCESS_INSTANCE_HISTORY_TTL =
      "PT168H"; // 7 days
  private static final String BATCH_OPERATION_RESOLVE_INCIDENT_HISTORY_TTL = "PT144H"; // 6 days
  private static final String MIN_HISTORY_CLEANUP_INTERVAL = "PT1S";
  private static final String MAX_HISTORY_CLEANUP_INTERVAL = "PT2H";
  private static final int HISTORY_CLEANUP_BATCH_SIZE = 2000;
  private static final int HISTORY_CLEANUP_PROCESS_INSTANCE_BATCH_SIZE = 1000;
  private static final String HISTORY_USAGE_METRICS_CLEANUP_INTERVAL = "PT48H";
  private static final String HISTORY_USAGE_METRICS_TTL = "PT1H";

  private static final int MAX_PROCESS_CACHE_SIZE = 4711;
  private static final int MAX_BATCH_OPERATIONS_CACHE_SIZE = 4711;

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=rdbms",
        "camunda.data.secondary-storage.rdbms.url=http://expected-url:4321",
        "camunda.data.secondary-storage.rdbms.username=" + USERNAME,
        "camunda.data.secondary-storage.rdbms.password=" + PASSWORD,
        "camunda.data.secondary-storage.rdbms.flushInterval=" + FLUSH_INTERVAL,
        "camunda.data.secondary-storage.rdbms.queueSize=" + QUEUE_SIZE,
        "camunda.data.secondary-storage.rdbms.queueMemoryLimit=" + QUEUE_MEMORY_LIMIT,
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
        "camunda.data.secondary-storage.rdbms.history.historyCleanupProcessInstanceBatchSize="
            + HISTORY_CLEANUP_PROCESS_INSTANCE_BATCH_SIZE,
        "camunda.data.secondary-storage.rdbms.history.usageMetricsCleanup="
            + HISTORY_USAGE_METRICS_CLEANUP_INTERVAL,
        "camunda.data.secondary-storage.rdbms.history.usageMetricsTTL=" + HISTORY_USAGE_METRICS_TTL,
        "camunda.data.secondary-storage.rdbms.processCache.maxSize=" + MAX_PROCESS_CACHE_SIZE,
        "camunda.data.secondary-storage.rdbms.batchOperationCache.maxSize="
            + MAX_BATCH_OPERATIONS_CACHE_SIZE,
        "camunda.data.secondary-storage.rdbms.exportBatchOperationItemsOnCreation=false",
        "camunda.data.secondary-storage.rdbms.batchOperationItemInsertBlockSize=1234",
        "camunda.data.secondary-storage.rdbms.insert-batching.max-audit-log-insert-batch-size=50",
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
          .isEqualTo(Duration.parse(FLUSH_INTERVAL));
      assertThat(exporterConfiguration.getQueueSize()).isEqualTo(QUEUE_SIZE);
      assertThat(exporterConfiguration.getQueueMemoryLimit()).isEqualTo(QUEUE_MEMORY_LIMIT);
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
      assertThat(exporterConfiguration.getHistory().getHistoryCleanupProcessInstanceBatchSize())
          .isEqualTo(HISTORY_CLEANUP_PROCESS_INSTANCE_BATCH_SIZE);
      assertThat(exporterConfiguration.getHistory().getUsageMetricsCleanup())
          .isEqualTo(Duration.parse(HISTORY_USAGE_METRICS_CLEANUP_INTERVAL));
      assertThat(exporterConfiguration.getHistory().getUsageMetricsTTL())
          .isEqualTo(Duration.parse(HISTORY_USAGE_METRICS_TTL));

      if (exporterConfiguration.getProcessCache() != null) {
        assertThat(exporterConfiguration.getProcessCache().getMaxSize())
            .isEqualTo(MAX_PROCESS_CACHE_SIZE);
      }

      if (exporterConfiguration.getBatchOperationCache() != null) {
        assertThat(exporterConfiguration.getBatchOperationCache().getMaxSize())
            .isEqualTo(MAX_BATCH_OPERATIONS_CACHE_SIZE);
      }

      assertThat(exporterConfiguration.isExportBatchOperationItemsOnCreation()).isFalse();

      assertThat(exporterConfiguration.getBatchOperationItemInsertBlockSize()).isEqualTo(1234);
      assertThat(exporterConfiguration.getInsertBatching().getMaxAuditLogInsertBatchSize())
          .isEqualTo(50);
    }

    @Test
    void testCamundaSearchEngineConnectProperties() {
      assertThat(searchEngineConnectProperties.getTypeEnum()).isEqualTo(DatabaseType.RDBMS);
      assertThat(searchEngineConnectProperties.getUrl()).isEqualTo("http://expected-url:4321");
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
    void testSecondaryStorageExporterWithoutArgsGetDefaults() {
      final ExporterCfg exporter = brokerBasedProperties.getRdbmsExporter();
      assertThat(exporter).isNotNull();
      final Map<String, Object> args = exporter.getArgs();
      assertThat(args.get("queueSize")).isEqualTo(1000);
      assertThat(args.get("queueMemoryLimit")).isEqualTo(20);
      assertThat(args.get("flushInterval")).isEqualTo(Duration.ofMillis(500));
      assertThat(args.get("exportBatchOperationItemsOnCreation")).isEqualTo(true);
      assertThat(args.get("batchOperationItemInsertBlockSize")).isEqualTo(10000);
    }
  }
}
