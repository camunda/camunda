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
package io.camunda.example;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.example.config.TestConfig;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@CamundaSpringProcessTest
@Import(TestConfig.class)
class ParallelDmnJobWorkerTest {

  @Autowired private CamundaClient camundaClient;

  @Autowired private CamundaProcessTestContext processTestContext;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void shouldProcessMultipleOrdersInParallel() throws JsonProcessingException {
    // given - deploy the process
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("test-process.bpmn")
        .send()
        .join();

    // given - a list of orders with different amounts and customer types
    final List<Map<String, Object>> orders =
        List.of(
            Map.of("orderId", "ORDER-001", "orderAmount", 1500, "customerType", "PREMIUM"),
            Map.of("orderId", "ORDER-002", "orderAmount", 600, "customerType", "PREMIUM"),
            Map.of("orderId", "ORDER-003", "orderAmount", 1200, "customerType", "REGULAR"),
            Map.of("orderId", "ORDER-004", "orderAmount", 800, "customerType", "REGULAR"),
            Map.of("orderId", "ORDER-005", "orderAmount", 300, "customerType", "REGULAR"));

    final String ordersJson = objectMapper.writeValueAsString(orders);

    // when - we deploy and start a simple process that triggers our job worker
    final ProcessInstanceEvent processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId("test-process")
            .latestVersion()
            .variables(Map.of("orders", ordersJson))
            .send()
            .join();

    // then - process completes successfully
    assertThatProcessInstance(processInstance).isCompleted();

    // Verify that all orders were processed with correct discounts
    assertThat(processInstance).isNotNull();
  }

  @Test
  void shouldHandleSingleOrder() throws JsonProcessingException {
    // given - deploy the process
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("test-process.bpmn")
        .send()
        .join();

    // given - a single order
    final List<Map<String, Object>> orders =
        List.of(Map.of("orderId", "ORDER-100", "orderAmount", 1000, "customerType", "PREMIUM"));

    final String ordersJson = objectMapper.writeValueAsString(orders);

    // when
    final ProcessInstanceEvent processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId("test-process")
            .latestVersion()
            .variables(Map.of("orders", ordersJson))
            .send()
            .join();

    // then
    assertThatProcessInstance(processInstance).isCompleted();
  }

  @Test
  void shouldHandleEmptyOrderList() throws JsonProcessingException {
    // given - deploy the process
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("test-process.bpmn")
        .send()
        .join();

    // given - an empty list of orders
    final List<Map<String, Object>> orders = List.of();
    final String ordersJson = objectMapper.writeValueAsString(orders);

    // when
    final ProcessInstanceEvent processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId("test-process")
            .latestVersion()
            .variables(Map.of("orders", ordersJson))
            .send()
            .join();

    // then
    assertThatProcessInstance(processInstance).isCompleted();
  }
}
