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
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.coverage.core.CoverageCollector;
import io.camunda.process.test.impl.coverage.model.Coverage;
import io.camunda.process.test.impl.coverage.model.Run;
import io.camunda.process.test.impl.coverage.report.CoverageReportCreator;
import io.camunda.process.test.impl.coverage.report.SuiteCoverageReport;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
public class ProcessEngineCoverageIT {

  private CamundaProcessTestContext processTestContext;
  private CamundaClient client;

  @Test
  void shouldCollectEventBasedGatewayFlows() {
    // given
    final CoverageCollector coverageCollector =
        CoverageCollector.createCollector(
            getClass(), new ArrayList<>(), () -> new CamundaDataSource(client));

    // when
    final ProcessInstanceEvent processInstance =
        deployAndCreateProcess("test-with-event-based-gateway", null);
    CamundaAssert.assertThat(processInstance).hasCompletedElements("End_Event");

    coverageCollector.collectTestRunCoverage("test-run");

    // then
    final Collection<Run> runs = coverageCollector.getSuite().getRuns();
    assertThat(runs).hasSize(1);
    assertThat(runs)
        .first()
        .extracting(Run::getCoverages)
        .satisfies(
            coverages -> {
              assertThat(coverages).hasSize(1);
              assertThat(coverages)
                  .first()
                  .extracting(Coverage::getProcessDefinitionId)
                  .isEqualTo("test-with-event-based-gateway");
              assertThat(coverages.stream().findFirst().get())
                  .extracting(Coverage::getTakenSequenceFlows)
                  .satisfies(
                      takenFlows -> {
                        assertThat(takenFlows).contains("Flow_Timer");
                      });
            });
  }

  @Test
  void shouldCoverProcess() {
    // given
    final CoverageCollector coverageCollector =
        CoverageCollector.createCollector(
            getClass(), new ArrayList<>(), () -> new CamundaDataSource(client));
    final Map<String, Object> variables = new HashMap<>();

    // when
    final ProcessInstanceEvent processInstance1 =
        deployAndCreateProcess("test-with-simple-gateway", variables);
    CamundaAssert.assertThat(processInstance1).isCompleted();

    variables.put("gatewayAnswer", "yes");
    final ProcessInstanceEvent processInstance2 =
        deployAndCreateProcess("test-with-simple-gateway", variables);
    CamundaAssert.assertThat(processInstance2).isCompleted();

    coverageCollector.collectTestRunCoverage("simple-run");

    // then
    final Collection<Run> runs = coverageCollector.getSuite().getRuns();
    assertThat(runs.stream().findFirst())
        .isPresent()
        .get()
        .satisfies(
            run -> {
              assertThat(run.getCoverages()).hasSize(2);
              assertThat(run.getCoverages())
                  .extracting(Coverage::getCompletedElements)
                  .containsExactly(
                      Arrays.asList("StartEvent", "GatewayEvent", "NoEvent", "EndEvent"),
                      Arrays.asList("StartEvent", "GatewayEvent", "YesEvent", "EndEvent"));
              assertThat(run.getCoverages())
                  .extracting(Coverage::getTakenSequenceFlows)
                  .containsExactly(
                      Arrays.asList("FlowGateway", "FlowGatewayDefault", "FlowNoEnd"),
                      Arrays.asList("FlowGateway", "FlowGatewayYes", "FlowYesEnd"));
              assertThat(run.getCoverages())
                  .extracting(Coverage::getCoverage)
                  .containsExactly(0.7, 0.7);
              assertThat(run.getCoverages())
                  .extracting(Coverage::getProcessDefinitionId)
                  .containsExactly("test-with-simple-gateway", "test-with-simple-gateway");
            });
  }

  @Test
  void shouldExcludeProcess() {
    // given
    final CoverageCollector coverageCollector =
        CoverageCollector.createCollector(
            getClass(),
            Collections.singletonList("test-with-event-based-gateway"),
            () -> new CamundaDataSource(client));

    // when
    final ProcessInstanceEvent processInstance1 =
        deployAndCreateProcess("test-with-simple-gateway", null);
    CamundaAssert.assertThat(processInstance1).isCompleted();

    final ProcessInstanceEvent processInstance2 =
        deployAndCreateProcess("test-with-event-based-gateway", null);
    CamundaAssert.assertThat(processInstance2).isCompleted();

    coverageCollector.collectTestRunCoverage("simple-run");

    // then
    final Collection<Run> runs = coverageCollector.getSuite().getRuns();
    assertThat(runs.stream().findFirst())
        .isPresent()
        .get()
        .satisfies(
            run -> {
              assertThat(run.getCoverages()).hasSize(1);
              assertThat(run.getCoverages())
                  .first()
                  .extracting(Coverage::getProcessDefinitionId)
                  .isEqualTo("test-with-simple-gateway");
            });
  }

  @Test
  void shouldNotCoverMockedChildProcess() {
    // given
    final CoverageCollector coverageCollector =
        CoverageCollector.createCollector(
            getClass(), new ArrayList<>(), () -> new CamundaDataSource(client));

    processTestContext.mockChildProcess("mocked-child-process");

    // when
    final ProcessInstanceEvent processInstance =
        deployAndCreateProcessInstance(
            "test-with-mocked-child",
            Bpmn.createExecutableProcess("test-with-mocked-child")
                .startEvent("StartEvent")
                .callActivity("CallActivity", c -> c.zeebeProcessId("mocked-child-process"))
                .endEvent("EndEvent")
                .done());
    CamundaAssert.assertThat(processInstance).isCompleted();

    coverageCollector.collectTestRunCoverage("mocked-child-run");

    // then
    final Collection<Run> runs = coverageCollector.getSuite().getRuns();
    assertThat(runs.stream().findFirst())
        .isPresent()
        .get()
        .satisfies(
            run ->
                assertThat(run.getCoverages())
                    .extracting(Coverage::getProcessDefinitionId)
                    .containsExactly("test-with-mocked-child"));
  }

  /**
   * Collected as two runs of one suite rather than as two suites, because the data source is not
   * scoped to a point in time: every collection reads every process instance created so far, so a
   * second suite would re-collect the instances of the first one as well.
   */
  @Test
  void shouldNotExceed100PercentWhenProcessIsAlsoMockedElsewhere() {
    // given: a run that covers the process under test end to end
    final CoverageCollector coverageCollector =
        CoverageCollector.createCollector(
            getClass(), new ArrayList<>(), () -> new CamundaDataSource(client));

    final ProcessInstanceEvent testedProcessInstance =
        deployAndCreateProcessInstance(
            "tested-and-mocked-process",
            Bpmn.createExecutableProcess("tested-and-mocked-process")
                .startEvent("StartEvent")
                .sequenceFlowId("FlowToTask")
                .task("Task_Inside_Tested_Process")
                .sequenceFlowId("FlowToEnd")
                .endEvent("EndEvent")
                .done());
    CamundaAssert.assertThat(testedProcessInstance).isCompleted();

    coverageCollector.collectTestRunCoverage("covering-run");

    // when: a later run mocks that same process as a child process
    processTestContext.mockChildProcess("tested-and-mocked-process");

    final ProcessInstanceEvent callerProcessInstance =
        deployAndCreateProcessInstance(
            "test-calling-the-mocked-process",
            Bpmn.createExecutableProcess("test-calling-the-mocked-process")
                .startEvent("StartEvent")
                .callActivity("CallActivity", c -> c.zeebeProcessId("tested-and-mocked-process"))
                .endEvent("EndEvent")
                .done());
    CamundaAssert.assertThat(callerProcessInstance).isCompleted();

    coverageCollector.collectTestRunCoverage("mocking-run");

    // then: the process is reported by the model under test, covered in full
    final SuiteCoverageReport report =
        CoverageReportCreator.createSuiteCoverageReport(
            coverageCollector.getSuite(), coverageCollector.getModels());

    assertThat(report.getModels())
        .filteredOn(model -> model.getProcessDefinitionId().equals("tested-and-mocked-process"))
        .singleElement()
        .satisfies(model -> assertThat(model.xml()).contains("Task_Inside_Tested_Process"));
    assertThat(report.getCoverages())
        .filteredOn(
            coverage -> coverage.getProcessDefinitionId().equals("tested-and-mocked-process"))
        .singleElement()
        .satisfies(coverage -> assertThat(coverage.getCoverage()).isEqualTo(1.0));
  }

  private ProcessInstanceEvent deployAndCreateProcessInstance(
      final String processDefinitionId, final BpmnModelInstance processModel) {
    client
        .newDeployResourceCommand()
        .addProcessModel(processModel, processDefinitionId + ".bpmn")
        .send()
        .join();

    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processDefinitionId)
        .latestVersion()
        .send()
        .join();
  }

  private ProcessInstanceEvent deployAndCreateProcess(
      final String processDefinitionId, final Map<String, Object> variables) {
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("coverage/" + processDefinitionId + ".bpmn")
        .send()
        .join();

    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processDefinitionId)
        .latestVersion()
        .variables(Optional.ofNullable(variables).orElse(new HashMap<>()))
        .send()
        .join();
  }
}
