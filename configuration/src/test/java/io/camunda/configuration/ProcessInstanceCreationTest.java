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
import io.camunda.zeebe.broker.system.configuration.engine.ProcessInstanceCreationCfg;
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
public class ProcessInstanceCreationTest {
  @Nested
  class WithNoneSet {
    final BrokerBasedProperties brokerCfg;

    WithNoneSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetDefaultBusinessIdUniquenessEnabled() {
      assertThat(brokerCfg.getExperimental().getEngine().getProcessInstanceCreation())
          .returns(false, ProcessInstanceCreationCfg::isBusinessIdUniquenessEnabled);
    }

    @Test
    void shouldSetDefaultMessageStartDedupExpirationSweepInterval() {
      assertThat(brokerCfg.getExperimental().getEngine().getProcessInstanceCreation())
          .returns(
              Duration.ofSeconds(30),
              ProcessInstanceCreationCfg::getMessageStartDedupExpirationSweepInterval);
    }

    @Test
    void shouldSetDefaultMessageStartDedupExpirationSweepBatchLimit() {
      assertThat(brokerCfg.getExperimental().getEngine().getProcessInstanceCreation())
          .returns(100, ProcessInstanceCreationCfg::getMessageStartDedupExpirationSweepBatchLimit);
    }

    @Test
    void shouldSetDefaultMessageStartAskRetryInterval() {
      assertThat(brokerCfg.getExperimental().getEngine().getProcessInstanceCreation())
          .returns(
              Duration.ofSeconds(10), ProcessInstanceCreationCfg::getMessageStartAskRetryInterval);
    }

    @Test
    void shouldSetDefaultMessageStartLockReleasePollInterval() {
      assertThat(brokerCfg.getExperimental().getEngine().getProcessInstanceCreation())
          .returns(
              Duration.ofSeconds(1),
              ProcessInstanceCreationCfg::getMessageStartLockReleasePollInterval);
    }

    @Test
    void shouldSetDefaultMessageStartLockReleasePollMaxBackoff() {
      assertThat(brokerCfg.getExperimental().getEngine().getProcessInstanceCreation())
          .returns(
              Duration.ofSeconds(30),
              ProcessInstanceCreationCfg::getMessageStartLockReleasePollMaxBackoff);
    }

    @Test
    void shouldSetDefaultMessageStartLockReleasePollBatchLimit() {
      assertThat(brokerCfg.getExperimental().getEngine().getProcessInstanceCreation())
          .returns(64, ProcessInstanceCreationCfg::getMessageStartLockReleasePollBatchLimit);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-instance-creation.business-id-uniqueness-enabled=true",
        "camunda.process-instance-creation.message-start-dedup-expiration-sweep-interval=1m",
        "camunda.process-instance-creation.message-start-dedup-expiration-sweep-batch-limit=250",
        "camunda.process-instance-creation.message-start-ask-retry-interval=5s",
        "camunda.process-instance-creation.message-start-lock-release-poll-interval=2s",
        "camunda.process-instance-creation.message-start-lock-release-poll-max-backoff=45s",
        "camunda.process-instance-creation.message-start-lock-release-poll-batch-limit=128",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetProcessInstanceCreation() {
      assertThat(brokerCfg.getExperimental().getEngine().getProcessInstanceCreation())
          .returns(true, ProcessInstanceCreationCfg::isBusinessIdUniquenessEnabled)
          .returns(
              Duration.ofMinutes(1),
              ProcessInstanceCreationCfg::getMessageStartDedupExpirationSweepInterval)
          .returns(250, ProcessInstanceCreationCfg::getMessageStartDedupExpirationSweepBatchLimit)
          .returns(
              Duration.ofSeconds(5), ProcessInstanceCreationCfg::getMessageStartAskRetryInterval)
          .returns(
              Duration.ofSeconds(2),
              ProcessInstanceCreationCfg::getMessageStartLockReleasePollInterval)
          .returns(
              Duration.ofSeconds(45),
              ProcessInstanceCreationCfg::getMessageStartLockReleasePollMaxBackoff)
          .returns(128, ProcessInstanceCreationCfg::getMessageStartLockReleasePollBatchLimit);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.experimental.engine.processInstanceCreation.businessIdUniquenessEnabled=true",
        "zeebe.broker.experimental.engine.processInstanceCreation.messageStartDedupExpirationSweepInterval=1m",
        "zeebe.broker.experimental.engine.processInstanceCreation.messageStartDedupExpirationSweepBatchLimit=250",
        "zeebe.broker.experimental.engine.processInstanceCreation.messageStartAskRetryInterval=5s",
        "zeebe.broker.experimental.engine.processInstanceCreation.messageStartLockReleasePollInterval=2s",
        "zeebe.broker.experimental.engine.processInstanceCreation.messageStartLockReleasePollMaxBackoff=45s",
        "zeebe.broker.experimental.engine.processInstanceCreation.messageStartLockReleasePollBatchLimit=128",
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetProcessInstanceCreationFromLegacy() {
      assertThat(brokerCfg.getExperimental().getEngine().getProcessInstanceCreation())
          .returns(true, ProcessInstanceCreationCfg::isBusinessIdUniquenessEnabled)
          .returns(
              Duration.ofMinutes(1),
              ProcessInstanceCreationCfg::getMessageStartDedupExpirationSweepInterval)
          .returns(250, ProcessInstanceCreationCfg::getMessageStartDedupExpirationSweepBatchLimit)
          .returns(
              Duration.ofSeconds(5), ProcessInstanceCreationCfg::getMessageStartAskRetryInterval)
          .returns(
              Duration.ofSeconds(2),
              ProcessInstanceCreationCfg::getMessageStartLockReleasePollInterval)
          .returns(
              Duration.ofSeconds(45),
              ProcessInstanceCreationCfg::getMessageStartLockReleasePollMaxBackoff)
          .returns(128, ProcessInstanceCreationCfg::getMessageStartLockReleasePollBatchLimit);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.process-instance-creation.business-id-uniqueness-enabled=true",
        "camunda.process-instance-creation.message-start-dedup-expiration-sweep-interval=1m",
        "camunda.process-instance-creation.message-start-dedup-expiration-sweep-batch-limit=250",
        "camunda.process-instance-creation.message-start-ask-retry-interval=5s",
        "camunda.process-instance-creation.message-start-lock-release-poll-interval=2s",
        "camunda.process-instance-creation.message-start-lock-release-poll-max-backoff=45s",
        "camunda.process-instance-creation.message-start-lock-release-poll-batch-limit=128",
        // legacy
        "zeebe.broker.experimental.engine.processInstanceCreation.businessIdUniquenessEnabled=false",
        "zeebe.broker.experimental.engine.processInstanceCreation.messageStartDedupExpirationSweepInterval=2m",
        "zeebe.broker.experimental.engine.processInstanceCreation.messageStartDedupExpirationSweepBatchLimit=999",
        "zeebe.broker.experimental.engine.processInstanceCreation.messageStartAskRetryInterval=42s",
        "zeebe.broker.experimental.engine.processInstanceCreation.messageStartLockReleasePollInterval=3s",
        "zeebe.broker.experimental.engine.processInstanceCreation.messageStartLockReleasePollMaxBackoff=60s",
        "zeebe.broker.experimental.engine.processInstanceCreation.messageStartLockReleasePollBatchLimit=256",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetProcessInstanceCreationFromNew() {
      assertThat(brokerCfg.getExperimental().getEngine().getProcessInstanceCreation())
          .returns(true, ProcessInstanceCreationCfg::isBusinessIdUniquenessEnabled)
          .returns(
              Duration.ofMinutes(1),
              ProcessInstanceCreationCfg::getMessageStartDedupExpirationSweepInterval)
          .returns(250, ProcessInstanceCreationCfg::getMessageStartDedupExpirationSweepBatchLimit)
          .returns(
              Duration.ofSeconds(5), ProcessInstanceCreationCfg::getMessageStartAskRetryInterval)
          .returns(
              Duration.ofSeconds(2),
              ProcessInstanceCreationCfg::getMessageStartLockReleasePollInterval)
          .returns(
              Duration.ofSeconds(45),
              ProcessInstanceCreationCfg::getMessageStartLockReleasePollMaxBackoff)
          .returns(128, ProcessInstanceCreationCfg::getMessageStartLockReleasePollBatchLimit);
    }
  }
}
