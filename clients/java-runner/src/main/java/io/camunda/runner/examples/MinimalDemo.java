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
import java.util.List;
import java.util.Map;

/**
 * Smallest LiveBpmn demo — two service tasks, variables flowing.
 *
 * <pre>
 *   start -> greet -> print -> end
 * </pre>
 *
 * <p>{@code greet} (Function&lt;Job, Map&gt; form): reads {@code name}, returns {@code greeting}.
 * Auto-complete with the returned map.
 *
 * <p>{@code print} (Consumer&lt;Job&gt; form): reads {@code greeting}, writes stdout. Auto-complete
 * on fall-through.
 */
public final class MinimalDemo {

  private MinimalDemo() {}

  public static void main(final String[] args) throws Exception {
    final List<String> names = List.of("Stephan", "Anna", "World");
    try (var cluster = LiveBpmn.cluster().testcontainer()) {
      LiveBpmn.createExecutableProcess("hello")
          .startEvent()
          .serviceTask(
              "greet",
              (Job job) -> Map.of("greeting", "hello, " + job.variable("name", String.class)))
          .serviceTask(
              "print", (Job job) -> System.out.println(job.variable("greeting", String.class)))
          .endEvent()
          .run(RunOptions.of(names.size()).variables(i -> Map.of("name", names.get(i))), cluster)
          .await(Duration.ofMinutes(1));
    }
  }
}
