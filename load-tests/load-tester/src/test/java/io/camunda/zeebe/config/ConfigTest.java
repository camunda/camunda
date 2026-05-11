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
    assertThat(properties.isMonitorDataAvailability()).isTrue();
    assertThat(properties.getMonitorDataAvailabilityInterval()).hasMillis(250);
    assertThat(properties.isPerformReadBenchmarks()).isFalse();
    assertThat(properties.getDisabledQueries()).isEmpty();
    assertThat(properties.getDisabledQueriesList()).isEmpty();

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

    // optimize - POJO default `enabled=true`; production deployments override to false
    // via application.yaml ${LOAD_TESTER_OPTIMIZE_ENABLED:false} since the test does not
    // bind application.yaml (no @SpringBootTest).
    final var optimizeCfg = properties.getOptimize();
    assertThat(optimizeCfg).isNotNull();
    assertThat(optimizeCfg.isEnabled()).isTrue();
    assertThat(optimizeCfg.getBaseUrl()).isEqualTo("http://optimize:8090");
    assertThat(optimizeCfg.getKeycloakUrl()).isEqualTo("http://camunda-platform-keycloak:18080");
    assertThat(optimizeCfg.getRealm()).isEqualTo("camunda-platform");
    assertThat(optimizeCfg.getClientId()).isEqualTo("optimize");
    assertThat(optimizeCfg.getClientSecret()).isEmpty();
    assertThat(optimizeCfg.getUsername()).isEqualTo("demo");
    assertThat(optimizeCfg.getPassword()).isEqualTo("demo");
    assertThat(optimizeCfg.getProcessDefinitionKey()).isEmpty();
    assertThat(optimizeCfg.getEvaluationInterval()).hasSeconds(60);
    assertThat(optimizeCfg.getInitialDelay()).hasSeconds(10);
    assertThat(optimizeCfg.getAuthRetryMaxAttempts()).isEqualTo(30);
    assertThat(optimizeCfg.getAuthRetryDelay()).hasSeconds(10);
    assertThat(optimizeCfg.getTokenRefreshSkew()).hasSeconds(30);
    assertThat(optimizeCfg.getRequestTimeout()).hasSeconds(30);
  }

  @Nested
  @SpringJUnitConfig(ConfigTest.class)
  @EnableConfigurationProperties(LoadTesterProperties.class)
  @TestPropertySource("classpath:different-application.properties")
  class DifferentAppConfigTest {

    @Autowired private LoadTesterProperties properties;

    @Test
    void shouldReadDifferentAppConfig() {
      // given / when - overrides loaded from different-application.properties

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

      // optimize overrides
      final var optimizeCfg = properties.getOptimize();
      assertThat(optimizeCfg.isEnabled()).isTrue();
      assertThat(optimizeCfg.getBaseUrl()).isEqualTo("http://optimize.svc:8090");
      assertThat(optimizeCfg.getKeycloakUrl()).isEqualTo("http://kc.svc:18080");
      assertThat(optimizeCfg.getRealm()).isEqualTo("custom-realm");
      assertThat(optimizeCfg.getClientId()).isEqualTo("custom-optimize");
      assertThat(optimizeCfg.getClientSecret()).isEqualTo("s3cr3t");
      assertThat(optimizeCfg.getUsername()).isEqualTo("user-x");
      assertThat(optimizeCfg.getPassword()).isEqualTo("pass-x");
      assertThat(optimizeCfg.getProcessDefinitionKey()).isEqualTo("pd-override");
      assertThat(optimizeCfg.getEvaluationInterval()).hasSeconds(30);
      assertThat(optimizeCfg.getInitialDelay()).hasSeconds(5);
      assertThat(optimizeCfg.getAuthRetryMaxAttempts()).isEqualTo(5);
      assertThat(optimizeCfg.getAuthRetryDelay()).hasSeconds(2);
      assertThat(optimizeCfg.getTokenRefreshSkew()).hasSeconds(15);
      assertThat(optimizeCfg.getRequestTimeout()).hasSeconds(20);
    }
  }
}
