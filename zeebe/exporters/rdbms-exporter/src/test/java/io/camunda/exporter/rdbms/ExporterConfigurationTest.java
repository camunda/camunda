/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setFlushInterval(Duration.ofMillis(-1000));
    configuration.setQueueSize(-1000);
    configuration.setDefaultHistoryTTL(Duration.ofMillis(-1000));
    configuration.setMinHistoryCleanupInterval(Duration.ofMillis(-1000));
    configuration.setMaxHistoryCleanupInterval(Duration.ofMillis(-2000));
    configuration.setHistoryCleanupBatchSize(-1000);
    configuration.setBatchOperationItemInsertBlockSize(-1000);
    configuration.getBatchOperationCache().setMaxSize(-1000);
    configuration.getProcessCache().setMaxSize(-1000);

    // when
    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("flushInterval must be")
        .hasMessageContaining("queueSize must be")
        .hasMessageContaining("defaultHistoryTTL must be")
        .hasMessageContaining("minHistoryCleanupInterval must be")
        .hasMessageContaining("maxHistoryCleanupInterval must be a positive duration")
        .hasMessageContaining(
            "maxHistoryCleanupInterval must be greater than minHistoryCleanupInterval")
        .hasMessageContaining("historyCleanupBatchSize must be")
        .hasMessageContaining("batchOperationItemInsertBlockSize must be")
        .hasMessageContaining("batchOperationCache.maxSize must be")
        .hasMessageContaining("processCache.maxSize must be");
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
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setDefaultHistoryTTL(Duration.ofMillis(-1000));

    assertThatThrownBy(configuration::validate).hasMessageContaining("defaultHistoryTTL must be");
  }

  @Test
  public void shouldFailWithDefaultHistoryTTL() {
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setDefaultHistoryTTL(Duration.ZERO);

    assertThatThrownBy(configuration::validate).hasMessageContaining("defaultHistoryTTL must be");
  }

  @Test
  public void shouldFailWithNegativeMinHistoryCleanupInterval() {
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setMinHistoryCleanupInterval(Duration.ofMillis(-1000));

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("minHistoryCleanupInterval must be");
  }

  @Test
  public void shouldFailWithZeroMinHistoryCleanupInterval() {
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setMinHistoryCleanupInterval(Duration.ZERO);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("minHistoryCleanupInterval must be");
  }

  @Test
  public void shouldFailWithNegativeMaxHistoryCleanupInterval() {
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setMaxHistoryCleanupInterval(Duration.ofMillis(-1000));

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("maxHistoryCleanupInterval must be a positive duration");
  }

  @Test
  public void shouldFailWithZeroMaxHistoryCleanupInterval() {
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setMaxHistoryCleanupInterval(Duration.ZERO);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("maxHistoryCleanupInterval must be a positive duration");
  }

  @Test
  public void shouldFailWithMaxHistoryCleanupIntervalLesserThanMin() {
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setMaxHistoryCleanupInterval(Duration.ofSeconds(1));
    configuration.setMinHistoryCleanupInterval(Duration.ofSeconds(2));

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining(
            "maxHistoryCleanupInterval must be greater than minHistoryCleanupInterval");
  }

  @Test
  public void shouldFailWithNegativeHistoryCleanupBatchSize() {
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setHistoryCleanupBatchSize(-1);

    assertThatThrownBy(configuration::validate)
        .hasMessageContaining("historyCleanupBatchSize must be");
  }

  @Test
  public void shouldFailWithZeroHistoryCleanupBatchSize() {
    final ExporterConfiguration configuration = new ExporterConfiguration();
    configuration.setHistoryCleanupBatchSize(0);

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
}
