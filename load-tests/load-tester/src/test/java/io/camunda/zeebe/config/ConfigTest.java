/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(ConfigTest.class)
@EnableConfigurationProperties(LoadTesterProperties.class)
class ConfigTest {

  @Autowired private LoadTesterProperties properties;

  @Test
  void shouldReadDefaultAppConfig() {
    // given / when - no external overrides, defaults come from LoadTesterProperties POJO

    // then
<<<<<<< HEAD
    assertThat(properties.isMonitorDataAvailability()).isTrue();
    assertThat(properties.getMonitorDataAvailabilityInterval()).hasMillis(250);
    assertThat(properties.isPerformReadBenchmarks()).isFalse();
    assertThat(properties.getDisabledQueries()).isEmpty();
    assertThat(properties.getDisabledQueriesList()).isEmpty();
=======
    assertThat(appCfg.getBrokerUrl()).isEqualTo("http://localhost:26500");
    assertThat(appCfg.getBrokerRestUrl()).isEqualTo("http://localhost:8080");
    assertThat(appCfg.isPreferRest()).isTrue();
    assertThat(appCfg.getMonitoringPort()).isEqualTo(9600);
    assertThat(appCfg.isMonitorDataAvailability()).isTrue();
    assertThat(appCfg.getMonitorDataAvailabilityInterval()).hasMillis(250);
    assertThat(appCfg.isPerformReadBenchmarks()).isFalse();
    assertThat(appCfg.getDisabledQueriesList()).isEmpty();
    assertThat(appCfg.isClientSideLoadBalancing()).isFalse();

    // authentication
    final var authCfg = appCfg.getAuth();
    assertThat(authCfg).isNotNull();
    assertThat(authCfg.getType()).isEqualTo(AuthCfg.AuthType.NONE);
>>>>>>> e09b064f5 (test: client side load balancing config for load test)

    // starter
    final var starterCfg = properties.getStarter();
    assertThat(starterCfg).isNotNull();
    assertThat(starterCfg.getProcessId()).isEqualTo("benchmark");
    assertThat(starterCfg.getRate()).isEqualTo(300.0);
    assertThat(starterCfg.getRateDuration()).hasSeconds(1);
    assertThat(starterCfg.getRatePerSecond()).isEqualTo(300.0);
    assertThat(starterCfg.getThreads()).isEqualTo(2);
    assertThat(starterCfg.getBpmnXmlPath()).isEqualTo("bpmn/one_task.bpmn");
    assertThat(starterCfg.getExtraBpmnModels()).isEmpty();
    assertThat(starterCfg.getBusinessKey()).isEqualTo("businessKey");
    assertThat(starterCfg.getPayloadPath()).isEqualTo("bpmn/big_payload.json");
    assertThat(starterCfg.isWithResults()).isFalse();
    assertThat(starterCfg.getWithResultsTimeout()).hasSeconds(60);
    assertThat(starterCfg.getDurationLimit()).isZero();
    assertThat(starterCfg.getMsgName()).isEqualTo("msg");
    assertThat(starterCfg.isStartViaMessage()).isFalse();

    // worker (connection properties like type, name, threads, capacity, polling,
    // streaming, timeout are configured via camunda.client.worker.defaults.*)
    final var workerCfg = properties.getWorker();
    assertThat(workerCfg).isNotNull();
    assertThat(workerCfg.getCompletionDelay()).hasMillis(300);
    assertThat(workerCfg.getPayloadPath()).isEqualTo("bpmn/big_payload.json");
    assertThat(workerCfg.getMessageName()).isEqualTo("messageName");
    assertThat(workerCfg.isSendMessage()).isFalse();
    assertThat(workerCfg.getCorrelationKeyVariableName()).isEqualTo("correlationKey-var");
  }

  @Nested
  @SpringJUnitConfig(ConfigTest.class)
  @EnableConfigurationProperties(LoadTesterProperties.class)
  @TestPropertySource("classpath:different-application.properties")
  class DifferentAppConfigTest {

    @Autowired private LoadTesterProperties properties;

<<<<<<< HEAD
    @Test
    void shouldReadDifferentAppConfig() {
      // given / when - overrides loaded from different-application.properties
=======
    // then
    assertThat(appCfg.getBrokerUrl()).isEqualTo("http://localhost:26500");
    assertThat(appCfg.getBrokerRestUrl()).isEqualTo("http://localhost:8081");
    assertThat(appCfg.isPreferRest()).isTrue();
    assertThat(appCfg.getMonitoringPort()).isEqualTo(9600);
    assertThat(appCfg.isMonitorDataAvailability()).isFalse();
    assertThat(appCfg.getMonitorDataAvailabilityInterval()).hasMillis(50);
    assertThat(appCfg.isPerformReadBenchmarks()).isTrue();
    assertThat(appCfg.getDisabledQueriesList())
        .containsExactlyInAnyOrder("process_instances_active", "audit_log_by_category");
    assertThat(appCfg.isClientSideLoadBalancing()).isTrue();
>>>>>>> e09b064f5 (test: client side load balancing config for load test)

      // then
      assertThat(properties.isMonitorDataAvailability()).isFalse();
      assertThat(properties.getMonitorDataAvailabilityInterval()).hasMillis(50);
      assertThat(properties.isPerformReadBenchmarks()).isTrue();
      assertThat(properties.getDisabledQueriesList())
          .containsExactlyInAnyOrder("process_instances_active", "audit_log_by_category");

      // starter
      final var starterCfg = properties.getStarter();
      assertThat(starterCfg.getProcessId()).isEqualTo("benchmark");
      assertThat(starterCfg.getRate()).isEqualTo(30.5);
      assertThat(starterCfg.getRateDuration()).hasMinutes(5);
      assertThat(starterCfg.getRatePerSecond()).isCloseTo(30.5 / 300.0, within(1e-9));
      assertThat(starterCfg.getThreads()).isEqualTo(2);
      assertThat(starterCfg.getBpmnXmlPath())
          .isEqualTo("bpmn/realistic/bankCustomerComplaintDisputeHandling.bpmn");
      assertThat(starterCfg.getExtraBpmnModels())
          .containsExactly(
              "bpmn/realistic/determineFraudRatingConfidence.dmn",
              "bpmn/realistic/refundingProcess.bpmn");
      assertThat(starterCfg.getBusinessKey()).isEqualTo("customerId");
      assertThat(starterCfg.getPayloadPath()).isEqualTo("bpmn/realistic/realisticPayload.json");

      // worker
      final var workerCfg = properties.getWorker();
      assertThat(workerCfg.getCompletionDelay()).hasMillis(300);
      assertThat(workerCfg.getPayloadPath()).isEqualTo("bpmn/big_payload.json");
      assertThat(workerCfg.getMessageName()).isEqualTo("msg");
      assertThat(workerCfg.isSendMessage()).isTrue();
      assertThat(workerCfg.getCorrelationKeyVariableName()).isEqualTo("var");
    }
  }
}
