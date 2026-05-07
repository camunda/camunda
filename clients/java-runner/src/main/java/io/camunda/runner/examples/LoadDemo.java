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
 * Same {@link OrderProcess#model() order process} and {@link OrderProcess#VALIDATE handlers} as
 * {@link OrderDemo} / {@link OrderDemoBindings}, just at scale: 50 instances paced 100 ms apart so
 * they trickle into Operate visibly instead of all completing in milliseconds.
 *
 * <p>This is also a stress check that the variable generator, worker pool, and the broker handle
 * concurrent in-flight instances without dropping anything. The {@code workersHandled} summary at
 * the end should always read {@code 50} per task.
 */
public final class LoadDemo {

  private LoadDemo() {}

  private static final int INSTANCES = 50;
  private static final Duration PACING = Duration.ofMillis(100);

  public static void main(final String[] args) throws Exception {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");

    System.out.println("[LoadDemo] booting cluster (first run pulls the image, ~1-2 min)…");
    try (var cluster = LiveBpmn.cluster().testcontainer()) {
      System.out.println(
          "[LoadDemo] cluster ready, firing " + INSTANCES + " instances at " + PACING + " each…");

      final Run run =
          LiveBpmn.of(OrderProcess.model())
              .bind("validate", OrderProcess.VALIDATE)
              .bind("charge", OrderProcess.CHARGE)
              .bind("ship", OrderProcess.SHIP)
              .run(
                  RunOptions.of(INSTANCES).pacing(PACING).variables(OrderProcess.INITIAL_VARIABLES),
                  cluster);

      System.out.println("Operate: " + run.operateUrl());
      run.await(Duration.ofMinutes(5));
      summarise(run.workersHandled());
    }
  }

  private static void summarise(final Map<String, Long> handled) {
    System.out.println("done; handled per task = " + handled);
    final long total = handled.values().stream().mapToLong(Long::longValue).sum();
    System.out.println("total job completions = " + total + " (expected " + (INSTANCES * 3) + ")");
  }
}
