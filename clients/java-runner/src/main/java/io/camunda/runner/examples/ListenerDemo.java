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

import static io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType.end;
import static io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType.start;

import io.camunda.runner.Job;
import io.camunda.runner.LiveBpmn;
import io.camunda.runner.RunOptions;
import java.time.Duration;
import java.util.Map;

/**
 * Listener demo: a service task with execution listeners on both lifecycle events plus a downstream
 * service task that consumes a variable produced by the body lambda.
 *
 * <pre>
 *   start -> greet -> print -> end
 * </pre>
 *
 * <p>Listener attachments use the unified {@code .on(eventType, lambda)} surface (Java's enum-typed
 * overload resolution picks execution-listener vs task-listener). Task listeners on user tasks use
 * the same {@code .on(...)} method with a {@link
 * io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType} constant — see the README
 * for the binding form.
 */
public final class ListenerDemo {

  private ListenerDemo() {}

  public static void main(final String[] args) throws Exception {
    try (final var cluster = LiveBpmn.cluster().localhost()) {
      LiveBpmn.createExecutableProcess("hello")
          .startEvent()
          .serviceTask(
              "greet", (Job job) -> Map.of("greeting", "hi " + job.variable("name", String.class)))
          .listeners(
              l ->
                  l.on(start, (Job job) -> System.out.println("[el-start] greet activating"))
                      .on(end, (Job job) -> System.out.println("[el-end]   greet completed")))
          .serviceTask(
              "print", (Job job) -> System.out.println(job.variable("greeting", String.class)))
          .endEvent()
          .run(RunOptions.of(2).variables(i -> Map.of("name", "World " + i)), cluster)
          .await(Duration.ofMinutes(1));
    }
  }
}
