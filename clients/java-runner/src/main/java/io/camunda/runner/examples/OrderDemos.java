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
import java.util.function.IntFunction;

/**
 * Two side-by-side demos of the same order flow:
 *
 * <ul>
 *   <li>{@link Inline#main} — fluent chain with the lambda <em>at</em> each service task.
 *   <li>{@link Bindings#main} — same flow, but lambdas attached by element id via {@code
 *       LiveBpmn.of(model).bind(...)}. The migration story for code that already builds a {@link
 *       BpmnModelInstance} with vanilla {@code Bpmn.createExecutableProcess(...)}.
 * </ul>
 *
 * <p>Both share the handlers and the initial-variable generator below so behaviour is
 * apples-to-apples — only the wiring differs.
 *
 * <p>Run either by right-clicking {@link Inline} or {@link Bindings} in your IDE.
 */
public final class OrderDemos {

  private OrderDemos() {}

  public static final String PROCESS_ID = "order";

  // --------------------------------------------------------------------------
  // Shared BPMN model
  // --------------------------------------------------------------------------

  static BpmnModelInstance model() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent("received")
        .serviceTask("validate", t -> t.zeebeJobType("validate"))
        .serviceTask("charge", t -> t.zeebeJobType("charge"))
        .serviceTask("ship", t -> t.zeebeJobType("ship"))
        .endEvent("delivered")
        .done();
  }

  // --------------------------------------------------------------------------
  // Shared handlers (realistic: simulate I/O latency, branch on data, emit
  // multiple output variables). Used as method references by both demos.
  // --------------------------------------------------------------------------

  static Map<String, Object> validate(final Job job) {
    final String orderId = job.variable("orderId", String.class);
    final String customerId = job.variable("customerId", String.class);
    final double amount = job.variable("amount", Number.class).doubleValue();

    sleep(20 + ThreadLocalRandom.current().nextInt(30)); // ~20-50 ms "DB lookup"

    final boolean valid = amount > 0 && amount < 10_000;
    final String reason = valid ? "ok" : (amount <= 0 ? "non-positive amount" : "over limit");

    System.out.printf(
        "[validate] order=%s customer=%s amount=%.2f -> %s%n", orderId, customerId, amount, reason);
    return Map.of("valid", valid, "validationReason", reason);
  }

  static Map<String, Object> charge(final Job job) {
    final String orderId = job.variable("orderId", String.class);
    final double amount = job.variable("amount", Number.class).doubleValue();

    sleep(50 + ThreadLocalRandom.current().nextInt(100)); // ~50-150 ms "payment gateway"

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

    sleep(30 + ThreadLocalRandom.current().nextInt(40)); // ~30-70 ms "warehouse"

    final String carrier = amount >= 500 ? "Express-DHL" : "Standard-Post";
    final String trackingId = carrier.substring(0, 1) + "-" + job.getProcessInstanceKey();

    System.out.printf(
        "[ship]     order=%s payment=%s -> %s (%s)%n", orderId, paymentId, trackingId, carrier);
    return Map.of("trackingId", trackingId, "carrier", carrier);
  }

  /** Per-instance variable generator. ~1-in-5 instances trips the express path. */
  static final IntFunction<Map<String, Object>> INITIAL_VARIABLES =
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

  private static void sleep(final long ms) {
    try {
      Thread.sleep(ms);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  // --------------------------------------------------------------------------
  // Demo 1: inline-lambda API
  // --------------------------------------------------------------------------

  /** Build the process and attach lambdas in one fluent chain. */
  public static final class Inline {

    private Inline() {}

    public static void main(final String[] args) throws Exception {
      System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");

      try (var cluster = LiveBpmn.cluster().testcontainer()) {
        final Run run =
            LiveBpmn.createExecutableProcess(PROCESS_ID)
                .startEvent("received")
                .serviceTask("validate", OrderDemos::validate)
                .serviceTask("charge", OrderDemos::charge)
                .serviceTask("ship", OrderDemos::ship)
                .endEvent("delivered")
                .run(RunOptions.of(3).variables(INITIAL_VARIABLES), cluster);

        System.out.println("Operate: " + run.operateUrl());
        run.await(Duration.ofMinutes(2));
        System.out.println("done; handled per task = " + run.workersHandled());
      }
    }
  }

  // --------------------------------------------------------------------------
  // Demo 2: binding API (same handlers, attached by element id after the model
  // is built — useful when the BPMN definition lives separately).
  // --------------------------------------------------------------------------

  public static final class Bindings {

    private Bindings() {}

    public static void main(final String[] args) throws Exception {
      System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");

      try (var cluster = LiveBpmn.cluster().testcontainer()) {
        final Run run =
            LiveBpmn.of(model())
                .bind("validate", OrderDemos::validate)
                .bind("charge", OrderDemos::charge)
                .bind("ship", OrderDemos::ship)
                .run(RunOptions.of(3).variables(INITIAL_VARIABLES), cluster);

        System.out.println("Operate: " + run.operateUrl());
        run.await(Duration.ofMinutes(2));
        System.out.println("done; handled per task = " + run.workersHandled());
      }
    }
  }
}
