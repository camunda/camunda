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

import io.camunda.runner.LiveBpmn;
import io.camunda.runner.Run;
import io.camunda.runner.RunOptions;
import java.time.Duration;
import java.util.Map;

/**
 * Binding-API counterpart to {@link OrderDemo}: same {@link OrderProcess#model() BPMN process},
 * same {@link OrderProcess#VALIDATE validate} / {@link OrderProcess#CHARGE charge} / {@link
 * OrderProcess#SHIP ship} handler logic, same {@link OrderProcess#INITIAL_VARIABLES initial
 * variables}.
 *
 * <p>The only difference is the wiring: the BPMN definition is built up-front (e.g. lifted from
 * existing production code that uses vanilla {@code Bpmn.createExecutableProcess(...)}), then
 * lambdas are attached by element id via {@code .bind(...)}.
 *
 * <p>Pick this form when the BPMN definition already lives somewhere else in your codebase, or when
 * separating the topology from the worker logic reads better at scale.
 */
public final class OrderDemoBindings {

  private OrderDemoBindings() {}

  public static void main(final String[] args) throws Exception {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");

    System.out.println(
        "[OrderDemoBindings] booting cluster (first run pulls the image, ~1-2 min)…");
    try (var cluster = LiveBpmn.cluster().testcontainer()) {
      System.out.println(
          "[OrderDemoBindings] cluster ready, binding workers & creating instances…");

      final Run run =
          LiveBpmn.of(OrderProcess.model())
              .bind("validate", OrderProcess.VALIDATE)
              .bind("charge", OrderProcess.CHARGE)
              .bind("ship", OrderProcess.SHIP)
              .run(RunOptions.of(3).variables(OrderProcess.INITIAL_VARIABLES), cluster);

      System.out.println("Operate: " + run.operateUrl());
      run.await(Duration.ofMinutes(2));
      summarise(run.workersHandled());
    }
  }

  private static void summarise(final Map<String, Long> handled) {
    System.out.println("done; handled per task = " + handled);
  }
}
