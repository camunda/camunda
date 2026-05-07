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
 * Paced creation of many instances — 50 instances are created at 100 ms apart so they trickle into
 * Operate visibly.
 */
public final class LoadDemo {

  private LoadDemo() {}

  public static void main(final String[] args) throws Exception {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");

    System.out.println("[LoadDemo] booting cluster…");
    try (var cluster = LiveBpmn.cluster().testcontainer()) {
      final Run run =
          LiveBpmn.createExecutableProcess("load")
              .startEvent()
              .serviceTask(
                  "work",
                  (Job job) -> {
                    try {
                      Thread.sleep(50);
                    } catch (final InterruptedException e) {
                      Thread.currentThread().interrupt();
                    }
                    return null;
                  })
              .endEvent()
              .run(RunOptions.of(50).pacing(Duration.ofMillis(100)), cluster);
      System.out.println("Operate: " + run.operateUrl());
      run.await(Duration.ofMinutes(3));
      System.out.println("done; handled = " + run.workersHandled());
    }
  }
}
