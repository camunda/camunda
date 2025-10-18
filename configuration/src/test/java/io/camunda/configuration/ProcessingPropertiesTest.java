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
import io.camunda.zeebe.broker.system.configuration.ConsistencyCheckCfg;
import io.camunda.zeebe.broker.system.configuration.FeatureFlagsCfg;
import io.camunda.zeebe.broker.system.configuration.ProcessingCfg;
import java.time.Duration;
import java.util.Set;
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
public class ProcessingPropertiesTest {

  private static final int EXPECTED_MAX_COMMANDS_IN_BATCH = 200;
  private static final boolean EXPECTED_ENABLE_ASYNC_SCHEDULED_TASKS = false;
  private static final Duration EXPECTED_SCHEDULED_TASKS_CHECK_INTERVAL = Duration.ofSeconds(10);
  private static final Set<Long> EXPECTED_SKIP_POSITIONS = Set.of(10L, 20L);
  private static final boolean EXPECTED_ENABLE_PRECONDITIONS_CHECK = true;
  private static final boolean EXPECTED_ENABLE_FOREIGN_KEY_CHECKS = true;
  private static final boolean EXPECTED_ENABLE_YIELDING_DUEDATE_CHECKER = false;
  private static final boolean EXPECTED_ENABLE_ASYNC_MESSAGE_TTL_CHECKER = true;
  private static final boolean EXPECTED_ENABLE_ASYNC_TIMER_DUEDATE_CHECKER = true;
  private static final boolean EXPECTED_ENABLE_STRAIGHTTHROUGH_PROCESSING_LOOP_DETECTOR = false;
  private static final boolean EXPECTED_ENABLE_MESSAGE_BODY_ON_EXPIRED = true;

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.processing.max-commands-in-batch=" + EXPECTED_MAX_COMMANDS_IN_BATCH,
        "camunda.processing.enable-async-scheduled-tasks=" + EXPECTED_ENABLE_ASYNC_SCHEDULED_TASKS,
        "camunda.processing.scheduled-tasks-check-interval=10s",
        "camunda.processing.skip-positions=10,20",
        "camunda.processing.enable-preconditions-check=" + EXPECTED_ENABLE_PRECONDITIONS_CHECK,
        "camunda.processing.enable-foreign-key-checks=" + EXPECTED_ENABLE_FOREIGN_KEY_CHECKS,
        "camunda.processing.enable-yielding-duedate-checker="
            + EXPECTED_ENABLE_YIELDING_DUEDATE_CHECKER,
        "camunda.processing.enable-async-message-ttl-checker="
            + EXPECTED_ENABLE_ASYNC_MESSAGE_TTL_CHECKER,
        "camunda.processing.enable-async-timer-duedate-checker="
            + EXPECTED_ENABLE_ASYNC_TIMER_DUEDATE_CHECKER,
        "camunda.processing.enable-straightthrough-processing-loop-detector="
            + EXPECTED_ENABLE_STRAIGHTTHROUGH_PROCESSING_LOOP_DETECTOR,
        "camunda.processing.enable-message-body-on-expired="
            + EXPECTED_ENABLE_MESSAGE_BODY_ON_EXPIRED
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerBasedProperties;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetProcessingProperties() {
      assertThat(brokerBasedProperties.getProcessing())
          .returns(EXPECTED_MAX_COMMANDS_IN_BATCH, ProcessingCfg::getMaxCommandsInBatch)
          .returns(
              EXPECTED_ENABLE_ASYNC_SCHEDULED_TASKS, ProcessingCfg::isEnableAsyncScheduledTasks)
          .returns(
              EXPECTED_SCHEDULED_TASKS_CHECK_INTERVAL, ProcessingCfg::getScheduledTaskCheckInterval)
          .returns(EXPECTED_SKIP_POSITIONS, ProcessingCfg::skipPositions);

      assertThat(brokerBasedProperties.getExperimental().getConsistencyChecks())
          .returns(EXPECTED_ENABLE_PRECONDITIONS_CHECK, ConsistencyCheckCfg::isEnablePreconditions)
          .returns(
              EXPECTED_ENABLE_FOREIGN_KEY_CHECKS, ConsistencyCheckCfg::isEnableForeignKeyChecks);

      assertThat(brokerBasedProperties.getExperimental().getFeatures())
          .returns(
              EXPECTED_ENABLE_YIELDING_DUEDATE_CHECKER,
              FeatureFlagsCfg::isEnableYieldingDueDateChecker)
          .returns(
              EXPECTED_ENABLE_ASYNC_MESSAGE_TTL_CHECKER,
              FeatureFlagsCfg::isEnableMessageTtlCheckerAsync)
          .returns(
              EXPECTED_ENABLE_ASYNC_TIMER_DUEDATE_CHECKER,
              FeatureFlagsCfg::isEnableTimerDueDateCheckerAsync)
          .returns(
              EXPECTED_ENABLE_STRAIGHTTHROUGH_PROCESSING_LOOP_DETECTOR,
              FeatureFlagsCfg::isEnableStraightThroughProcessingLoopDetector)
          .returns(
              EXPECTED_ENABLE_MESSAGE_BODY_ON_EXPIRED,
              FeatureFlagsCfg::isEnableMessageBodyOnExpired);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.processingCfg.maxCommandsInBatch=" + EXPECTED_MAX_COMMANDS_IN_BATCH,
        "zeebe.broker.processingCfg.enableAsyncScheduledTasks="
            + EXPECTED_ENABLE_ASYNC_SCHEDULED_TASKS,
        "zeebe.broker.processingCfg.scheduledTaskCheckInterval=10s",
        "zeebe.broker.processingCfg.skipPositions=10,20",
        "zeebe.broker.experimental.consistencyChecks.enablePreconditions="
            + EXPECTED_ENABLE_PRECONDITIONS_CHECK,
        "zeebe.broker.experimental.consistencyChecks.enableForeignKeyChecks="
            + EXPECTED_ENABLE_FOREIGN_KEY_CHECKS,
        "zeebe.broker.experimental.features.enableYieldingDueDateChecker="
            + EXPECTED_ENABLE_YIELDING_DUEDATE_CHECKER,
        "zeebe.broker.experimental.features.enableMessageTtlCheckerAsync="
            + EXPECTED_ENABLE_ASYNC_MESSAGE_TTL_CHECKER,
        "zeebe.broker.experimental.features.enableTimerDueDateCheckerAsync="
            + EXPECTED_ENABLE_ASYNC_TIMER_DUEDATE_CHECKER,
        "zeebe.broker.experimental.features.enableStraightThroughProcessingLoopDetector="
            + EXPECTED_ENABLE_STRAIGHTTHROUGH_PROCESSING_LOOP_DETECTOR,
        "zeebe.broker.experimental.features.enableMessageBodyOnExpired="
            + EXPECTED_ENABLE_MESSAGE_BODY_ON_EXPIRED,
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerBasedProperties;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetProcessingPropertiesFromLegacy() {
      assertThat(brokerBasedProperties.getProcessing())
          .returns(EXPECTED_MAX_COMMANDS_IN_BATCH, ProcessingCfg::getMaxCommandsInBatch)
          .returns(
              EXPECTED_ENABLE_ASYNC_SCHEDULED_TASKS, ProcessingCfg::isEnableAsyncScheduledTasks)
          .returns(
              EXPECTED_SCHEDULED_TASKS_CHECK_INTERVAL, ProcessingCfg::getScheduledTaskCheckInterval)
          .returns(EXPECTED_SKIP_POSITIONS, ProcessingCfg::skipPositions);

      assertThat(brokerBasedProperties.getExperimental().getConsistencyChecks())
          .returns(EXPECTED_ENABLE_PRECONDITIONS_CHECK, ConsistencyCheckCfg::isEnablePreconditions)
          .returns(
              EXPECTED_ENABLE_FOREIGN_KEY_CHECKS, ConsistencyCheckCfg::isEnableForeignKeyChecks);

      assertThat(brokerBasedProperties.getExperimental().getFeatures())
          .returns(
              EXPECTED_ENABLE_YIELDING_DUEDATE_CHECKER,
              FeatureFlagsCfg::isEnableYieldingDueDateChecker)
          .returns(
              EXPECTED_ENABLE_ASYNC_MESSAGE_TTL_CHECKER,
              FeatureFlagsCfg::isEnableMessageTtlCheckerAsync)
          .returns(
              EXPECTED_ENABLE_ASYNC_TIMER_DUEDATE_CHECKER,
              FeatureFlagsCfg::isEnableTimerDueDateCheckerAsync)
          .returns(
              EXPECTED_ENABLE_STRAIGHTTHROUGH_PROCESSING_LOOP_DETECTOR,
              FeatureFlagsCfg::isEnableStraightThroughProcessingLoopDetector)
          .returns(
              EXPECTED_ENABLE_MESSAGE_BODY_ON_EXPIRED,
              FeatureFlagsCfg::isEnableMessageBodyOnExpired);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.processing.max-commands-in-batch=" + EXPECTED_MAX_COMMANDS_IN_BATCH,
        "camunda.processing.enable-async-scheduled-tasks=" + EXPECTED_ENABLE_ASYNC_SCHEDULED_TASKS,
        "camunda.processing.scheduled-tasks-check-interval=10s",
        "camunda.processing.skip-positions=10,20",
        "camunda.processing.enable-preconditions-check=" + EXPECTED_ENABLE_PRECONDITIONS_CHECK,
        "camunda.processing.enable-foreign-key-checks=" + EXPECTED_ENABLE_FOREIGN_KEY_CHECKS,
        "camunda.processing.enable-yielding-duedate-checker="
            + EXPECTED_ENABLE_YIELDING_DUEDATE_CHECKER,
        "camunda.processing.enable-async-message-ttl-checker="
            + EXPECTED_ENABLE_ASYNC_MESSAGE_TTL_CHECKER,
        "camunda.processing.enable-async-timer-duedate-checker="
            + EXPECTED_ENABLE_ASYNC_TIMER_DUEDATE_CHECKER,
        "camunda.processing.enable-straightthrough-processing-loop-detector="
            + EXPECTED_ENABLE_STRAIGHTTHROUGH_PROCESSING_LOOP_DETECTOR,
        "camunda.processing.enable-message-body-on-expired="
            + EXPECTED_ENABLE_MESSAGE_BODY_ON_EXPIRED,

        // legacy
        "zeebe.broker.processingCfg.maxCommandsInBatch=1",
        "zeebe.broker.processingCfg.enableAsyncScheduledTasks=true",
        "zeebe.broker.processingCfg.scheduledTaskCheckInterval=1s",
        "zeebe.broker.processingCfg.skipPositions=30,40",
        "zeebe.broker.experimental.consistencyChecks.enablePreconditions=false",
        "zeebe.broker.experimental.consistencyChecks.enableForeignKeyChecks=false",
        "zeebe.broker.experimental.features.enableYieldingDueDateChecker=true",
        "zeebe.broker.experimental.features.enableMessageTtlCheckerAsync=false",
        "zeebe.broker.experimental.features.enableTimerDueDateCheckerAsync=false",
        "zeebe.broker.experimental.features.enableStraightThroughProcessingLoopDetector=true",
        "zeebe.broker.experimental.features.enableMessageBodyOnExpired=false"
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerBasedProperties;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void shouldSetProcessingPropertiesFromNew() {
      assertThat(brokerBasedProperties.getProcessing())
          .returns(EXPECTED_MAX_COMMANDS_IN_BATCH, ProcessingCfg::getMaxCommandsInBatch)
          .returns(
              EXPECTED_ENABLE_ASYNC_SCHEDULED_TASKS, ProcessingCfg::isEnableAsyncScheduledTasks)
          .returns(
              EXPECTED_SCHEDULED_TASKS_CHECK_INTERVAL, ProcessingCfg::getScheduledTaskCheckInterval)
          .returns(EXPECTED_SKIP_POSITIONS, ProcessingCfg::skipPositions);

      assertThat(brokerBasedProperties.getExperimental().getConsistencyChecks())
          .returns(EXPECTED_ENABLE_PRECONDITIONS_CHECK, ConsistencyCheckCfg::isEnablePreconditions)
          .returns(
              EXPECTED_ENABLE_FOREIGN_KEY_CHECKS, ConsistencyCheckCfg::isEnableForeignKeyChecks);

      assertThat(brokerBasedProperties.getExperimental().getFeatures())
          .returns(
              EXPECTED_ENABLE_YIELDING_DUEDATE_CHECKER,
              FeatureFlagsCfg::isEnableYieldingDueDateChecker)
          .returns(
              EXPECTED_ENABLE_ASYNC_MESSAGE_TTL_CHECKER,
              FeatureFlagsCfg::isEnableMessageTtlCheckerAsync)
          .returns(
              EXPECTED_ENABLE_ASYNC_TIMER_DUEDATE_CHECKER,
              FeatureFlagsCfg::isEnableTimerDueDateCheckerAsync)
          .returns(
              EXPECTED_ENABLE_STRAIGHTTHROUGH_PROCESSING_LOOP_DETECTOR,
              FeatureFlagsCfg::isEnableStraightThroughProcessingLoopDetector)
          .returns(
              EXPECTED_ENABLE_MESSAGE_BODY_ON_EXPIRED,
              FeatureFlagsCfg::isEnableMessageBodyOnExpired);
    }
  }
}
