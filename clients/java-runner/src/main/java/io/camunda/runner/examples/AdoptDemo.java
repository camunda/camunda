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
package io.camunda.runner.examples;

import io.camunda.runner.Job;
import io.camunda.runner.LiveBpmn;
import io.camunda.runner.Run;
import io.camunda.runner.RunOptions;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.Map;

/**
 * Same order flow as {@link OrderDemo} — but the workers are attached via the <em>binding</em> API
 * instead of inline lambdas.
 *
 * <p>This is the migration story for existing code: you already have a {@link BpmnModelInstance}
 * built with vanilla {@code Bpmn.createExecutableProcess(...)}; LiveBpmn adopts it without any
 * builder rewrite, and {@code .bind(elementId, lambda)} hooks lambdas onto each service task by id.
 *
 * <p>Compare with {@link OrderDemo} (inline lambdas) and pick the form that fits your codebase:
 * binding keeps the BPMN definition in one place (often a constant or a separate factory) and keeps
 * the worker code in another, which scales better for large processes.
 */
public final class AdoptDemo {

  private AdoptDemo() {}

  public static void main(final String[] args) throws Exception {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");

    // ---- Step 1: existing BPMN model (e.g. one you already have in production code) ----
    final BpmnModelInstance orderModel =
        Bpmn.createExecutableProcess("order")
            .startEvent()
            .serviceTask("validate", t -> t.zeebeJobType("validate"))
            .serviceTask("charge", t -> t.zeebeJobType("charge"))
            .serviceTask("ship", t -> t.zeebeJobType("ship"))
            .endEvent()
            .done();

    System.out.println("[AdoptDemo] booting cluster (first run pulls the image, ~1-2 min)…");
    try (var cluster = LiveBpmn.cluster().testcontainer()) {
      System.out.println("[AdoptDemo] cluster ready, binding workers & creating instances…");

      // ---- Step 2: bind worker lambdas to elements by id, then run ----
      final Run run =
          LiveBpmn.of(orderModel)
              .bind(
                  "validate",
                  (Job job) -> {
                    final String orderId = job.variable("orderId", String.class);
                    final double amount = job.variable("amount", Number.class).doubleValue();
                    System.out.println("[validate] order=" + orderId + " amount=" + amount);
                    return Map.of("valid", amount > 0);
                  })
              .bind(
                  "charge",
                  (Job job) -> {
                    final String orderId = job.variable("orderId", String.class);
                    System.out.println("[charge]   charging " + orderId);
                    return Map.of("paymentId", "P-" + orderId);
                  })
              .bind(
                  "ship",
                  (Job job) -> {
                    final String paymentId = job.variable("paymentId", String.class);
                    System.out.println("[ship]     payment=" + paymentId);
                    return Map.of("trackingId", "T-" + job.getProcessInstanceKey());
                  })
              .run(
                  RunOptions.of(3)
                      .variables(
                          i -> Map.of("orderId", "ORDER-" + (1000 + i), "amount", 19.99 + i)),
                  cluster);

      System.out.println("Operate: " + run.operateUrl());
      run.await(Duration.ofMinutes(2));
      System.out.println("done; handled = " + run.workersHandled());
    }
  }
}
