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
import io.camunda.zeebe.broker.system.configuration.engine.JobsCfg;
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
public class EngineJobTest {
  @Nested
  @TestPropertySource(
      properties = {"camunda.processing.engine.job.include-variables-in-job-completed-event=true"})
  class IncludeVariablesInJobCompletedEvent {
    final BrokerBasedProperties brokerCfg;

    IncludeVariablesInJobCompletedEvent(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetJobs() {
      assertThat(brokerCfg.getExperimental().getEngine().getJobs())
          .returns(true, JobsCfg::isIncludeVariablesInJobCompletedEvent);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.processing.engine.job.timeout-checker-batch-limit=50",
        "camunda.processing.engine.job.timeout-checker-polling-interval=2s",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetTimeoutChecker() {
      assertThat(brokerCfg.getExperimental().getEngine().getJobs())
          .returns(50, JobsCfg::getTimeoutCheckerBatchLimit)
          .returns(Duration.ofSeconds(2), JobsCfg::getTimeoutCheckerPollingInterval);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.experimental.engine.jobs.timeoutCheckerBatchLimit=50",
        "zeebe.broker.experimental.engine.jobs.timeoutCheckerPollingInterval=2s",
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetTimeoutCheckerFromLegacy() {
      assertThat(brokerCfg.getExperimental().getEngine().getJobs())
          .returns(50, JobsCfg::getTimeoutCheckerBatchLimit)
          .returns(Duration.ofSeconds(2), JobsCfg::getTimeoutCheckerPollingInterval);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.processing.engine.job.timeout-checker-batch-limit=50",
        "camunda.processing.engine.job.timeout-checker-polling-interval=2s",
        // legacy
        "zeebe.broker.experimental.engine.jobs.timeoutCheckerBatchLimit=500",
        "zeebe.broker.experimental.engine.jobs.timeoutCheckerPollingInterval=20s",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetTimeoutCheckerFromNew() {
      assertThat(brokerCfg.getExperimental().getEngine().getJobs())
          .returns(50, JobsCfg::getTimeoutCheckerBatchLimit)
          .returns(Duration.ofSeconds(2), JobsCfg::getTimeoutCheckerPollingInterval);
    }
  }
}
