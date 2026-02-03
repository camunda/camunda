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
package io.camunda.example.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.zeebe.dmn.DecisionEngine;
import io.camunda.zeebe.dmn.DecisionEvaluationResult;
import io.camunda.zeebe.dmn.ParsedDecisionRequirementsGraph;
import io.camunda.zeebe.dmn.impl.VariablesContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.StructuredTaskScope;
import org.agrona.DirectBuffer;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ParallelDmnJobWorker {

  private static final Logger LOG = LoggerFactory.getLogger(ParallelDmnJobWorker.class);

  private final DecisionEngine decisionEngine;
  private final ParsedDecisionRequirementsGraph parsedDrg;
  private final ObjectMapper objectMapper;
  private final ObjectMapper msgPackMapper;

  public ParallelDmnJobWorker(
      final DecisionEngine decisionEngine,
      final ParsedDecisionRequirementsGraph parsedDrg,
      final ObjectMapper objectMapper) {
    this.decisionEngine = decisionEngine;
    this.parsedDrg = parsedDrg;
    this.objectMapper = objectMapper;
    this.msgPackMapper = new ObjectMapper(new MessagePackFactory());
  }

  @JobWorker(type = "process-orders")
  public Map<String, Object> processOrders(
      final JobClient client, final ActivatedJob job, @Variable("orders") final String ordersJson)
      throws Exception {

    LOG.info("Processing orders job with key: {}", job.getKey());

    // Parse the JSON list of orders
    final List<Map<String, Object>> orders =
        objectMapper.readValue(ordersJson, new TypeReference<>() {});

    LOG.info("Received {} orders to process", orders.size());

    // Process all orders in parallel using structured concurrency with virtual threads
    final List<Map<String, Object>> results = processOrdersInParallel(orders);

    LOG.info("Successfully processed {} orders", results.size());

    // Return the results as a map to be stored as process variables
    return Map.of("orderResults", results);
  }

  /**
   * Process orders in parallel using Java 21 structured concurrency with virtual threads. Each
   * order is evaluated against the DMN decision in its own virtual thread.
   */
  private List<Map<String, Object>> processOrdersInParallel(final List<Map<String, Object>> orders)
      throws Exception {

    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
      // Create a subtask for each order
      final List<StructuredTaskScope.Subtask<Map<String, Object>>> subtasks = new ArrayList<>();

      for (final Map<String, Object> order : orders) {
        final StructuredTaskScope.Subtask<Map<String, Object>> subtask =
            scope.fork(() -> processOrder(order));
        subtasks.add(subtask);
      }

      // Wait for all subtasks to complete or fail
      scope.join();
      scope.throwIfFailed();

      // Collect results from all subtasks
      final List<Map<String, Object>> results = new ArrayList<>();
      for (final StructuredTaskScope.Subtask<Map<String, Object>> subtask : subtasks) {
        results.add(subtask.get());
      }

      return results;
    }
  }

  /** Process a single order by evaluating it against the DMN decision. */
  private Map<String, Object> processOrder(final Map<String, Object> order) {
    final String orderId = (String) order.get("orderId");
    final Number orderAmount = (Number) order.get("orderAmount");
    final String customerType = (String) order.get("customerType");

    LOG.debug(
        "Processing order: {} with amount: {} and customer type: {}",
        orderId,
        orderAmount,
        customerType);

    // Create context for DMN evaluation
    final Map<String, Object> variables = new HashMap<>();
    variables.put("orderAmount", orderAmount);
    variables.put("customerType", customerType);

    // Evaluate the DMN decision
    final DecisionEvaluationResult evaluationResult =
        decisionEngine.evaluateDecisionById(
            parsedDrg, "calculate_discount", new VariablesContext(variables));

    final Map<String, Object> result = new HashMap<>(order);

    if (evaluationResult.isFailure()) {
      LOG.error("Failed to evaluate decision for order: {}", orderId);
      result.put("error", evaluationResult.getFailureMessage());
      result.put("discountPercentage", 0);
    } else {
      // Extract the discount percentage from the MessagePack result
      final DirectBuffer output = evaluationResult.getOutput();
      try {
        final Map<String, Object> outputMap = convertDirectBufferToMap(output);
        final Number discountPercentage = (Number) outputMap.get("discountPercentage");
        result.put("discountPercentage", discountPercentage);

        LOG.debug("Order {} calculated discount: {}%", orderId, discountPercentage);
      } catch (final Exception e) {
        LOG.error("Failed to parse DMN output for order: {}", orderId, e);
        result.put("error", "Failed to parse DMN output: " + e.getMessage());
        result.put("discountPercentage", 0);
      }
    }

    return result;
  }

  /**
   * Convert DirectBuffer (MessagePack format) to a Map. The DMN engine returns results in
   * MessagePack format, which we need to deserialize.
   */
  private Map<String, Object> convertDirectBufferToMap(final DirectBuffer buffer) throws Exception {
    final byte[] bytes = new byte[buffer.capacity()];
    buffer.getBytes(0, bytes);
    return msgPackMapper.readValue(bytes, new TypeReference<>() {});
  }
}
