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
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;

/**
 * Self-contained scale demo: 50 instances of a small two-step flow, paced 100 ms apart so they
 * trickle into Operate visibly instead of all completing in milliseconds.
 *
 * <pre>
 *   start -> enrich -> notify -> end
 * </pre>
 *
 * <p>Independent from {@link OrderDemos} — its own model, its own handlers, its own variable
 * generator. The point here is throughput, not realism. {@code workersHandled} should always read
 * 50 per task at the end.
 */
public final class LoadDemo {

  private LoadDemo() {}

  private static final int INSTANCES = 50;
  private static final Duration PACING = Duration.ofMillis(100);

  /** Per-instance generator producing a distinct payload per item. */
  private static final IntFunction<Map<String, Object>> INITIAL_VARIABLES =
      i -> Map.of("itemId", "ITEM-" + i, "priority", i % 3 == 0 ? "high" : "normal");

  public static void main(final String[] args) throws Exception {
    final AtomicLong enriched = new AtomicLong();
    final AtomicLong notified = new AtomicLong();

    System.out.println("[LoadDemo] booting cluster (first run pulls the image, ~1-2 min)…");
    try (var cluster = LiveBpmn.cluster().testcontainer()) {
      System.out.println(
          "[LoadDemo] cluster ready, firing " + INSTANCES + " instances at " + PACING + " each…");

      final Run run =
          LiveBpmn.createExecutableProcess("load")
              .startEvent()
              .serviceTask(
                  "enrich",
                  (Job job) -> {
                    sleep(20 + ThreadLocalRandom.current().nextInt(30));
                    final long n = enriched.incrementAndGet();
                    if (n % 10 == 0) {
                      System.out.println("[enrich] " + n + "/" + INSTANCES);
                    }
                    return Map.of("score", ThreadLocalRandom.current().nextInt(100));
                  })
              .serviceTask(
                  "notify",
                  (Job job) -> {
                    sleep(10 + ThreadLocalRandom.current().nextInt(20));
                    final long n = notified.incrementAndGet();
                    if (n % 10 == 0) {
                      System.out.println("[notify] " + n + "/" + INSTANCES);
                    }
                  })
              .endEvent()
              .run(RunOptions.of(INSTANCES).pacing(PACING).variables(INITIAL_VARIABLES), cluster);

      System.out.println("Operate: " + run.operateUrl());
      run.await(Duration.ofMinutes(5));
      summarise(run.workersHandled());
    }
  }

  private static void summarise(final Map<String, Long> handled) {
    System.out.println("done; handled per task = " + handled);
    final long total = handled.values().stream().mapToLong(Long::longValue).sum();
    System.out.println("total job completions = " + total + " (expected " + (INSTANCES * 2) + ")");
  }

  private static void sleep(final long ms) {
    try {
      Thread.sleep(ms);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
