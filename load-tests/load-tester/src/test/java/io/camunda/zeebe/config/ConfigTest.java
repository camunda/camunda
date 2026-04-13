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

import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

public class ConfigTest {

  @SpringBootApplication
  @EnableConfigurationProperties(LoadTesterProperties.class)
  static class TestApp {}

  @Nested
  @SpringBootTest(classes = ConfigTest.TestApp.class)
  class DefaultConfigTest {

    @Autowired private LoadTesterProperties props;

    @Test
    public void shouldReadDefaultAppConfig() {
      // then
      assertThat(props.isMonitorDataAvailability()).isTrue();
      assertThat(props.getMonitorDataAvailabilityInterval()).hasMillis(250);
      assertThat(props.isPerformReadBenchmarks()).isFalse();
      assertThat(props.getDisabledQueriesList()).isEmpty();

      // starter
      final var starterCfg = props.getStarter();
      assertThat(starterCfg).isNotNull();

      assertThat(starterCfg.getProcessId()).isEqualTo("benchmark");
      assertThat(starterCfg.getRate()).isEqualTo(300.0);
      assertThat(starterCfg.getRateDuration()).hasSeconds(1);
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
      final var workerCfg = props.getWorker();
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
      assertThat(workerCfg.getMessageName()).isEqualTo("defaultMessage");
      assertThat(workerCfg.isSendMessage()).isFalse();
      assertThat(workerCfg.getCorrelationKeyVariableName()).isEqualTo("correlationKey-var");
    }
  }

  @Nested
  @SpringBootTest(classes = ConfigTest.TestApp.class)
  @ActiveProfiles("test-different")
  class DifferentConfigTest {

    @Autowired private LoadTesterProperties props;

    @Test
    public void shouldReadDifferentAppConfig() {
      // then
      assertThat(props.isMonitorDataAvailability()).isFalse();
      assertThat(props.getMonitorDataAvailabilityInterval()).hasMillis(50);
      assertThat(props.isPerformReadBenchmarks()).isTrue();
      assertThat(props.getDisabledQueriesList())
          .containsExactlyInAnyOrder("process_instances_active", "audit_log_by_category");

      // starter
      final var starterCfg = props.getStarter();
      assertThat(starterCfg).isNotNull();

      assertThat(starterCfg.getProcessId()).isEqualTo("benchmark");
      assertThat(starterCfg.getRate()).isEqualTo(30.5);
      assertThat(starterCfg.getRateDuration()).hasMinutes(5);
      assertThat(starterCfg.getRatePerSecond()).isCloseTo(30.5 / 300.0, within(1e-9));
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
      final var workerCfg = props.getWorker();
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

  @Test
  public void shouldConvertFractionalRateWithCustomDuration() {
    // given
    final var starterCfg = new StarterProperties();
    starterCfg.setRate(30.5);
    starterCfg.setRateDuration(Duration.ofMinutes(1));

    // when
    final double ratePerSecond = starterCfg.getRatePerSecond();

    // then
    assertThat(ratePerSecond).isCloseTo(30.5 / 60.0, within(1e-9));
  }

  @Test
  public void shouldReturnRateUnchangedForOneSecondDuration() {
    // given
    final var starterCfg = new StarterProperties();
    starterCfg.setRate(0.5);
    starterCfg.setRateDuration(Duration.ofSeconds(1));

    // when
    final double ratePerSecond = starterCfg.getRatePerSecond();

    // then
    assertThat(ratePerSecond).isEqualTo(0.5);
  }

  @Test
  public void shouldParseDisabledQueriesFromCommaSeparatedString() {
    // given
    final var props = new LoadTesterProperties();
    props.setDisabledQueries("query_a, query_b ,query_c");

    // when
    final var result = props.getDisabledQueriesList();

    // then
    assertThat(result).containsExactly("query_a", "query_b", "query_c");
  }

  @Test
  public void shouldReturnEmptyListForBlankDisabledQueries() {
    // given
    final var props = new LoadTesterProperties();
    props.setDisabledQueries(",,");

    // when
    final var result = props.getDisabledQueriesList();

    // then
    assertThat(result).isEmpty();
  }
}
