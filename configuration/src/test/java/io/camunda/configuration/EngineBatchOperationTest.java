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
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.system.configuration.engine.BatchOperationCfg;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
public class EngineBatchOperationTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.processing.engine.batch-operations.scheduler-interval=15s",
        "camunda.processing.engine.batch-operations.chunk-size=500",
        "camunda.processing.engine.batch-operations.query-page-size=200",
        "camunda.processing.engine.batch-operations.query-in-clause-size=50",
        "camunda.processing.engine.batch-operations.query-retry-max=5",
        "camunda.processing.engine.batch-operations.query-retry-initial-delay=2s",
        "camunda.processing.engine.batch-operations.query-retry-max-delay=30s",
        "camunda.processing.engine.batch-operations.query-retry-backoff-factor=3",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetBatchOperation() {
      assertThat(brokerCfg.getExperimental().getEngine().getBatchOperations())
          .returns(Duration.ofSeconds(15), BatchOperationCfg::getSchedulerInterval)
          .returns(500, BatchOperationCfg::getChunkSize)
          .returns(200, BatchOperationCfg::getQueryPageSize)
          .returns(50, BatchOperationCfg::getQueryInClauseSize)
          .returns(5, BatchOperationCfg::getQueryRetryMax)
          .returns(Duration.ofSeconds(2), BatchOperationCfg::getQueryRetryInitialDelay)
          .returns(Duration.ofSeconds(30), BatchOperationCfg::getQueryRetryMaxDelay)
          .returns(3, BatchOperationCfg::getQueryRetryBackoffFactor);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.experimental.engine.batchOperations.schedulerInterval=15s",
        "zeebe.broker.experimental.engine.batchOperations.chunkSize=500",
        "zeebe.broker.experimental.engine.batchOperations.queryPageSize=200",
        "zeebe.broker.experimental.engine.batchOperations.queryInClauseSize=50",
        "zeebe.broker.experimental.engine.batchOperations.queryRetryMax=5",
        "zeebe.broker.experimental.engine.batchOperations.queryRetryInitialDelay=2s",
        "zeebe.broker.experimental.engine.batchOperations.queryRetryMaxDelay=30s",
        "zeebe.broker.experimental.engine.batchOperations.queryRetryBackoffFactor=3",
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetBatchOperationFromLegacy() {
      assertThat(brokerCfg.getExperimental().getEngine().getBatchOperations())
          .returns(Duration.ofSeconds(15), BatchOperationCfg::getSchedulerInterval)
          .returns(500, BatchOperationCfg::getChunkSize)
          .returns(200, BatchOperationCfg::getQueryPageSize)
          .returns(50, BatchOperationCfg::getQueryInClauseSize)
          .returns(5, BatchOperationCfg::getQueryRetryMax)
          .returns(Duration.ofSeconds(2), BatchOperationCfg::getQueryRetryInitialDelay)
          .returns(Duration.ofSeconds(30), BatchOperationCfg::getQueryRetryMaxDelay)
          .returns(3, BatchOperationCfg::getQueryRetryBackoffFactor);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.processing.engine.batch-operations.scheduler-interval=15s",
        "camunda.processing.engine.batch-operations.chunk-size=500",
        "camunda.processing.engine.batch-operations.query-page-size=200",
        "camunda.processing.engine.batch-operations.query-in-clause-size=50",
        "camunda.processing.engine.batch-operations.query-retry-max=5",
        "camunda.processing.engine.batch-operations.query-retry-initial-delay=2s",
        "camunda.processing.engine.batch-operations.query-retry-max-delay=30s",
        "camunda.processing.engine.batch-operations.query-retry-backoff-factor=3",
        // legacy
        "zeebe.broker.experimental.engine.batchOperations.schedulerInterval=150s",
        "zeebe.broker.experimental.engine.batchOperations.chunkSize=5000",
        "zeebe.broker.experimental.engine.batchOperations.queryPageSize=2000",
        "zeebe.broker.experimental.engine.batchOperations.queryInClauseSize=500",
        "zeebe.broker.experimental.engine.batchOperations.queryRetryMax=50",
        "zeebe.broker.experimental.engine.batchOperations.queryRetryInitialDelay=20s",
        "zeebe.broker.experimental.engine.batchOperations.queryRetryMaxDelay=300s",
        "zeebe.broker.experimental.engine.batchOperations.queryRetryBackoffFactor=30",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetBatchOperationFromNew() {
      assertThat(brokerCfg.getExperimental().getEngine().getBatchOperations())
          .returns(Duration.ofSeconds(15), BatchOperationCfg::getSchedulerInterval)
          .returns(500, BatchOperationCfg::getChunkSize)
          .returns(200, BatchOperationCfg::getQueryPageSize)
          .returns(50, BatchOperationCfg::getQueryInClauseSize)
          .returns(5, BatchOperationCfg::getQueryRetryMax)
          .returns(Duration.ofSeconds(2), BatchOperationCfg::getQueryRetryInitialDelay)
          .returns(Duration.ofSeconds(30), BatchOperationCfg::getQueryRetryMaxDelay)
          .returns(3, BatchOperationCfg::getQueryRetryBackoffFactor);
    }
  }
}
