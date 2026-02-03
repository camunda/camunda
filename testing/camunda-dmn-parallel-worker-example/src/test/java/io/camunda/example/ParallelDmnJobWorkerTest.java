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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@CamundaSpringProcessTest
class ParallelDmnJobWorkerTest {

  @Autowired private CamundaClient camundaClient;

  @Autowired private CamundaProcessTestContext processTestContext;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void shouldProcessMultipleOrdersInParallel() throws JsonProcessingException {
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

    // then - wait for the process to complete
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              processTestContext.assertProcessInstanceCompleted(processInstance.getProcessInstanceKey());
            });

    // and - verify that all orders were processed with correct discounts
    final Map<String, Object> variables =
        processTestContext.getProcessInstanceVariables(processInstance.getProcessInstanceKey());

    assertThat(variables).containsKey("orderResults");

    @SuppressWarnings("unchecked")
    final List<Map<String, Object>> results =
        (List<Map<String, Object>>) variables.get("orderResults");

    assertThat(results).hasSize(5);

    // Verify each order result
    final Map<String, Object> order1 = findOrderById(results, "ORDER-001");
    assertThat(order1).containsEntry("discountPercentage", 20); // PREMIUM >= 1000

    final Map<String, Object> order2 = findOrderById(results, "ORDER-002");
    assertThat(order2).containsEntry("discountPercentage", 15); // PREMIUM >= 500

    final Map<String, Object> order3 = findOrderById(results, "ORDER-003");
    assertThat(order3).containsEntry("discountPercentage", 10); // REGULAR >= 1000

    final Map<String, Object> order4 = findOrderById(results, "ORDER-004");
    assertThat(order4).containsEntry("discountPercentage", 5); // REGULAR >= 500

    final Map<String, Object> order5 = findOrderById(results, "ORDER-005");
    assertThat(order5).containsEntry("discountPercentage", 0); // < 500
  }

  @Test
  void shouldHandleSingleOrder() throws JsonProcessingException {
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
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              processTestContext.assertProcessInstanceCompleted(processInstance.getProcessInstanceKey());
            });

    final Map<String, Object> variables =
        processTestContext.getProcessInstanceVariables(processInstance.getProcessInstanceKey());

    @SuppressWarnings("unchecked")
    final List<Map<String, Object>> results =
        (List<Map<String, Object>>) variables.get("orderResults");

    assertThat(results).hasSize(1);
    assertThat(results.get(0)).containsEntry("orderId", "ORDER-100").containsEntry("discountPercentage", 20);
  }

  @Test
  void shouldHandleEmptyOrderList() throws JsonProcessingException {
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
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              processTestContext.assertProcessInstanceCompleted(processInstance.getProcessInstanceKey());
            });

    final Map<String, Object> variables =
        processTestContext.getProcessInstanceVariables(processInstance.getProcessInstanceKey());

    @SuppressWarnings("unchecked")
    final List<Map<String, Object>> results =
        (List<Map<String, Object>>) variables.get("orderResults");

    assertThat(results).isEmpty();
  }

  private Map<String, Object> findOrderById(
      final List<Map<String, Object>> results, final String orderId) {
    return results.stream()
        .filter(order -> orderId.equals(order.get("orderId")))
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("Order with id " + orderId + " not found in results"));
  }
}
