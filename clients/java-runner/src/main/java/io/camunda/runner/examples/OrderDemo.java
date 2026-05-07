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
import java.time.Duration;
import java.util.Map;

/** Hackday demo entry point — boots a Testcontainer and runs 5 instances. */
public final class OrderDemo {

  private OrderDemo() {}

  public static void main(final String[] args) throws Exception {
    try (var cluster = LiveBpmn.cluster().testcontainer()) {
      final var run =
          LiveBpmn.createExecutableProcess("order")
              .startEvent()
              .serviceTask("validate", (Job job) -> Map.of("valid", true))
              .serviceTask(
                  "ship",
                  (Job job) -> {
                    System.out.println("shipping for instance " + job.getProcessInstanceKey());
                    return Map.of("trackingId", "T-" + job.getProcessInstanceKey());
                  })
              .endEvent()
              .run(5, cluster);
      run.await(Duration.ofMinutes(2));
      System.out.println("done; handled = " + run.workersHandled());
    }
  }
}
