/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ExporterConfigurationTest {

  @Test
  public void shouldBeOkWithDefaults() {
    // given
    final ExporterConfiguration configuration = new ExporterConfiguration();

    // when
    configuration.validate();

    // then
    // no error
  }

  @Test
  public void shouldFailWithAllErrors() {
    final ExporterConfiguration.HistoryConfiguration historyConfiguration =
        new ExporterConfiguration.HistoryConfiguration();
    historyConfiguration.setDefaultHistoryTTL(Duration.ofMillis(-1000));
    historyConfiguration.setDefaultBatchOperationHistoryTTL(Duration.ofMillis(-1000));
    historyConfiguration.setMinHistoryCleanupInterval(Duration.ofMillis(-1000));
    historyConfiguration.setMaxHistoryCleanupInterval(Duration.ofMillis(-2000));
    historyConfiguration.setUsageMetricsCleanup(Duration.ofMillis(-2000));
    historyConfiguration.setUsageMetricsTTL(Duration.ofMillis(-2000));
    historyConfiguration.setHistoryCleanupBatchSize(-1000);
    historyConfiguration.setBatchOperationCancelProcessInstanceHistoryTTL(Duration.ofMillis(-1000));
    historyConfiguration.setBatchOperationMigrateProcessInstanceHistoryTTL(
        Duration.ofMillis(-1000));
    historyConfiguration.setBatchOperationModifyProcessInstanceHistoryTTL(Duration.ofMillis(-1000));
    historyConfiguration.setBatchOperationResolveIncidentHistoryTTL(Duration.ofMillis(-1000));

    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setFlushInterval(Duration.ofMillis(-1000));
    configuration.setQueueSize(-1000);
    configuration.setBatchOperationItemInsertBlockSize(-1000);
    configuration.getBatchOperationCache().setMaxSize(-1000);
    configuration.getProcessCache().setMaxSize(-1000);
    configuration.getInsertBatching().setMaxVariableInsertBatchSize(-1000);
    configuration.getInsertBatching().setMaxAuditLogInsertBatchSize(-1000);
    configuration.setHistory(historyConfiguration);

    // when
    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("flushInterval must be")
        .hasMessageContaining("queueSize must be")
        .hasMessageContaining("defaultHistoryTTL must be")
        .hasMessageContaining("defaultBatchOperationHistoryTTL must be")
        .hasMessageContaining("batchOperationCancelProcessInstanceHistoryTTL must be")
        .hasMessageContaining("batchOperationMigrateProcessInstanceHistoryTTL must be")
        .hasMessageContaining("batchOperationModifyProcessInstanceHistoryTTL must be")
        .hasMessageContaining("batchOperationResolveIncidentHistoryTTL must be")
        .hasMessageContaining("minHistoryCleanupInterval must be")
        .hasMessageContaining("maxHistoryCleanupInterval must be a positive duration")
        .hasMessageContaining("usageMetricsCleanup must be a positive duration")
        .hasMessageContaining("usageMetricsTTL must be a positive duration")
        .hasMessageContaining(
            "maxHistoryCleanupInterval must be greater than minHistoryCleanupInterval")
        .hasMessageContaining("historyCleanupBatchSize must be")
        .hasMessageContaining("batchOperationItemInsertBlockSize must be")
        .hasMessageContaining("batchOperationCache.maxSize must be")
        .hasMessageContaining("processCache.maxSize must be")
        .hasMessageContaining("insertBatching.maxAuditLogInsertBatchSize must be")
        .hasMessageContaining("insertBatching.maxVariableInsertBatchSize must be");
  }

  @Test
  public void shouldFailWithNegativeFlushInterval() {
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setFlushInterval(Duration.ofMillis(-1000));

    // when
    assertThatThrownBy(configuration::validate).hasMessageContaining("flushInterval must be");
  }

  @Test
  public void shouldFailWithNegativeQueueSize() {
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setQueueSize(-1000);

    assertThatThrownBy(configuration::validate).hasMessageContaining("queueSize must be");
  }

  @Test
  public void shouldFailWithNegativeDefaultHistoryTTL() {
    final ExporterConfiguration.HistoryConfiguration historyConfiguration =
        new ExporterConfiguration.HistoryConfiguration();
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setHistory(historyConfiguration);

    historyConfiguration.setDefaultHistoryTTL(Duration.ofMillis(-1000));

    assertThatThrownBy(configuration::validate).hasMessageContaining("defaultHistoryTTL must be");
  }

  @Test
  public void shouldFailWithDefaultHistoryTTL() {
    final ExporterConfiguration.HistoryConfiguration historyConfiguration =
        new ExporterConfiguration.HistoryConfiguration();
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setHistory(historyConfiguration);

    historyConfiguration.setDefaultHistoryTTL(Duration.ZERO);

    assertThatThrownBy(configuration::validate).hasMessageContaining("defaultHistoryTTL must be");
  }

  @Test
  public void shouldFailWithBatchOperationCancelProcessInstanceHistoryTTL() {
    final ExporterConfiguration.HistoryConfiguration historyConfiguration =
        new ExporterConfiguration.HistoryConfiguration();
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setHistory(historyConfiguration);

    historyConfiguration.setBatchOperationCancelProcessInstanceHistoryTTL(Duration.ZERO);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("batchOperationCancelProcessInstanceHistoryTTL must be");
  }

  @Test
  public void shouldFailWithBatchOperationMigrateProcessInstanceHistoryTTL() {
    final ExporterConfiguration.HistoryConfiguration historyConfiguration =
        new ExporterConfiguration.HistoryConfiguration();
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setHistory(historyConfiguration);

    historyConfiguration.setBatchOperationMigrateProcessInstanceHistoryTTL(Duration.ZERO);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("batchOperationMigrateProcessInstanceHistoryTTL must be");
  }

  @Test
  public void shouldFailWithBatchOperationModifyProcessInstanceHistoryTTL() {
    final ExporterConfiguration.HistoryConfiguration historyConfiguration =
        new ExporterConfiguration.HistoryConfiguration();
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setHistory(historyConfiguration);

    historyConfiguration.setBatchOperationModifyProcessInstanceHistoryTTL(Duration.ZERO);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("batchOperationModifyProcessInstanceHistoryTTL must be");
  }

  @Test
  public void shouldFailWithBatchOperationResolveIncidentHistoryTTL() {
    final ExporterConfiguration.HistoryConfiguration historyConfiguration =
        new ExporterConfiguration.HistoryConfiguration();
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setHistory(historyConfiguration);

    historyConfiguration.setBatchOperationResolveIncidentHistoryTTL(Duration.ZERO);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("batchOperationResolveIncidentHistoryTTL must be");
  }

  @Test
  public void shouldFailWithNegativeMinHistoryCleanupInterval() {
    final ExporterConfiguration.HistoryConfiguration historyConfiguration =
        new ExporterConfiguration.HistoryConfiguration();
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setHistory(historyConfiguration);

    historyConfiguration.setMinHistoryCleanupInterval(Duration.ofMillis(-1000));

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("minHistoryCleanupInterval must be");
  }

  @Test
  public void shouldFailWithZeroMinHistoryCleanupInterval() {
    final ExporterConfiguration.HistoryConfiguration historyConfiguration =
        new ExporterConfiguration.HistoryConfiguration();
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setHistory(historyConfiguration);

    historyConfiguration.setMinHistoryCleanupInterval(Duration.ZERO);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("minHistoryCleanupInterval must be");
  }

  @Test
  public void shouldFailWithNegativeMaxHistoryCleanupInterval() {
    final ExporterConfiguration.HistoryConfiguration historyConfiguration =
        new ExporterConfiguration.HistoryConfiguration();
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setHistory(historyConfiguration);

    historyConfiguration.setMaxHistoryCleanupInterval(Duration.ofMillis(-1000));

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("maxHistoryCleanupInterval must be a positive duration");
  }

  @Test
  public void shouldFailWithNegativeUsageMetricsCleanup() {
    final ExporterConfiguration.HistoryConfiguration historyConfiguration =
        new ExporterConfiguration.HistoryConfiguration();
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setHistory(historyConfiguration);

    historyConfiguration.setUsageMetricsCleanup(Duration.ofMillis(-1000));

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("usageMetricsCleanup must be a positive duration");
  }

  @Test
  public void shouldFailWithNegativeUsageMetricsTTL() {
    final ExporterConfiguration.HistoryConfiguration historyConfiguration =
        new ExporterConfiguration.HistoryConfiguration();
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setHistory(historyConfiguration);

    historyConfiguration.setUsageMetricsTTL(Duration.ofMillis(-1000));

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("usageMetricsTTL must be a positive duration");
  }

  @Test
  public void shouldFailWithZeroMaxHistoryCleanupInterval() {
    final ExporterConfiguration.HistoryConfiguration historyConfiguration =
        new ExporterConfiguration.HistoryConfiguration();
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setHistory(historyConfiguration);

    historyConfiguration.setMaxHistoryCleanupInterval(Duration.ZERO);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("maxHistoryCleanupInterval must be a positive duration");
  }

  @Test
  public void shouldFailWithZeroUsageMetricsCleanup() {
    final ExporterConfiguration.HistoryConfiguration historyConfiguration =
        new ExporterConfiguration.HistoryConfiguration();
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setHistory(historyConfiguration);

    historyConfiguration.setUsageMetricsCleanup(Duration.ZERO);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("usageMetricsCleanup must be a positive duration");
  }

  @Test
  public void shouldFailWithZeroUsageMetricsTTL() {
    final ExporterConfiguration.HistoryConfiguration historyConfiguration =
        new ExporterConfiguration.HistoryConfiguration();
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setHistory(historyConfiguration);

    historyConfiguration.setUsageMetricsTTL(Duration.ZERO);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("usageMetricsTTL must be a positive duration");
  }

  @Test
  public void shouldFailWithMaxHistoryCleanupIntervalLesserThanMin() {
    final ExporterConfiguration.HistoryConfiguration historyConfiguration =
        new ExporterConfiguration.HistoryConfiguration();
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setHistory(historyConfiguration);

    historyConfiguration.setMaxHistoryCleanupInterval(Duration.ofSeconds(1));
    historyConfiguration.setMinHistoryCleanupInterval(Duration.ofSeconds(2));

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining(
            "maxHistoryCleanupInterval must be greater than minHistoryCleanupInterval");
  }

  @Test
  public void shouldFailWithNegativeHistoryCleanupBatchSize() {
    final ExporterConfiguration.HistoryConfiguration historyConfiguration =
        new ExporterConfiguration.HistoryConfiguration();
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setHistory(historyConfiguration);

    historyConfiguration.setHistoryCleanupBatchSize(-1);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("historyCleanupBatchSize must be");
  }

  @Test
  public void shouldFailWithZeroHistoryCleanupBatchSize() {
    final ExporterConfiguration.HistoryConfiguration historyConfiguration =
        new ExporterConfiguration.HistoryConfiguration();
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setHistory(historyConfiguration);

    historyConfiguration.setHistoryCleanupBatchSize(0);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("historyCleanupBatchSize must be");
  }

  @Test
  public void shouldFailWithNegativeBatchOperationItemInsertBlockSize() {
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setBatchOperationItemInsertBlockSize(-1);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("batchOperationItemInsertBlockSize must be");
  }

  @Test
  public void shouldFailWithZeroBatchOperationItemInsertBlockSize() {
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setBatchOperationItemInsertBlockSize(0);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("batchOperationItemInsertBlockSize must be");
  }

  @Test
  public void shouldFailWithNegativeBatchOperationCacheSize() {
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.getBatchOperationCache().setMaxSize(-1);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("batchOperationCache.maxSize must be");
  }

  @Test
  public void shouldFailWithZeroBatchOperationCacheSize() {
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.getBatchOperationCache().setMaxSize(0);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("batchOperationCache.maxSize must be");
  }

  @Test
  public void shouldFailWithNegativeProcessCacheSize() {
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.getProcessCache().setMaxSize(-1);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("processCache.maxSize must be");
  }

  @Test
  public void shouldFailWithZeroProcessCacheSize() {
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.getProcessCache().setMaxSize(0);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("processCache.maxSize must be");
  }

  @Test
  public void shouldFailWithZeroVariableInsertBatchSize() {
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.getInsertBatching().setMaxVariableInsertBatchSize(0);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("insertBatching.maxVariableInsertBatchSize must be");
  }

  @Test
  public void shouldFailWithNegativeVariableInsertBatchSize() {
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.getInsertBatching().setMaxVariableInsertBatchSize(-1);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("insertBatching.maxVariableInsertBatchSize must be");
  }

  @Test
  public void shouldFailWithZeroAuditLogInsertBatchSize() {
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.getInsertBatching().setMaxAuditLogInsertBatchSize(0);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("insertBatching.maxAuditLogInsertBatchSize must be");
  }

  @Test
  public void shouldFailWithNegativeAuditLogInsertBatchSize() {
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.getInsertBatching().setMaxAuditLogInsertBatchSize(-1);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("insertBatching.maxAuditLogInsertBatchSize must be");
  }

  @Test
  public void shouldSetBatchSizesToOneWhenVendorDoesNotSupportInsertBatching() {
    // given
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.getInsertBatching().setMaxVariableInsertBatchSize(25);
    configuration.getInsertBatching().setMaxAuditLogInsertBatchSize(50);
    configuration.getInsertBatching().setMaxJobInsertBatchSize(25);
    configuration.getInsertBatching().setMaxFlowNodeInsertBatchSize(25);

    final VendorDatabaseProperties vendorProps = mock(VendorDatabaseProperties.class);
    when(vendorProps.supportsInsertBatching()).thenReturn(false);

    // when
    final var writerConfig = configuration.createRdbmsWriterConfig(1, vendorProps);

    // then
    assertThat(writerConfig.insertBatchingConfig().variableInsertBatchSize()).isEqualTo(1);
    assertThat(writerConfig.insertBatchingConfig().auditLogInsertBatchSize()).isEqualTo(1);
    assertThat(writerConfig.insertBatchingConfig().jobInsertBatchSize()).isEqualTo(1);
    assertThat(writerConfig.insertBatchingConfig().flowNodeInsertBatchSize()).isEqualTo(1);
  }

  @Test
  public void shouldUseBatchSizesFromConfigWhenVendorSupportsInsertBatching() {
    // given
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.getInsertBatching().setMaxVariableInsertBatchSize(25);
    configuration.getInsertBatching().setMaxAuditLogInsertBatchSize(50);
    configuration.getInsertBatching().setMaxJobInsertBatchSize(30);
    configuration.getInsertBatching().setMaxFlowNodeInsertBatchSize(35);

    final VendorDatabaseProperties vendorProps = mock(VendorDatabaseProperties.class);
    when(vendorProps.supportsInsertBatching()).thenReturn(true);

    // when
    final var writerConfig = configuration.createRdbmsWriterConfig(1, vendorProps);

    // then
    assertThat(writerConfig.insertBatchingConfig().variableInsertBatchSize()).isEqualTo(25);
    assertThat(writerConfig.insertBatchingConfig().auditLogInsertBatchSize()).isEqualTo(50);
    assertThat(writerConfig.insertBatchingConfig().jobInsertBatchSize()).isEqualTo(30);
    assertThat(writerConfig.insertBatchingConfig().flowNodeInsertBatchSize()).isEqualTo(35);
  }
}
