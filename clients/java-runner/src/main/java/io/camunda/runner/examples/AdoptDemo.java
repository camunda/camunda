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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.Map;

/**
 * Adopt an existing {@link BpmnModelInstance} (e.g. one built with vanilla {@code Bpmn}) and bind
 * lambda workers to it via {@link LiveBpmn#of(BpmnModelInstance)} + {@code bind(...)}.
 */
public final class AdoptDemo {

  private AdoptDemo() {}

  public static void main(final String[] args) throws Exception {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");

    final BpmnModelInstance existing =
        Bpmn.createExecutableProcess("adopt-me")
            .startEvent()
            .serviceTask("greet", t -> t.zeebeJobType("greet"))
            .endEvent()
            .done();

    System.out.println("[AdoptDemo] booting cluster…");
    try (var cluster = LiveBpmn.cluster().testcontainer()) {
      final Run run =
          LiveBpmn.of(existing)
              .bind(
                  "greet",
                  (Job job) -> {
                    System.out.println("hello from elementId=" + job.getElementId());
                    return Map.of();
                  })
              .run(2, cluster);
      System.out.println("Operate: " + run.operateUrl());
      run.await(Duration.ofMinutes(1));
      System.out.println("done; handled = " + run.workersHandled());
    }
  }
}
