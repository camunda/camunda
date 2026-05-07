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

import io.camunda.runner.Cluster;
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
import java.util.function.IntFunction;

/**
 * Two ways to wire the same order flow, side by side. Run with {@code inline} (default) or {@code
 * bindings} as the program argument:
 *
 * <pre>
 *   java io.camunda.runner.examples.OrderDemos            # inline (default)
 *   java io.camunda.runner.examples.OrderDemos bindings   # binding API
 * </pre>
 *
 * <p>The two forms appear directly below each other so they read side by side. They produce
 * identical behaviour and identical Operate output — only the wiring differs.
 */
public final class OrderDemos {

  private OrderDemos() {}

  /** Per-instance input variables. ~1-in-5 instances trips the express path (amount ≥ 500). */
  private static final IntFunction<Map<String, Object>> INITIAL_VARIABLES =
      i -> {
        final double amount = i % 5 == 0 ? 750.00 + i : 49.99 + i;
        return Map.of(
            "orderId",
            String.format("ORDER-%04d", 1000 + i),
            "customerId",
            "CUST-" + (100 + (i % 7)),
            "amount",
            amount);
      };

  public static void main(final String[] args) throws Exception {
    final String mode = args.length > 0 ? args[0] : "inline";
    try (var cluster = LiveBpmn.cluster().testcontainer()) {
      final Run run =
          switch (mode) {
            case "bindings" -> Bindings.run(cluster);
            case "inline" -> Inline.run(cluster);
            default ->
                throw new IllegalArgumentException(
                    "unknown mode '" + mode + "' — use 'inline' or 'bindings'");
          };
      System.out.println("Operate: " + run.operateUrl());
      run.await(Duration.ofMinutes(2));
      System.out.println("done; handled per task = " + run.workersHandled());
    }
  }

  // =========================================================================
  // INLINE form — lambda bodies sit at each service task, in one fluent chain
  // =========================================================================
  static final class Inline {

    private Inline() {}

    static Run run(final Cluster cluster) {
      return LiveBpmn.createExecutableProcess("order")
          .startEvent("received")
          .serviceTask(
              "validate",
              (Job job) -> {
                final String orderId = job.variable("orderId", String.class);
                final String customerId = job.variable("customerId", String.class);
                final double amount = job.variable("amount", Number.class).doubleValue();
                sleep(20 + ThreadLocalRandom.current().nextInt(30));
                final boolean valid = amount > 0 && amount < 10_000;
                final String reason =
                    valid ? "ok" : (amount <= 0 ? "non-positive amount" : "over limit");
                System.out.printf(
                    "[validate] order=%s customer=%s amount=%.2f -> %s%n",
                    orderId, customerId, amount, reason);
                return Map.of("valid", valid, "validationReason", reason);
              })
          .serviceTask(
              "charge",
              (Job job) -> {
                final String orderId = job.variable("orderId", String.class);
                final double amount = job.variable("amount", Number.class).doubleValue();
                sleep(50 + ThreadLocalRandom.current().nextInt(100));
                final String paymentMethod = amount >= 500 ? "credit-card" : "wallet";
                final String paymentId = "P-" + orderId + "-" + System.currentTimeMillis();
                System.out.printf(
                    "[charge]   order=%s amount=%.2f via %s -> %s%n",
                    orderId, amount, paymentMethod, paymentId);
                final Map<String, Object> out = new HashMap<>();
                out.put("paymentId", paymentId);
                out.put("paymentMethod", paymentMethod);
                out.put("chargedAt", Instant.now().toString());
                return out;
              })
          .serviceTask(
              "ship",
              (Job job) -> {
                final String orderId = job.variable("orderId", String.class);
                final String paymentId = job.variable("paymentId", String.class);
                final double amount = job.variable("amount", Number.class).doubleValue();
                sleep(30 + ThreadLocalRandom.current().nextInt(40));
                final String carrier = amount >= 500 ? "Express-DHL" : "Standard-Post";
                final String trackingId =
                    carrier.substring(0, 1) + "-" + job.getProcessInstanceKey();
                System.out.printf(
                    "[ship]     order=%s payment=%s -> %s (%s)%n",
                    orderId, paymentId, trackingId, carrier);
                return Map.of("trackingId", trackingId, "carrier", carrier);
              })
          .endEvent("delivered")
          .run(RunOptions.of(3).variables(INITIAL_VARIABLES), cluster);
    }
  }

  // =========================================================================
  // BINDING form — build the model, then attach lambdas by element id
  // =========================================================================
  static final class Bindings {

    private Bindings() {}

    static Run run(final Cluster cluster) {
      final BpmnModelInstance model =
          Bpmn.createExecutableProcess("order")
              .startEvent("received")
              .serviceTask("validate", t -> t.zeebeJobType("validate"))
              .serviceTask("charge", t -> t.zeebeJobType("charge"))
              .serviceTask("ship", t -> t.zeebeJobType("ship"))
              .endEvent("delivered")
              .done();

      return LiveBpmn.of(model)
          .bind("validate", Bindings::validate)
          .bind("charge", Bindings::charge)
          .bind("ship", Bindings::ship)
          .run(RunOptions.of(3).variables(INITIAL_VARIABLES), cluster);
    }

    static Map<String, Object> validate(final Job job) {
      final String orderId = job.variable("orderId", String.class);
      final String customerId = job.variable("customerId", String.class);
      final double amount = job.variable("amount", Number.class).doubleValue();
      sleep(20 + ThreadLocalRandom.current().nextInt(30));
      final boolean valid = amount > 0 && amount < 10_000;
      final String reason = valid ? "ok" : (amount <= 0 ? "non-positive amount" : "over limit");
      System.out.printf(
          "[validate] order=%s customer=%s amount=%.2f -> %s%n",
          orderId, customerId, amount, reason);
      return Map.of("valid", valid, "validationReason", reason);
    }

    static Map<String, Object> charge(final Job job) {
      final String orderId = job.variable("orderId", String.class);
      final double amount = job.variable("amount", Number.class).doubleValue();
      sleep(50 + ThreadLocalRandom.current().nextInt(100));
      final String paymentMethod = amount >= 500 ? "credit-card" : "wallet";
      final String paymentId = "P-" + orderId + "-" + System.currentTimeMillis();
      System.out.printf(
          "[charge]   order=%s amount=%.2f via %s -> %s%n",
          orderId, amount, paymentMethod, paymentId);
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
          "[ship]     order=%s payment=%s -> %s (%s)%n", orderId, paymentId, trackingId, carrier);
      return Map.of("trackingId", trackingId, "carrier", carrier);
    }
  }

  private static void sleep(final long ms) {
    try {
      Thread.sleep(ms);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
