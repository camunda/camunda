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
import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.coverage.model.CoverageReport;
import io.camunda.process.test.api.coverage.model.CoverageRunReport;
import io.camunda.process.test.api.coverage.model.ProcessCoverage;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.coverage.CoverageCollector;
import io.camunda.process.test.impl.coverage.CoverageTestDataCollector;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

@CamundaProcessTest
public class ProcessEngineCoverageIT {

  private static final Class<?> TEST_CLASS = ProcessEngineCoverageIT.class;

  private CamundaProcessTestContext processTestContext;
  private CamundaClient client;

  @Test
  void shouldCollectEventBasedGatewayFlows(final TestInfo testInfo) {
    // given
    final CoverageCollector coverageCollector = CoverageCollector.newBuilder().build();

    // when
    final ProcessInstanceEvent processInstance =
        deployAndCreateProcess("test-with-event-based-gateway", null);
    CamundaAssert.assertThat(processInstance).hasCompletedElements("End_Event");

    final String testName = testInfo.getDisplayName();
    final CoverageRunReport coverageRunReport =
        collectCoverageRunReport(coverageCollector, TEST_CLASS, testName);

    // then
    final List<ProcessCoverage> processCoverages = coverageRunReport.getProcessCoverages();
    assertThat(processCoverages).hasSize(1);
    assertThat(processCoverages)
        .first()
        .extracting(ProcessCoverage::getProcessDefinitionId)
        .isEqualTo("test-with-event-based-gateway");
    assertThat(processCoverages.stream().findFirst().get())
        .extracting(ProcessCoverage::getTakenSequenceFlows)
        .satisfies(
            takenFlows -> {
              assertThat(takenFlows).contains("Flow_Timer");
            });
  }

  @Test
  void shouldCoverProcess(final TestInfo testInfo) {
    // given
    final CoverageCollector coverageCollector = CoverageCollector.newBuilder().build();
    final Map<String, Object> variables = new HashMap<>();

    // when
    final ProcessInstanceEvent processInstance1 =
        deployAndCreateProcess("test-with-simple-gateway", variables);
    CamundaAssert.assertThat(processInstance1).isCompleted();

    variables.put("gatewayAnswer", "yes");
    final ProcessInstanceEvent processInstance2 =
        deployAndCreateProcess("test-with-simple-gateway", variables);
    CamundaAssert.assertThat(processInstance2).isCompleted();

    final String testName = testInfo.getDisplayName();
    final CoverageRunReport coverageRunReport =
        collectCoverageRunReport(coverageCollector, TEST_CLASS, testName);

    // then
    assertThat(coverageRunReport.getProcessCoverages()).hasSize(2);
    assertThat(coverageRunReport.getProcessCoverages())
        .extracting(ProcessCoverage::getCompletedElements)
        .containsExactly(
            Arrays.asList("StartEvent", "GatewayEvent", "NoEvent", "EndEvent"),
            Arrays.asList("StartEvent", "GatewayEvent", "YesEvent", "EndEvent"));
    assertThat(coverageRunReport.getProcessCoverages())
        .extracting(ProcessCoverage::getTakenSequenceFlows)
        .containsExactly(
            Arrays.asList("FlowGateway", "FlowGatewayDefault", "FlowNoEnd"),
            Arrays.asList("FlowGateway", "FlowGatewayYes", "FlowYesEnd"));
    assertThat(coverageRunReport.getProcessCoverages())
        .extracting(ProcessCoverage::getCoverage)
        .containsExactly(0.7, 0.7);
    assertThat(coverageRunReport.getProcessCoverages())
        .extracting(ProcessCoverage::getProcessDefinitionId)
        .containsExactly("test-with-simple-gateway", "test-with-simple-gateway");
  }

  @Test
  void shouldExcludeProcess(final TestInfo testInfo) {
    // given
    final CoverageCollector coverageCollector =
        CoverageCollector.newBuilder()
            .excludeProcessDefinitionIds(Collections.singletonList("test-with-event-based-gateway"))
            .build();

    // when
    final ProcessInstanceEvent processInstance1 =
        deployAndCreateProcess("test-with-simple-gateway", null);
    CamundaAssert.assertThat(processInstance1).isCompleted();

    final ProcessInstanceEvent processInstance2 =
        deployAndCreateProcess("test-with-event-based-gateway", null);
    CamundaAssert.assertThat(processInstance2).isCompleted();

    final String testName = testInfo.getDisplayName();
    final CoverageRunReport coverageRunReport =
        collectCoverageRunReport(coverageCollector, ExcludeProcessTest.class, testName);

    // then
    assertThat(coverageRunReport.getProcessCoverages()).hasSize(1);
    assertThat(coverageRunReport.getProcessCoverages())
        .first()
        .extracting(ProcessCoverage::getProcessDefinitionId)
        .isEqualTo("test-with-simple-gateway");
  }

  @Test
  void shouldCoverDecision(final TestInfo testInfo) {
    // given
    final CoverageCollector coverageCollector = CoverageCollector.newBuilder().build();

    // when
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("coverage/test-with-decision-table.dmn")
        .send()
        .join();

    final EvaluateDecisionResponse decisionResponse =
        client
            .newEvaluateDecisionCommand()
            .decisionId("test-coverage-decision")
            .variable("category", "A")
            .send()
            .join();

    CamundaAssert.assertThatDecision(decisionResponse).isEvaluated();

    final String testName = testInfo.getDisplayName();
    final CoverageRunReport coverageRunReport =
        collectCoverageRunReport(coverageCollector, TEST_CLASS, testName);

    // then
    assertThat(coverageRunReport.getDecisionCoverages()).hasSize(1);
    assertThat(coverageRunReport.getDecisionCoverages())
        .first()
        .satisfies(
            dc -> {
              assertThat(dc.getDecisionDefinitionId()).isEqualTo("test-coverage-decision");
              assertThat(dc.getMatchedRuleIds()).containsExactly("DecisionRule_1");
              assertThat(dc.getMatchedRuleIndices()).containsExactly(1);
              assertThat(dc.getCoverage()).isEqualTo(0.5);
            });
  }

  @Test
  void shouldExcludeDecision(final TestInfo testInfo) {
    // given
    final CoverageCollector coverageCollector =
        CoverageCollector.newBuilder()
            .excludeDecisionDefinitionIds(Collections.singletonList("test-coverage-decision"))
            .build();

    // when
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("coverage/test-with-decision-table.dmn")
        .send()
        .join();

    final EvaluateDecisionResponse decisionResponse =
        client
            .newEvaluateDecisionCommand()
            .decisionId("test-coverage-decision")
            .variable("category", "A")
            .send()
            .join();

    CamundaAssert.assertThatDecision(decisionResponse).isEvaluated();

    final String testName = testInfo.getDisplayName();
    final CoverageRunReport coverageRunReport =
        collectCoverageRunReport(coverageCollector, ExcludeDecisionTest.class, testName);

    // then
    assertThat(coverageRunReport.getDecisionCoverages()).isEmpty();
  }

  private CoverageRunReport collectCoverageRunReport(
      final CoverageCollector coverageCollector, final Class<?> testClass, final String testName) {
    final CoverageReport coverageReport =
        coverageCollector.collectTestRunCoverage(
            testClass,
            testName,
            CoverageTestDataCollector.collectData(new CamundaDataSource(client)));
    return coverageReport.getSuites().stream()
        .flatMap(report -> report.getRuns().stream())
        .filter(run -> run.getName().equals(testName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No run report found for " + testName));
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

  private static final class ExcludeProcessTest {}

  private static final class ExcludeDecisionTest {}
}
