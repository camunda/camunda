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

/**
 * Scale demo: fire {@value #INSTANCES} instances paced {@code PACING} apart so they trickle into
 * Operate visibly instead of all completing in milliseconds.
 *
 * <pre>
 *   start -> work -> end
 * </pre>
 *
 * <p>The {@code work} task simulates ~50 ms of work. Open the printed Operate URL to watch the
 * instances move through the diagram in real time. {@code run.workersHandled()} should read {@value
 * #INSTANCES} for {@code work} when {@code await()} returns.
 */
public final class LoadDemo {

  private LoadDemo() {}

  private static final int INSTANCES = 50;
  private static final Duration PACING = Duration.ofMillis(100);

  public static void main(final String[] args) throws Exception {
    System.out.println("[LoadDemo] firing " + INSTANCES + " instances, " + PACING + " apart…");

    // Demo flow assumes a local cluster on localhost:26500 (start `c8run start` or
    // `docker compose up -d` first). Swap to .testcontainer() for a JVM-managed container.
    try (var cluster = LiveBpmn.cluster().localhost()) {
      final Run run =
          LiveBpmn.createExecutableProcess("load")
              .startEvent()
              .serviceTask(
                  "work",
                  (Job job) -> {
                    sleep(50); // pretend work
                  })
              .endEvent()
              .run(RunOptions.of(INSTANCES).pacing(PACING), cluster);

      System.out.println("Operate: " + run.operateUrl());
      run.await(Duration.ofMinutes(5));
      System.out.println("done; handled = " + run.workersHandled());
    }
  }

  private static void sleep(final long ms) {
    try {
      Thread.sleep(ms);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
