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
package io.camunda.process.test.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.coverage.core.CoverageCollector;
import io.camunda.process.test.api.coverage.model.Run;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
public class ProcessEngineCoverageIT {

  private CamundaProcessTestContext processTestContext;
  private CamundaClient client;

  @Test
  void shouldCollectEventBasedGatewayFlows() {
    // given
    final CoverageCollector coverageCollector =
        new CoverageCollector(getClass(), new ArrayList<>(), () -> new CamundaDataSource(client));
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("test-event-based-gateway.bpmn")
        .send()
        .join();

    // when
    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("test-event-based-gateway")
            .latestVersion()
            .send()
            .join();

    CamundaAssert.assertThat(processInstance).hasCompletedElements("End_Event");

    coverageCollector.collectTestRunCoverage("test-run");

    // then
    final Collection<Run> runs = coverageCollector.getSuite().getRuns();
    assertThat(runs).hasSize(1);
    assertThat(runs.stream().findFirst().get())
        .extracting(Run::getCoverages)
        .satisfies(
            coverages -> {
              assertThat(coverages).hasSize(1);
              assertThat(coverages.stream().findFirst().get().getProcessDefinitionId())
                  .isEqualTo("test-event-based-gateway");
            });
  }

  @Test
  void shouldCalculateCoverage() {
    // given
    final CoverageCollector coverageCollector =
        new CoverageCollector(getClass(), new ArrayList<>(), () -> new CamundaDataSource(client));
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("test-process-with-user-task.bpmn")
        .send()
        .join();

    // when
    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("test-process-with-user-task")
            .latestVersion()
            .send()
            .join();
    processTestContext.completeUserTask("user-task");

    CamundaAssert.assertThat(processInstance).isCompleted();
    coverageCollector.collectTestRunCoverage("test-run");

    // then
    final Collection<Run> runs = coverageCollector.getSuite().getRuns();
    assertThat(runs).hasSize(1);
    assertThat(runs.stream().findFirst().get())
        .extracting(Run::getCoverages)
        .satisfies(
            coverages -> {
              assertThat(coverages).hasSize(1);
              assertThat(coverages.stream().findFirst().get().getProcessDefinitionId())
                  .isEqualTo("test-process-with-user-task");
            });
  }
}
