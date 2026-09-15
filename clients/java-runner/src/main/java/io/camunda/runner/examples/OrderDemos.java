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
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Two ways to wire the same order flow, side by side in one {@code main}.
 *
 * <pre>
 *   java io.camunda.runner.examples.OrderDemos            # inline (default)
 *   java io.camunda.runner.examples.OrderDemos bindings   # binding API
 * </pre>
 *
 * <p>Both branches use the same {@link Handlers handler methods} so they produce identical
 * behaviour — only the wiring differs.
 */
public final class OrderDemos {

  private OrderDemos() {}

  public static void main(final String[] args) throws Exception {
    final String mode = args.length > 0 ? args[0] : "inline";

    // Demo flow assumes a local cluster on localhost:26500 (start `c8run start` or
    // `docker compose up -d` first). Swap to .testcontainer() for a JVM-managed container.
    try (var cluster = LiveBpmn.cluster().localhost()) {

      final Run run =
          switch (mode) {

            // ---- INLINE form: build process and attach lambdas in one chain ----
            case "inline" ->
                LiveBpmn.createExecutableProcess("order")
                    .startEvent()
                    .serviceTask("validate", Handlers::validate)
                    .serviceTask("charge", Handlers::charge)
                    .serviceTask("ship", Handlers::ship)
                    .endEvent()
                    .run(RunOptions.of(3).variables(Handlers::initialVariables), cluster);

            // ---- BINDING form: build model first, attach lambdas by element id ----
            case "bindings" -> {
              final BpmnModelInstance model =
                  Bpmn.createExecutableProcess("order")
                      .startEvent()
                      .serviceTask("validate", t -> t.zeebeJobType("validate"))
                      .serviceTask("charge", t -> t.zeebeJobType("charge"))
                      .serviceTask("ship", t -> t.zeebeJobType("ship"))
                      .endEvent()
                      .done();
              yield LiveBpmn.of(model)
                  .bind("validate", Handlers::validate)
                  .bind("charge", Handlers::charge)
                  .bind("ship", Handlers::ship)
                  .run(RunOptions.of(3).variables(Handlers::initialVariables), cluster);
            }

            default ->
                throw new IllegalArgumentException(
                    "unknown mode '" + mode + "' — use 'inline' or 'bindings'");
          };

      System.out.println("Operate: " + run.operateUrl());
      run.await(Duration.ofMinutes(2));
      System.out.println("done; handled per task = " + run.workersHandled());
    }
  }

  /** Handler functions + per-instance variable generator. Used by both wiring branches above. */
  private static final class Handlers {

    private Handlers() {}

    /** Three orders with growing amounts — the third one (600) trips the express threshold. */
    static Map<String, Object> initialVariables(final int i) {
      return Map.of(
          "orderId", "ORDER-" + (i + 1),
          "customerId", "CUST-" + (i + 1),
          "amount", 200.0 * (i + 1));
    }

    static Map<String, Object> validate(final Job job) {
      final String orderId = job.variable("orderId", String.class);
      final double amount = job.variable("amount", Number.class).doubleValue();
      sleep(20 + ThreadLocalRandom.current().nextInt(30));
      final boolean valid = amount > 0 && amount < 10_000;
      System.out.printf("[validate] %s amount=%.2f -> %s%n", orderId, amount, valid ? "ok" : "no");
      return Map.of("valid", valid);
    }

    static Map<String, Object> charge(final Job job) {
      final String orderId = job.variable("orderId", String.class);
      final double amount = job.variable("amount", Number.class).doubleValue();
      sleep(50 + ThreadLocalRandom.current().nextInt(100));
      final String paymentMethod = amount >= 500 ? "credit-card" : "wallet";
      final String paymentId = "P-" + orderId;
      System.out.printf("[charge]   %s via %s -> %s%n", orderId, paymentMethod, paymentId);
      final Map<String, Object> out = new HashMap<>();
      out.put("paymentId", paymentId);
      out.put("paymentMethod", paymentMethod);
      out.put("chargedAt", Instant.now().toString());
      return out;
    }

    static Map<String, Object> ship(final Job job) {
      final String orderId = job.variable("orderId", String.class);
      final String paymentId = job.variable("paymentId", String.class);
      final double amount = job.variable("amount", Number.class).doubleValue();
      sleep(30 + ThreadLocalRandom.current().nextInt(40));
      final String carrier = amount >= 500 ? "Express-DHL" : "Standard-Post";
      final String trackingId = carrier.substring(0, 1) + "-" + job.getProcessInstanceKey();
      System.out.printf(
          "[ship]     %s payment=%s -> %s (%s)%n", orderId, paymentId, trackingId, carrier);
      return Map.of("trackingId", trackingId, "carrier", carrier);
    }

    private static void sleep(final long ms) {
      try {
        Thread.sleep(ms);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
