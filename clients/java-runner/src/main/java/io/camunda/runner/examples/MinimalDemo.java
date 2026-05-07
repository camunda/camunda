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
import io.camunda.runner.RunOptions;
import java.time.Duration;
import java.util.Map;

/**
 * The smallest demo: one service task, inline lambda, showing the job-control verbs ({@code
 * job.variable}, {@code job.complete(map)}, {@code job.fail(reason)}) in their full beauty.
 *
 * <p>This is the "look at how this reads" example. Compare with {@link OrderDemos} for a real
 * multi-task flow.
 */
public final class MinimalDemo {

  private MinimalDemo() {}

  public static void main(final String[] args) throws Exception {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");

    try (var cluster = LiveBpmn.cluster().testcontainer()) {
      LiveBpmn.createExecutableProcess("hello")
          .startEvent()
          .serviceTask(
              "greet",
              (Job job) -> {
                final String name = job.variable("name", String.class);
                if (name == null || name.isBlank()) {
                  job.fail("missing 'name' variable");
                  return;
                }
                System.out.println(
                    "hello, " + name + "! (instance " + job.getProcessInstanceKey() + ")");
                job.complete(Map.of("greeting", "hello, " + name));
              })
          .endEvent()
          .run(
              RunOptions.of(3)
                  .variables(
                      i ->
                          Map.of(
                              "name",
                              switch (i) {
                                case 0 -> "Stephan";
                                case 1 -> "Anna";
                                default -> "World";
                              })),
              cluster)
          .await(Duration.ofMinutes(1));
    }
  }
}
