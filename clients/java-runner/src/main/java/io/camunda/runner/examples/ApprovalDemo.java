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

/**
 * Exclusive gateway with two branches: review computes an {@code approved} flag; flow forks on a
 * FEEL condition into a "ship" or "reject" path.
 */
public final class ApprovalDemo {

  private ApprovalDemo() {}

  public static void main(final String[] args) throws Exception {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");

    System.out.println("[ApprovalDemo] booting cluster…");
    try (var cluster = LiveBpmn.cluster().testcontainer()) {
      final Run run =
          LiveBpmn.createExecutableProcess("approval")
              .startEvent()
              .serviceTask(
                  "review",
                  (Job job) -> {
                    final double amount = job.variable("amount", Number.class).doubleValue();
                    final boolean approved = amount < 1000;
                    System.out.println("[review] amount=" + amount + " approved=" + approved);
                    return Map.of("approved", approved);
                  })
              .exclusiveGateway("decision")
              .condition("=approved = true")
              .serviceTask(
                  "ship",
                  (Job job) -> {
                    System.out.println("[ship] approved -> shipping");
                    return Map.of("status", "shipped");
                  })
              .endEvent()
              .moveToNode("decision")
              .condition("=approved = false")
              .serviceTask(
                  "reject",
                  (Job job) -> {
                    System.out.println("[reject] amount too high -> rejecting");
                    return Map.of("status", "rejected");
                  })
              .endEvent()
              .run(
                  RunOptions.of(4).variables(i -> Map.of("amount", 100.0 * (i + 1) * (i + 1))),
                  cluster);
      System.out.println("Operate: " + run.operateUrl());
      run.await(Duration.ofMinutes(2));
      System.out.println("done; handled = " + run.workersHandled());
    }
  }
}
