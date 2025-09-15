/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class ConfigTest {

  @Test
  public void shouldReadDefaultAppConfig() {
    // given

    // when
    final var appCfg = AppConfigLoader.load();

    // then
    assertThat(appCfg.getBrokerUrl()).isEqualTo("http://localhost:26500");
    assertThat(appCfg.getBrokerRestUrl()).isEqualTo("http://localhost:8080");
    assertThat(appCfg.isPreferRest()).isFalse();
    assertThat(appCfg.getMonitoringPort()).isEqualTo(9600);

    // authentication
    final var authCfg = appCfg.getAuth();
    assertThat(authCfg).isNotNull();
    assertThat(authCfg.getType()).isEqualTo(AuthCfg.AuthType.NONE);

    // starter
    final var starterCfg = appCfg.getStarter();
    assertThat(starterCfg).isNotNull();

    assertThat(starterCfg.getProcessId()).isEqualTo("benchmark");
    assertThat(starterCfg.getRate()).isEqualTo(300);
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

    // worker
    final var workerCfg = appCfg.getWorker();
    assertThat(workerCfg).isNotNull();

    assertThat(workerCfg.getJobType()).isEqualTo("benchmark-task");
    assertThat(workerCfg.getWorkerName()).isEqualTo("benchmark-worker");
    assertThat(workerCfg.getThreads()).isEqualTo(10);
    assertThat(workerCfg.getCapacity()).isEqualTo(30);
    assertThat(workerCfg.getPollingDelay()).hasSeconds(1);
    assertThat(workerCfg.getCompletionDelay()).hasMillis(300);
    assertThat(workerCfg.getPayloadPath()).isEqualTo("bpmn/big_payload.json");
    assertThat(workerCfg.isStreamEnabled()).isTrue();
    assertThat(workerCfg.getTimeout()).hasSeconds(0);
    assertThat(workerCfg.getMessageName()).isEqualTo("messageName");
    assertThat(workerCfg.isSendMessage()).isFalse();
    assertThat(workerCfg.getCorrelationKeyVariableName()).isEqualTo("correlationKey-var");
  }

  @Test
  public void shouldReadDifferentAppConfig() {
    // given

    // when
    final var appCfg = AppConfigLoader.load("different-application.conf");

    // then
    assertThat(appCfg.getBrokerUrl()).isEqualTo("http://localhost:26500");
    assertThat(appCfg.getBrokerRestUrl()).isEqualTo("http://localhost:8081");
    assertThat(appCfg.isPreferRest()).isTrue();
    assertThat(appCfg.getMonitoringPort()).isEqualTo(9600);

    // authentication
    final var authCfg = appCfg.getAuth();
    assertThat(authCfg).isNotNull();
    assertThat(authCfg.getType()).isEqualTo(AuthCfg.AuthType.BASIC);
    assertThat(authCfg.getBasic().getUsername()).isEqualTo("benchmark-user");
    assertThat(authCfg.getBasic().getPassword()).isEqualTo("benchmark-password");

    assertThat(authCfg.getOauth().getAudience()).isEqualTo("zeebe");
    assertThat(authCfg.getOauth().getClientId()).isEqualTo("benchmark-client");
    assertThat(authCfg.getOauth().getClientSecret()).isEqualTo("benchmark-secret");
    assertThat(authCfg.getOauth().getAuthzUrl()).isEqualTo("http://localhost:9090/auth");

    // starter
    final var starterCfg = appCfg.getStarter();
    assertThat(starterCfg).isNotNull();

    assertThat(starterCfg.getProcessId()).isEqualTo("benchmark");
    assertThat(starterCfg.getRate()).isEqualTo(300);
    assertThat(starterCfg.getThreads()).isEqualTo(2);
    assertThat(starterCfg.getBpmnXmlPath())
        .isEqualTo("bpmn/realistic/bankCustomerComplaintDisputeHandling.bpmn");
    assertThat(starterCfg.getExtraBpmnModels())
        .isNotEmpty()
        .containsExactly(
            "bpmn/realistic/determineFraudRatingConfidence.dmn",
            "bpmn/realistic/refundingProcess.bpmn");
    assertThat(starterCfg.getBusinessKey()).isEqualTo("customerId");
    assertThat(starterCfg.getPayloadPath()).isEqualTo("bpmn/realistic/realisticPayload.json");
    assertThat(starterCfg.isWithResults()).isFalse();
    assertThat(starterCfg.getWithResultsTimeout()).hasSeconds(60);
    assertThat(starterCfg.getDurationLimit()).isZero();
    assertThat(starterCfg.getMsgName()).isEqualTo("msg");
    assertThat(starterCfg.isStartViaMessage()).isFalse();

    // worker
    final var workerCfg = appCfg.getWorker();
    assertThat(workerCfg).isNotNull();

    assertThat(workerCfg.getJobType()).isEqualTo("benchmark-task");
    assertThat(workerCfg.getWorkerName()).isEqualTo("benchmark-worker");
    assertThat(workerCfg.getThreads()).isEqualTo(10);
    assertThat(workerCfg.getCapacity()).isEqualTo(30);
    assertThat(workerCfg.getPollingDelay()).hasSeconds(1);
    assertThat(workerCfg.getCompletionDelay()).hasMillis(300);
    assertThat(workerCfg.getPayloadPath()).isEqualTo("bpmn/big_payload.json");
    assertThat(workerCfg.isStreamEnabled()).isTrue();
    assertThat(workerCfg.getTimeout()).hasSeconds(0);
    assertThat(workerCfg.getMessageName()).isEqualTo("msg");
    assertThat(workerCfg.isSendMessage()).isTrue();
    assertThat(workerCfg.getCorrelationKeyVariableName()).isEqualTo("var");
  }
}
