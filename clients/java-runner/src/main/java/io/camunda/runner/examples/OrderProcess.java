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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Shared order-flow process definition and lambda handlers used by the three demos:
 *
 * <ol>
 *   <li>{@link OrderDemo} — inline-lambda form (lambdas defined alongside service tasks)
 *   <li>{@link OrderDemoBindings} — binding-API form on the same process, lambdas attached by id
 *   <li>{@link LoadDemo} — same process at scale with paced creation
 * </ol>
 *
 * <p>Keeping one shared definition makes the three demos directly comparable: the BPMN topology and
 * the worker logic are identical, only the wiring differs.
 */
public final class OrderProcess {

  public static final String PROCESS_ID = "order";

  private OrderProcess() {}

  /** The BPMN model: {@code start -> validate -> charge -> ship -> end}. */
  public static BpmnModelInstance model() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent("received")
        .serviceTask("validate", t -> t.zeebeJobType("validate"))
        .serviceTask("charge", t -> t.zeebeJobType("charge"))
        .serviceTask("ship", t -> t.zeebeJobType("ship"))
        .endEvent("delivered")
        .done();
  }

  // --------------------------------------------------------------------------
  // Realistic-ish handlers: simulate I/O latency, decide things based on data,
  // emit non-trivial output variables. None of these block forever.
  // --------------------------------------------------------------------------

  /**
   * Validate the order: amount must be positive and below an arbitrary risk threshold. Sleeps
   * briefly to simulate a database lookup.
   */
  public static final Function<Job, Map<String, Object>> VALIDATE =
      job -> {
        final String orderId = job.variable("orderId", String.class);
        final String customerId = job.variable("customerId", String.class);
        final double amount = job.variable("amount", Number.class).doubleValue();

        sleep(20 + ThreadLocalRandom.current().nextInt(30)); // ~20-50ms "DB lookup"

        final boolean valid = amount > 0 && amount < 10_000;
        final String reason = valid ? "ok" : (amount <= 0 ? "non-positive amount" : "over limit");

        System.out.printf(
            "[validate] order=%s customer=%s amount=%.2f -> %s%n",
            orderId, customerId, amount, reason);
        return Map.of("valid", valid, "validationReason", reason);
      };

  /**
   * Charge the customer: derives a payment id from the order id, picks a payment method based on
   * amount, and records a timestamp. Sleeps to simulate a payment-gateway call.
   */
  public static final Function<Job, Map<String, Object>> CHARGE =
      job -> {
        final String orderId = job.variable("orderId", String.class);
        final double amount = job.variable("amount", Number.class).doubleValue();

        sleep(50 + ThreadLocalRandom.current().nextInt(100)); // ~50-150ms "gateway"

        final String paymentMethod = amount >= 500 ? "credit-card" : "wallet";
        final String paymentId = "P-" + orderId + "-" + System.currentTimeMillis();

        System.out.printf(
            "[charge]   order=%s amount=%.2f via %s -> %s%n",
            orderId, amount, paymentMethod, paymentId);

        // Use a HashMap rather than Map.of to allow the optional carrier hint (null-safe).
        final Map<String, Object> out = new HashMap<>();
        out.put("paymentId", paymentId);
        out.put("paymentMethod", paymentMethod);
        out.put("chargedAt", java.time.Instant.now().toString());
        return out;
      };

  /**
   * Ship the order: chooses a carrier based on the amount (express above 500), generates a tracking
   * id, simulates handing off to the warehouse.
   */
  public static final Function<Job, Map<String, Object>> SHIP =
      job -> {
        final String orderId = job.variable("orderId", String.class);
        final String paymentId = job.variable("paymentId", String.class);
        final double amount = job.variable("amount", Number.class).doubleValue();

        sleep(30 + ThreadLocalRandom.current().nextInt(40)); // ~30-70ms "warehouse"

        final String carrier = amount >= 500 ? "Express-DHL" : "Standard-Post";
        final String trackingId = carrier.substring(0, 1) + "-" + job.getProcessInstanceKey();

        System.out.printf(
            "[ship]     order=%s payment=%s -> %s (%s)%n", orderId, paymentId, trackingId, carrier);
        return Map.of("trackingId", trackingId, "carrier", carrier);
      };

  // --------------------------------------------------------------------------
  // Per-instance variable generator. Produces realistic-looking inputs so the
  // handlers above have something interesting to chew on.
  // --------------------------------------------------------------------------

  /**
   * Generates a map of initial process variables for instance {@code i}. The amount is chosen so
   * roughly 1-in-5 instances exceeds the 500 "express" threshold.
   */
  public static final IntFunction<Map<String, Object>> INITIAL_VARIABLES =
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
}
