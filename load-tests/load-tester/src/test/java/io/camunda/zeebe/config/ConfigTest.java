/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    // streaming, timeout are configured via camunda.client.zeebe.defaults.*)
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

    @Test
    void shouldReadDifferentAppConfig() {
      // given / when - overrides loaded from different-application.properties

      // then
      assertThat(properties.isMonitorDataAvailability()).isFalse();
      assertThat(properties.getMonitorDataAvailabilityInterval()).hasMillis(50);

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
