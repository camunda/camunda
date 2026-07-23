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
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.beans.SearchEngineConnectProperties;
import io.camunda.exporter.rdbms.ExporterConfiguration;
import io.camunda.operate.OperatePropertiesOverride;
import io.camunda.operate.property.OperateProperties;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.tasklist.TasklistPropertiesOverride;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.broker.system.configuration.engine.ValidatorsCfg;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.test.appender.ListAppender;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
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
  private static final String HISTORY_DECISION_INSTANCE_TTL = "PT2M";
  private static final String HISTORY_USAGE_METRICS_TTL = "PT1H";
  private static final String ASYNC_REPLICATION_POLLING_INTERVAL = "PT30S";
  private static final String ASYNC_REPLICATION_MAX_LAG = "PT5M";
  private static final String ASYNC_REPLICATION_QUEUE_DEBOUNCE_TIME = "PT0.25S";
  private static final int ASYNC_REPLICATION_QUEUE_CAPACITY = 4096;

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
        "camunda.data.secondary-storage.rdbms.history.decision-instance-ttl="
            + HISTORY_DECISION_INSTANCE_TTL,
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
        "camunda.data.secondary-storage.rdbms.async-replication.enabled=true",
        "camunda.data.secondary-storage.rdbms.async-replication.type=LOG_SEQ",
        "camunda.data.secondary-storage.rdbms.async-replication.polling-interval="
            + ASYNC_REPLICATION_POLLING_INTERVAL,
        "camunda.data.secondary-storage.rdbms.async-replication.min-sync-replicas=2",
        "camunda.data.secondary-storage.rdbms.async-replication.max-lag="
            + ASYNC_REPLICATION_MAX_LAG,
        "camunda.data.secondary-storage.rdbms.async-replication.pause-on-max-lag-exceeded=true",
        "camunda.data.secondary-storage.rdbms.async-replication.queue-debounce-time="
            + ASYNC_REPLICATION_QUEUE_DEBOUNCE_TIME,
        "camunda.data.secondary-storage.rdbms.async-replication.queue-capacity="
            + ASYNC_REPLICATION_QUEUE_CAPACITY,
        "camunda.data.secondary-storage.rdbms.max-varchar-field-length=200",
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
      assertThat(exporterConfiguration.getHistory().getDecisionInstanceTTL())
          .isEqualTo(Duration.parse(HISTORY_DECISION_INSTANCE_TTL));

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
      assertThat(exporterConfiguration.getAsyncReplication().isEnabled()).isTrue();
      assertThat(exporterConfiguration.getAsyncReplication().getType())
          .isEqualTo(ExporterConfiguration.ReplicationConfiguration.ReplicationType.LOG_SEQ);
      assertThat(exporterConfiguration.getAsyncReplication().getPollingInterval())
          .isEqualTo(Duration.parse(ASYNC_REPLICATION_POLLING_INTERVAL));
      assertThat(exporterConfiguration.getAsyncReplication().getMinSyncReplicas()).isEqualTo(2);
      assertThat(exporterConfiguration.getAsyncReplication().getMaxLag())
          .isEqualTo(Duration.parse(ASYNC_REPLICATION_MAX_LAG));
      assertThat(exporterConfiguration.getAsyncReplication().isPauseOnMaxLagExceeded()).isTrue();
      assertThat(exporterConfiguration.getAsyncReplication().getQueueDebounceTime())
          .isEqualTo(Duration.parse(ASYNC_REPLICATION_QUEUE_DEBOUNCE_TIME));
      assertThat(exporterConfiguration.getAsyncReplication().getQueueCapacity())
          .isEqualTo(ASYNC_REPLICATION_QUEUE_CAPACITY);
    }

    @Test
    void testEngineValidatorsDefaults() {
      final ValidatorsCfg validators =
          brokerBasedProperties.getExperimental().getEngine().getValidators();
      assertThat(validators.getMaxIdFieldLength()).isEqualTo(200);
      assertThat(validators.getMaxNameFieldLength()).isEqualTo(200);
      assertThat(validators.getMaxWorkerTypeLength()).isEqualTo(200);
    }

    @Test
    void testCamundaSearchEngineConnectProperties() {
      assertThat(searchEngineConnectProperties.getTypeEnum()).isEqualTo(DatabaseType.RDBMS);
      assertThat(searchEngineConnectProperties.getUrl()).isEqualTo("http://expected-url:4321");
    }
  }

  /**
   * The {@code rdbms} exporter is reserved and provisioned internally; it must be configured
   * through {@code camunda.data.secondary-storage.rdbms.*}. Declaring it through the generic
   * exporter properties — unified or legacy — is ignored (the reserved config wins) and a warning
   * is logged rather than failing startup (see #57804).
   */
  @Nested
  class ReservedExporterIgnored {
    private static final String RESERVED_WARNING = "'rdbms' exporter is reserved";

    private ApplicationContextRunner runnerWith(final String... properties) {
      return new ApplicationContextRunner()
          .withUserConfiguration(
              UnifiedConfiguration.class,
              UnifiedConfigurationHelper.class,
              BrokerBasedPropertiesOverride.class)
          .withPropertyValues("spring.profiles.active=broker")
          .withPropertyValues("camunda.data.secondary-storage.type=rdbms")
          .withPropertyValues(properties);
    }

    @Test
    void shouldIgnoreUnifiedRdbmsExporterConfig() {
      try (final LogCapturer logs =
          new LogCapturer(BrokerBasedPropertiesOverride.class.getName())) {
        runnerWith("camunda.data.exporters.rdbms.args.queue-size=0")
            .run(
                context -> {
                  assertThat(context).hasNotFailed();
                  final ExporterCfg exporter =
                      context.getBean(BrokerBasedProperties.class).getRdbmsExporter();
                  assertThat(exporter).isNotNull();
                  assertThat(exporter.getClassName())
                      .isEqualTo("io.camunda.exporter.rdbms.RdbmsExporter");
                  // the ignored generic queue-size=0 must not apply; the reserved config keeps the
                  // default queue size
                  assertThat(
                          UnifiedConfigurationHelper.argsToRdbmsExporterConfiguration(
                                  exporter.getArgs())
                              .getQueueSize())
                      .isEqualTo(1000);
                  // the ignored config must be surfaced to the user as a warning, not silently
                  assertThat(logs.contains(RESERVED_WARNING)).isTrue();
                });
      }
    }

    @Test
    void shouldIgnoreLegacyRdbmsExporterConfig() {
      try (final LogCapturer logs =
          new LogCapturer(BrokerBasedPropertiesOverride.class.getName())) {
        runnerWith(
                "zeebe.broker.exporters.rdbms.class-name=io.camunda.exporter.rdbms.RdbmsExporter")
            .run(
                context -> {
                  assertThat(context).hasNotFailed();
                  final ExporterCfg exporter =
                      context.getBean(BrokerBasedProperties.class).getRdbmsExporter();
                  assertThat(exporter).isNotNull();
                  assertThat(exporter.getClassName())
                      .isEqualTo("io.camunda.exporter.rdbms.RdbmsExporter");
                  assertThat(logs.contains(RESERVED_WARNING)).isTrue();
                });
      }
    }

    @Test
    void shouldIgnoreRdbmsExporterConfiguredPerPhysicalTenant() {
      // the per-tenant path goes through BrokerBasedPropertiesOverride.convert (invoked per tenant
      // by SystemContextLoader), which runs the same reserved-exporter handling
      final MockEnvironment environment = new MockEnvironment();
      environment.setProperty("camunda.data.secondary-storage.type", "rdbms");
      environment.setProperty("camunda.data.exporters.rdbms.args.queue-size", "0");
      final Camunda perTenant = new Camunda();
      Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(perTenant));

      final BrokerBasedProperties props;
      final boolean warned;
      try (final LogCapturer logs =
          new LogCapturer(BrokerBasedPropertiesOverride.class.getName())) {
        props = BrokerBasedPropertiesOverride.convert(perTenant);
        warned = logs.contains(RESERVED_WARNING);
      }

      final ExporterCfg exporter = props.getRdbmsExporter();
      assertThat(exporter).isNotNull();
      assertThat(exporter.getClassName()).isEqualTo("io.camunda.exporter.rdbms.RdbmsExporter");
      // the ignored generic queue-size=0 must not apply; the reserved config keeps the default
      assertThat(
              UnifiedConfigurationHelper.argsToRdbmsExporterConfiguration(exporter.getArgs())
                  .getQueueSize())
          .isEqualTo(1000);
      // the ignored config must be surfaced to the user as a warning, not silently
      assertThat(warned).isTrue();
    }
  }

  /**
   * Captures log events emitted for a given logger via a log4j2 {@link ListAppender}, so tests can
   * assert on messages that are logged instead of thrown (see {@link ReservedExporterIgnored}).
   */
  private static final class LogCapturer implements AutoCloseable {
    private final ListAppender appender;
    private final String loggerName;

    private LogCapturer(final String loggerName) {
      this.loggerName = loggerName;
      appender = new ListAppender("TestAppender");
      appender.start();
      // Ensure a dedicated logger config exists at a level that lets WARN through; without a log4j2
      // config file the default root level is ERROR, which would drop the warning before the
      // appender sees it.
      Configurator.setLevel(loggerName, Level.WARN);
      final LoggerContext context = (LoggerContext) LogManager.getContext(false);
      context
          .getConfiguration()
          .getLoggerConfig(loggerName)
          .addAppender(appender, Level.WARN, null);
      context.updateLoggers();
    }

    private boolean contains(final String message) {
      // the appender has no layout, so formatted strings live in getEvents(), not getMessages()
      return appender.getEvents().stream()
          .anyMatch(e -> e.getMessage().getFormattedMessage().contains(message));
    }

    @Override
    public void close() {
      final LoggerContext context = (LoggerContext) LogManager.getContext(false);
      context.getConfiguration().getLoggerConfig(loggerName).removeAppender("TestAppender");
      context.updateLoggers();
      appender.stop();
      appender.clear();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=rdbms",
        "camunda.data.secondary-storage.rdbms.urls=jdbc:postgresql://node1:5432/camunda,jdbc:postgresql://node2:5432/camunda",
        "camunda.data.secondary-storage.rdbms.username=" + USERNAME,
        "camunda.data.secondary-storage.rdbms.password=" + PASSWORD,
      })
  class WithUrlsConfigured {
    private static final List<String> EXPECTED_URLS =
        List.of("jdbc:postgresql://node1:5432/camunda", "jdbc:postgresql://node2:5432/camunda");

    final SearchEngineConnectProperties searchEngineConnectProperties;

    WithUrlsConfigured(
        @Autowired final SearchEngineConnectProperties searchEngineConnectProperties) {
      this.searchEngineConnectProperties = searchEngineConnectProperties;
    }

    @Test
    void testUrlsPropagatedToSearchEngineConnectProperties() {
      assertThat(searchEngineConnectProperties.getUrls()).isEqualTo(EXPECTED_URLS);
      assertThat(searchEngineConnectProperties.getUsername()).isEqualTo(USERNAME);
      assertThat(searchEngineConnectProperties.getPassword()).isEqualTo(PASSWORD);
    }
  }
}
