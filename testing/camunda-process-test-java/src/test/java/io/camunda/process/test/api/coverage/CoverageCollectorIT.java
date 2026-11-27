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
package io.camunda.process.test.api.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.process.test.api.coverage.model.CoverageReport;
import io.camunda.process.test.api.coverage.model.CoverageRunReport;
import io.camunda.process.test.impl.client.CamundaClockClient;
import io.camunda.process.test.impl.client.CamundaClockClientImpl;
import io.camunda.process.test.impl.runtime.CamundaProcessTestContainerRuntime;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntime;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Collects the process coverage without the JUnit extension or the Spring listener. The process is
 * deployed once, outside of the test runs, like an application that deploys its resources on
 * startup.
 */
class CoverageCollectorIT {

  private static CamundaProcessTestRuntime runtime;
  private static CamundaClient client;
  private static CamundaClockClient clockClient;

  private final String processId = "coverage-" + UUID.randomUUID();

  @BeforeAll
  static void startRuntime() {
    runtime = CamundaProcessTestContainerRuntime.newBuilder().build();
    runtime.start();
    runtime.waitUntilClusterReady(Duration.ofSeconds(30));

    client = runtime.getCamundaClientBuilderFactory().get().build();
    clockClient = new CamundaClockClientImpl(runtime.getCamundaClientBuilderFactory());
  }

  @AfterAll
  static void stopRuntime() throws Exception {
    if (client != null) {
      client.close();
    }
    if (clockClient != null) {
      clockClient.close();
    }
    if (runtime != null) {
      runtime.close();
    }
  }

  @Test
  void shouldCollectCoverageOfTestRuns() throws Exception {
    // given: the process is deployed once, outside of the test runs
    deployProcess();

    final CoverageCollector coverageCollector = CoverageCollector.newBuilder().build();

    final String suiteId = "suite-" + processId;

    // when: two test runs use the deployed process
    final Instant startRun1 = clockClient.getCurrentTime();
    runProcessInstance();
    coverageCollector.collectTestRunCoverage(
        CoverageTestIdentifier.of(suiteId, "My suite", "run-1", null), client, startRun1);

    final Instant startRun2 = clockClient.getCurrentTime();
    runProcessInstance();
    runProcessInstance();
    coverageCollector.collectTestRunCoverage(
        CoverageTestIdentifier.of(suiteId, "My suite", "run-2", "Second run"), client, startRun2);

    final CoverageReport report = coverageCollector.getAggregatedReport();

    // then: each run only has the instances created since its start
    assertThat(report.getSuites())
        .filteredOn(suite -> suite.getId().equals(suiteId))
        .singleElement()
        .satisfies(
            suite -> {
              assertThat(suite.getName()).isEqualTo("My suite");
              assertThat(suite.getRuns())
                  .extracting(CoverageRunReport::getName)
                  .containsExactly("run-1", "run-2");
              assertThat(suite.getRuns())
                  .extracting(run -> run.getProcessCoverages().size())
                  .containsExactly(1, 2);
              assertThat(suite.getRuns().get(1).getDisplayName()).isEqualTo("Second run");
            });
    assertThat(report.getProcessCoverages())
        .filteredOn(coverage -> coverage.getProcessDefinitionId().equals(processId))
        .singleElement()
        .satisfies(coverage -> assertThat(coverage.getCoverage()).isEqualTo(1.0));
  }

  private void deployProcess() {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId).startEvent().endEvent("end").done();

    client.newDeployResourceCommand().addProcessModel(process, processId + ".bpmn").send().join();
  }

  private void runProcessInstance() {
    final long processInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    // the data is exported asynchronously
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(
                        client
                            .newElementInstanceSearchRequest()
                            .filter(
                                filter ->
                                    filter
                                        .processInstanceKey(processInstanceKey)
                                        .elementId("end")
                                        .state(ElementInstanceState.COMPLETED))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));
  }
}
