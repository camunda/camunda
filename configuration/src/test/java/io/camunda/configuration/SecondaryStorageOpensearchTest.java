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
  SearchEngineIndexPropertiesOverride.class,
})
public class SecondaryStorageOpensearchTest {
  private static final String EXPECTED_CLUSTER_NAME = "sample-cluster";
  private static final String EXPECTED_INDEX_PREFIX = "sample-index-prefix";

  private static final String EXPECTED_USERNAME = "testUsername";
  private static final String EXPECTED_PASSWORD = "testPassword";

  private static final int EXPECTED_NUMBER_OF_SHARDS = 3;

  private static final boolean EXPECTED_HISTORY_PROCESS_INSTANCE_ENABLED = false;
  private static final String EXPECTED_HISTORY_ELS_ROLLOVER_DATE_FORMAT = "foo";
  private static final String EXPECTED_HISTORY_ROLLOVER_INTERVAL = "5d";
  private static final int EXPECTED_HISTORY_ROLLOVER_BATCH_SIZE = 200;
  private static final String EXPECTED_HISTORY_WAIT_PERIOD_BEFORE_ARCHIVING = "5h";
  private static final int EXPECTED_HISTORY_DELAY_BETWEEN_RUNS = 4000;
  private static final int EXPECTED_HISTORY_MAX_DELAY_BETWEEN_RUNS = 12000;

  private static final boolean EXPECTED_CREATE_SCHEMA = false;

  private static final String EXPECTED_INCIDENT_NOTIFIER_WEBHOOK =
      "https://test-webhook.example.com";
  private static final String EXPECTED_INCIDENT_NOTIFIER_AUTH0_DOMAIN = "test-domain.auth0.com";
  private static final String EXPECTED_INCIDENT_NOTIFIER_AUTH0_PROTOCOL = "https";
  private static final String EXPECTED_INCIDENT_NOTIFIER_M2M_CLIENT_ID = "test-client-id";
  private static final String EXPECTED_INCIDENT_NOTIFIER_M2M_CLIENT_SECRET = "test-client-secret";
  private static final String EXPECTED_INCIDENT_NOTIFIER_M2M_AUDIENCE = "test-audience";

  private static final int EXPECTED_BATCH_OPERATION_CACHE_MAX_SIZE = 5_000;
  private static final int EXPECTED_PROCESS_CACHE_MAX_SIZE = 15_000;
  private static final int EXPECTED_FORM_CACHE_MAX_SIZE = 20_000;

  private static final int EXPECTED_POST_EXPORT_BATCH_SIZE = 200;
  private static final int EXPECTED_POST_EXPORT_DELAY_BETWEEN_RUNS = 3000;
  private static final int EXPECTED_POST_EXPORT_MAX_DELAY_BETWEEN_RUNS = 70000;
  private static final boolean EXPECTED_POST_EXPORT_IGNORE_MISSING_DATA = true;

  private static final boolean EXPECTED_BATCH_OPERATION_EXPORT_ITEMS_ON_CREATION = false;

  private static final int EXPECTED_BULK_DELAY = 10;
  private static final int EXPECTED_BULK_SIZE = 2_000;
  private static final int EXPECTED_BULK_MEMORY_LIMIT = 50;

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=opensearch",
        "camunda.data.secondary-storage.opensearch.url=http://expected-url:4321",
        "camunda.data.secondary-storage.opensearch.username=" + EXPECTED_USERNAME,
        "camunda.data.secondary-storage.opensearch.password=" + EXPECTED_PASSWORD,
        "camunda.data.secondary-storage.opensearch.cluster-name=" + EXPECTED_CLUSTER_NAME,
        "camunda.data.secondary-storage.opensearch.index-prefix=" + EXPECTED_INDEX_PREFIX,
        "camunda.data.secondary-storage.opensearch.number-of-shards=" + EXPECTED_NUMBER_OF_SHARDS,
        "camunda.data.secondary-storage.opensearch.history.process-instance-enabled="
            + EXPECTED_HISTORY_PROCESS_INSTANCE_ENABLED,
        "camunda.data.secondary-storage.opensearch.history.els-rollover-date-format="
            + EXPECTED_HISTORY_ELS_ROLLOVER_DATE_FORMAT,
        "camunda.data.secondary-storage.opensearch.history.rollover-interval="
            + EXPECTED_HISTORY_ROLLOVER_INTERVAL,
        "camunda.data.secondary-storage.opensearch.history.rollover-batch-size="
            + EXPECTED_HISTORY_ROLLOVER_BATCH_SIZE,
        "camunda.data.secondary-storage.opensearch.history.wait-period-before-archiving="
            + EXPECTED_HISTORY_WAIT_PERIOD_BEFORE_ARCHIVING,
        "camunda.data.secondary-storage.opensearch.history.delay-between-runs="
            + EXPECTED_HISTORY_DELAY_BETWEEN_RUNS
            + "ms",
        "camunda.data.secondary-storage.opensearch.history.max-delay-between-runs="
            + EXPECTED_HISTORY_MAX_DELAY_BETWEEN_RUNS
            + "ms",
        "camunda.data.secondary-storage.opensearch.create-schema=" + EXPECTED_CREATE_SCHEMA,
        "camunda.data.secondary-storage.opensearch.incident-notifier.webhook="
            + EXPECTED_INCIDENT_NOTIFIER_WEBHOOK,
        "camunda.data.secondary-storage.opensearch.incident-notifier.auth0-domain="
            + EXPECTED_INCIDENT_NOTIFIER_AUTH0_DOMAIN,
        "camunda.data.secondary-storage.opensearch.incident-notifier.auth0-protocol="
            + EXPECTED_INCIDENT_NOTIFIER_AUTH0_PROTOCOL,
        "camunda.data.secondary-storage.opensearch.incident-notifier.m2m-client-id="
            + EXPECTED_INCIDENT_NOTIFIER_M2M_CLIENT_ID,
        "camunda.data.secondary-storage.opensearch.incident-notifier.m2m-client-secret="
            + EXPECTED_INCIDENT_NOTIFIER_M2M_CLIENT_SECRET,
        "camunda.data.secondary-storage.opensearch.incident-notifier.m2m-audience="
            + EXPECTED_INCIDENT_NOTIFIER_M2M_AUDIENCE,
        "camunda.data.secondary-storage.opensearch.post-export.batch-size="
            + EXPECTED_POST_EXPORT_BATCH_SIZE,
        "camunda.data.secondary-storage.opensearch.post-export.delay-between-runs=3s",
        "camunda.data.secondary-storage.opensearch.post-export.max-delay-between-runs=70s",
        "camunda.data.secondary-storage.opensearch.post-export.ignore-missing-data="
            + EXPECTED_POST_EXPORT_IGNORE_MISSING_DATA,
        "camunda.data.secondary-storage.opensearch.batch-operations.export-items-on-creation="
            + EXPECTED_BATCH_OPERATION_EXPORT_ITEMS_ON_CREATION,
        "camunda.data.secondary-storage.opensearch.bulk.delay=10s",
        "camunda.data.secondary-storage.opensearch.bulk.size=" + EXPECTED_BULK_SIZE,
        "camunda.data.secondary-storage.opensearch.bulk.memory-limit=50MB"
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
      assertThat(exporterConfiguration.getIndex().getNumberOfShards())
          .isEqualTo(EXPECTED_NUMBER_OF_SHARDS);
      assertThat(exporterConfiguration.getHistory().isProcessInstanceEnabled())
          .isEqualTo(EXPECTED_HISTORY_PROCESS_INSTANCE_ENABLED);
      assertThat(exporterConfiguration.getHistory().getElsRolloverDateFormat())
          .isEqualTo(EXPECTED_HISTORY_ELS_ROLLOVER_DATE_FORMAT);
      assertThat(exporterConfiguration.getHistory().getRolloverInterval())
          .isEqualTo(EXPECTED_HISTORY_ROLLOVER_INTERVAL);
      assertThat(exporterConfiguration.getHistory().getRolloverBatchSize())
          .isEqualTo(EXPECTED_HISTORY_ROLLOVER_BATCH_SIZE);
      assertThat(exporterConfiguration.getHistory().getWaitPeriodBeforeArchiving())
          .isEqualTo(EXPECTED_HISTORY_WAIT_PERIOD_BEFORE_ARCHIVING);
      assertThat(exporterConfiguration.getHistory().getDelayBetweenRuns())
          .isEqualTo(EXPECTED_HISTORY_DELAY_BETWEEN_RUNS);
      assertThat(exporterConfiguration.getHistory().getMaxDelayBetweenRuns())
          .isEqualTo(EXPECTED_HISTORY_MAX_DELAY_BETWEEN_RUNS);
      assertThat(exporterConfiguration.isCreateSchema()).isEqualTo(EXPECTED_CREATE_SCHEMA);
      assertThat(exporterConfiguration.getNotifier().getWebhook())
          .isEqualTo(EXPECTED_INCIDENT_NOTIFIER_WEBHOOK);
      assertThat(exporterConfiguration.getNotifier().getAuth0Domain())
          .isEqualTo(EXPECTED_INCIDENT_NOTIFIER_AUTH0_DOMAIN);
      assertThat(exporterConfiguration.getNotifier().getAuth0Protocol())
          .isEqualTo(EXPECTED_INCIDENT_NOTIFIER_AUTH0_PROTOCOL);
      assertThat(exporterConfiguration.getNotifier().getM2mClientId())
          .isEqualTo(EXPECTED_INCIDENT_NOTIFIER_M2M_CLIENT_ID);
      assertThat(exporterConfiguration.getNotifier().getM2mClientSecret())
          .isEqualTo(EXPECTED_INCIDENT_NOTIFIER_M2M_CLIENT_SECRET);
      assertThat(exporterConfiguration.getNotifier().getM2mAudience())
          .isEqualTo(EXPECTED_INCIDENT_NOTIFIER_M2M_AUDIENCE);
      assertThat(exporterConfiguration.getPostExport().getBatchSize())
          .isEqualTo(EXPECTED_POST_EXPORT_BATCH_SIZE);
      assertThat(exporterConfiguration.getPostExport().getDelayBetweenRuns())
          .isEqualTo(EXPECTED_POST_EXPORT_DELAY_BETWEEN_RUNS);
      assertThat(exporterConfiguration.getPostExport().getMaxDelayBetweenRuns())
          .isEqualTo(EXPECTED_POST_EXPORT_MAX_DELAY_BETWEEN_RUNS);
      assertThat(exporterConfiguration.getPostExport().isIgnoreMissingData())
          .isEqualTo(EXPECTED_POST_EXPORT_IGNORE_MISSING_DATA);
      assertThat(exporterConfiguration.getBatchOperation().isExportItemsOnCreation())
          .isEqualTo(EXPECTED_BATCH_OPERATION_EXPORT_ITEMS_ON_CREATION);
      assertThat(exporterConfiguration.getBulk().getDelay()).isEqualTo(EXPECTED_BULK_DELAY);
      assertThat(exporterConfiguration.getBulk().getSize()).isEqualTo(EXPECTED_BULK_SIZE);
      assertThat(exporterConfiguration.getBulk().getMemoryLimit())
          .isEqualTo(EXPECTED_BULK_MEMORY_LIMIT);
    }

    @Test
    void testCamundaSearchEngineConnectProperties() {
      final String expectedType = DatabaseType.Opensearch.name().toLowerCase();

      assertThat(searchEngineConnectProperties.getType().toLowerCase()).isEqualTo(expectedType);
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
        "zeebe.broker.exporters.camundaexporter.args.history.elsRolloverDateFormat="
            + EXPECTED_HISTORY_ELS_ROLLOVER_DATE_FORMAT,
        "zeebe.broker.exporters.camundaexporter.args.history.rolloverInterval="
            + EXPECTED_HISTORY_ROLLOVER_INTERVAL,
        "zeebe.broker.exporters.camundaexporter.args.history.rolloverBatchSize="
            + EXPECTED_HISTORY_ROLLOVER_BATCH_SIZE,
        "zeebe.broker.exporters.camundaexporter.args.history.waitPeriodBeforeArchiving="
            + EXPECTED_HISTORY_WAIT_PERIOD_BEFORE_ARCHIVING,
        "zeebe.broker.exporters.camundaexporter.args.history.delayBetweenRuns="
            + EXPECTED_HISTORY_DELAY_BETWEEN_RUNS
            + "ms",
        "zeebe.broker.exporters.camundaexporter.args.history.maxDelayBetweenRuns="
            + EXPECTED_HISTORY_MAX_DELAY_BETWEEN_RUNS,
        "zeebe.broker.exporters.camundaexporter.args.bulk.delay=10s",
        "zeebe.broker.exporters.camundaexporter.args.bulk.size=" + EXPECTED_BULK_SIZE,
        "zeebe.broker.exporters.camundaexporter.args.bulk.memoryLimit=50MB"
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerBasedProperties;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void testCamundaDataSecondaryStorageCamundaExporterProperties() {
      final ExporterCfg camundaExporter = brokerBasedProperties.getCamundaExporter();
      assertThat(camundaExporter).isNotNull();
      final Map<String, Object> args = camundaExporter.getArgs();
      assertThat(args).isNotNull();

      final ExporterConfiguration exporterConfiguration =
          UnifiedConfigurationHelper.argsToCamundaExporterConfiguration(args);
      assertThat(exporterConfiguration.getHistory().getElsRolloverDateFormat())
          .isEqualTo(EXPECTED_HISTORY_ELS_ROLLOVER_DATE_FORMAT);
      assertThat(exporterConfiguration.getHistory().getRolloverInterval())
          .isEqualTo(EXPECTED_HISTORY_ROLLOVER_INTERVAL);
      assertThat(exporterConfiguration.getHistory().getRolloverBatchSize())
          .isEqualTo(EXPECTED_HISTORY_ROLLOVER_BATCH_SIZE);
      assertThat(exporterConfiguration.getHistory().getWaitPeriodBeforeArchiving())
          .isEqualTo(EXPECTED_HISTORY_WAIT_PERIOD_BEFORE_ARCHIVING);
      assertThat(exporterConfiguration.getHistory().getDelayBetweenRuns())
          .isEqualTo(EXPECTED_HISTORY_DELAY_BETWEEN_RUNS);
      assertThat(exporterConfiguration.getHistory().getMaxDelayBetweenRuns())
          .isEqualTo(EXPECTED_HISTORY_MAX_DELAY_BETWEEN_RUNS);
      assertThat(exporterConfiguration.getBulk().getDelay()).isEqualTo(EXPECTED_BULK_DELAY);
      assertThat(exporterConfiguration.getBulk().getSize()).isEqualTo(EXPECTED_BULK_SIZE);
      assertThat(exporterConfiguration.getBulk().getMemoryLimit())
          .isEqualTo(EXPECTED_BULK_MEMORY_LIMIT);
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

        // number of shards
        "camunda.data.secondary-storage.opensearch.number-of-shards=" + EXPECTED_NUMBER_OF_SHARDS,
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
      assertThat(exporterConfiguration.getIndex().getNumberOfShards())
          .isEqualTo(EXPECTED_NUMBER_OF_SHARDS);
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

    @Test
    void testCamundaSearchEngineIndexProperties() {
      assertThat(searchEngineIndexProperties.getNumberOfShards())
          .isEqualTo(EXPECTED_NUMBER_OF_SHARDS);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=opensearch",
        "camunda.data.secondary-storage.opensearch.url=http://expected-url:4321",
        "camunda.data.secondary-storage.opensearch.batchOperation-cache.max-size="
            + EXPECTED_BATCH_OPERATION_CACHE_MAX_SIZE,
        "camunda.data.secondary-storage.opensearch.process-cache.max-size="
            + EXPECTED_PROCESS_CACHE_MAX_SIZE,
        "camunda.data.secondary-storage.opensearch.form-cache.max-size="
            + EXPECTED_FORM_CACHE_MAX_SIZE,
      })
  class WithCachePropertiesSet {
    final BrokerBasedProperties brokerBasedProperties;

    WithCachePropertiesSet(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void testCachesAreConfiguredCorrectly() {
      final ExporterCfg camundaExporter = brokerBasedProperties.getCamundaExporter();
      assertThat(camundaExporter).isNotNull();
      final Map<String, Object> args = camundaExporter.getArgs();
      assertThat(args).isNotNull();

      final ExporterConfiguration exporterConfiguration =
          UnifiedConfigurationHelper.argsToCamundaExporterConfiguration(args);
      assertThat(exporterConfiguration.getBatchOperationCache().getMaxCacheSize())
          .isEqualTo(EXPECTED_BATCH_OPERATION_CACHE_MAX_SIZE);
      assertThat(exporterConfiguration.getProcessCache().getMaxCacheSize())
          .isEqualTo(EXPECTED_PROCESS_CACHE_MAX_SIZE);
      assertThat(exporterConfiguration.getFormCache().getMaxCacheSize())
          .isEqualTo(EXPECTED_FORM_CACHE_MAX_SIZE);
    }
  }
}
