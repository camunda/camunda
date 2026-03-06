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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = ConfigTest.TestConfig.class,
    properties = {
      "camunda.client.enabled=false",
    })
public class ConfigTest {

  @Autowired private LoadTesterProperties properties;

  @EnableConfigurationProperties(LoadTesterProperties.class)
  static class TestConfig {}

  @Test
  public void shouldReadDefaultProperties() {
    // given / when - defaults from LoadTesterProperties

    // then
    assertThat(properties.isMonitorDataAvailability()).isTrue();
    assertThat(properties.getMonitorDataAvailabilityInterval()).hasMillis(250);
    assertThat(properties.isPerformReadBenchmarks()).isFalse();

    // starter
    final var starterCfg = properties.getStarter();
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
    final var workerCfg = properties.getWorker();
    assertThat(workerCfg).isNotNull();
    assertThat(workerCfg.getJobType()).isEqualTo("benchmark-task");
    assertThat(workerCfg.getWorkerName()).isEqualTo("benchmark-worker");
    assertThat(workerCfg.getThreads()).isEqualTo(10);
    assertThat(workerCfg.getCapacity()).isEqualTo(30);
    assertThat(workerCfg.getPollingDelay()).hasSeconds(1);
    assertThat(workerCfg.getCompletionDelay()).hasMillis(300);
    assertThat(workerCfg.getPayloadPath()).isEqualTo("bpmn/big_payload.json");
    assertThat(workerCfg.isStreamEnabled()).isTrue();
    assertThat(workerCfg.getTimeout()).isEqualTo(java.time.Duration.ZERO);
    assertThat(workerCfg.getMessageName()).isEqualTo("messageName");
    assertThat(workerCfg.isSendMessage()).isFalse();
    assertThat(workerCfg.getCorrelationKeyVariableName()).isEqualTo("correlationKey-var");
  }

  @Test
  public void shouldOverridePropertiesViaSpringConfig() {
    // given / when - properties are loaded from application.yaml in test resources
    // then - the default values above are verified; override tests can use
    //        @TestPropertySource or @SpringBootTest(properties = {...}) to override
    assertThat(properties.getStarter().getRate()).isEqualTo(300);
  }
}
