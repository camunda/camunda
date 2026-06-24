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
public class JobMetricsConfigTest {

  private static final Duration EXPECTED_EXPORT_INTERVAL = Duration.ofMinutes(10);
  private static final int EXPECTED_MAX_WORKER_NAME_LENGTH = 50;
  private static final int EXPECTED_MAX_JOB_TYPE_LENGTH = 75;
  private static final int EXPECTED_MAX_TENANT_ID_LENGTH = 20;
  private static final int EXPECTED_MAX_UNIQUE_KEYS = 5000;
  private static final boolean EXPECTED_ENABLED = false;

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.monitoring.metrics.job-metrics.export-interval=10m",
        "camunda.monitoring.metrics.job-metrics.max-worker-name-length="
            + EXPECTED_MAX_WORKER_NAME_LENGTH,
        "camunda.monitoring.metrics.job-metrics.max-job-type-length="
            + EXPECTED_MAX_JOB_TYPE_LENGTH,
        "camunda.monitoring.metrics.job-metrics.max-tenant-id-length="
            + EXPECTED_MAX_TENANT_ID_LENGTH,
        "camunda.monitoring.metrics.job-metrics.max-unique-keys=" + EXPECTED_MAX_UNIQUE_KEYS,
        "camunda.monitoring.metrics.job-metrics.enabled=" + EXPECTED_ENABLED,
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetJobMetrics() {
      final var jobMetrics = brokerCfg.getExperimental().getEngine().getJobMetrics();
      assertThat(jobMetrics.getExportInterval()).isEqualTo(EXPECTED_EXPORT_INTERVAL);
      assertThat(jobMetrics.getMaxWorkerNameLength()).isEqualTo(EXPECTED_MAX_WORKER_NAME_LENGTH);
      assertThat(jobMetrics.getMaxJobTypeLength()).isEqualTo(EXPECTED_MAX_JOB_TYPE_LENGTH);
      assertThat(jobMetrics.getMaxTenantIdLength()).isEqualTo(EXPECTED_MAX_TENANT_ID_LENGTH);
      assertThat(jobMetrics.getMaxUniqueKeys()).isEqualTo(EXPECTED_MAX_UNIQUE_KEYS);
      assertThat(jobMetrics.isEnabled()).isEqualTo(EXPECTED_ENABLED);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.experimental.engine.jobMetrics.exportInterval=10m",
        "zeebe.broker.experimental.engine.jobMetrics.maxWorkerNameLength="
            + EXPECTED_MAX_WORKER_NAME_LENGTH,
        "zeebe.broker.experimental.engine.jobMetrics.maxJobTypeLength="
            + EXPECTED_MAX_JOB_TYPE_LENGTH,
        "zeebe.broker.experimental.engine.jobMetrics.maxTenantIdLength="
            + EXPECTED_MAX_TENANT_ID_LENGTH,
        "zeebe.broker.experimental.engine.jobMetrics.maxUniqueKeys=" + EXPECTED_MAX_UNIQUE_KEYS,
        "zeebe.broker.experimental.engine.jobMetrics.enabled=" + EXPECTED_ENABLED,
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetJobMetricsFromLegacy() {
      final var jobMetrics = brokerCfg.getExperimental().getEngine().getJobMetrics();
      assertThat(jobMetrics.getExportInterval()).isEqualTo(EXPECTED_EXPORT_INTERVAL);
      assertThat(jobMetrics.getMaxWorkerNameLength()).isEqualTo(EXPECTED_MAX_WORKER_NAME_LENGTH);
      assertThat(jobMetrics.getMaxJobTypeLength()).isEqualTo(EXPECTED_MAX_JOB_TYPE_LENGTH);
      assertThat(jobMetrics.getMaxTenantIdLength()).isEqualTo(EXPECTED_MAX_TENANT_ID_LENGTH);
      assertThat(jobMetrics.getMaxUniqueKeys()).isEqualTo(EXPECTED_MAX_UNIQUE_KEYS);
      assertThat(jobMetrics.isEnabled()).isEqualTo(EXPECTED_ENABLED);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.monitoring.metrics.job-metrics.export-interval=10m",
        "camunda.monitoring.metrics.job-metrics.max-worker-name-length="
            + EXPECTED_MAX_WORKER_NAME_LENGTH,
        "camunda.monitoring.metrics.job-metrics.max-job-type-length="
            + EXPECTED_MAX_JOB_TYPE_LENGTH,
        "camunda.monitoring.metrics.job-metrics.max-tenant-id-length="
            + EXPECTED_MAX_TENANT_ID_LENGTH,
        "camunda.monitoring.metrics.job-metrics.max-unique-keys=" + EXPECTED_MAX_UNIQUE_KEYS,
        "camunda.monitoring.metrics.job-metrics.enabled=" + EXPECTED_ENABLED,
        // legacy (different values to ensure new config wins)
        "zeebe.broker.experimental.engine.jobMetrics.exportInterval=1m",
        "zeebe.broker.experimental.engine.jobMetrics.maxWorkerNameLength=10",
        "zeebe.broker.experimental.engine.jobMetrics.maxJobTypeLength=10",
        "zeebe.broker.experimental.engine.jobMetrics.maxTenantIdLength=10",
        "zeebe.broker.experimental.engine.jobMetrics.maxUniqueKeys=10",
        "zeebe.broker.experimental.engine.jobMetrics.enabled=true",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetJobMetrics() {
      final var jobMetrics = brokerCfg.getExperimental().getEngine().getJobMetrics();
      assertThat(jobMetrics.getExportInterval()).isEqualTo(EXPECTED_EXPORT_INTERVAL);
      assertThat(jobMetrics.getMaxWorkerNameLength()).isEqualTo(EXPECTED_MAX_WORKER_NAME_LENGTH);
      assertThat(jobMetrics.getMaxJobTypeLength()).isEqualTo(EXPECTED_MAX_JOB_TYPE_LENGTH);
      assertThat(jobMetrics.getMaxTenantIdLength()).isEqualTo(EXPECTED_MAX_TENANT_ID_LENGTH);
      assertThat(jobMetrics.getMaxUniqueKeys()).isEqualTo(EXPECTED_MAX_UNIQUE_KEYS);
      assertThat(jobMetrics.isEnabled()).isEqualTo(EXPECTED_ENABLED);
    }
  }
}
