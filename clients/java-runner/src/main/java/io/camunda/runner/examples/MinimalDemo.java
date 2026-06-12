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
 * Smallest LiveBpmn demo — two service tasks showing the two ways a lambda can finish.
 *
 * <pre>
 *   start -> greet -> print -> end
 * </pre>
 *
 * <ul>
 *   <li>{@code greet} — explicit control: calls {@link Job#fail(String)} on bad input, otherwise
 *       {@link Job#complete(Map)} with a new variable. (Consumer&lt;Job&gt; form.)
 *   <li>{@code print} — auto-complete: writes to stdout and falls off the end. The runner
 *       auto-completes with no variable updates. (Consumer&lt;Job&gt; form too.)
 * </ul>
 */
public final class MinimalDemo {

  private MinimalDemo() {}

  public static void main(final String[] args) throws Exception {
    final List<String> names = List.of("Stephan", "", "World"); // empty triggers fail

    // Demo flow assumes a local cluster on localhost:26500 (start `c8run start` or
    // `docker compose up -d` first). Swap to .testcontainer() if you'd rather have one
    // booted from this JVM — slower but zero-config.
    try (final var cluster = LiveBpmn.cluster().localhost()) {
      LiveBpmn.createExecutableProcess("hello")
          .startEvent()
          .serviceTask(
              "greet",
              (final Job job) -> {
                final String name = job.variable("name", String.class);
                if (name == null || name.isBlank()) {
                  job.fail("missing 'name' variable");
                } else {
                  job.complete(Map.of("greeting", "hello, " + name));
                }
              })
          .serviceTask(
              "print", (Job job) -> System.out.println(job.variable("greeting", String.class)))
          .endEvent()
          .run(RunOptions.of(names.size()).variables(i -> Map.of("name", names.get(i))), cluster)
          .await(Duration.ofMinutes(1));
    }
  }
}
