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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.response.DecisionDefinitionType;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.ProcessInstanceSequenceFlow;
import io.camunda.process.test.api.coverage.model.CoverageReport;
import io.camunda.process.test.impl.coverage.CoverageCollector;
import io.camunda.process.test.impl.coverage.data.CoverageTestData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageDecisionInstanceData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageProcessDefinitionData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageProcessInstanceData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageTestData;
import io.camunda.zeebe.model.bpmn.Bpmn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CoverageReportCollectorBuilderTest {

  @Mock private ProcessInstance processInstance;

  @Mock private DecisionInstance decisionInstance;

  @Test
  void shouldApplyExclusionConfigurationToCoverageResult() {
    // given
    when(processInstance.getProcessDefinitionId()).thenReturn("excluded-process");

    when(decisionInstance.getDecisionDefinitionId()).thenReturn("excluded-decision");
    when(decisionInstance.getDecisionDefinitionType())
        .thenReturn(DecisionDefinitionType.DECISION_TABLE);

    final CoverageTestData testResults =
        ImmutableCoverageTestData.builder()
            .addProcessInstanceData(
                ImmutableCoverageProcessInstanceData.builder()
                    .processInstance(processInstance)
                    .build())
            .addDecisionInstanceData(
                ImmutableCoverageDecisionInstanceData.builder()
                    .decisionInstance(decisionInstance)
                    .build())
            .build();

    final CoverageCollector coverageCollector =
        CoverageCollector.newBuilder()
            .excludeProcessDefinitionIds(java.util.Collections.singletonList("excluded-process"))
            .excludeDecisionDefinitionIds(java.util.Collections.singletonList("excluded-decision"))
            .build();

    // when
    final CoverageReport report =
        coverageCollector.collectTestRunCoverage(ExclusionTest.class, "run-1", testResults);

    // then
    assertThat(report.getSuites())
        .singleElement()
        .satisfies(suite -> assertThat(suite.getRuns()).hasSize(1));
    assertThat(report.getProcessCoverages()).isEmpty();
    assertThat(report.getDecisionCoverages()).isEmpty();
  }

  @Test
  void shouldIncludeGivenRunAndCollectedDataInSuiteReport() {
    // given
    final CoverageCollector coverageCollector = CoverageCollector.newBuilder().build();
    final String processDefinitionId = "process-a";
    final CoverageTestData testData = createProcessCoverageTestData(processDefinitionId, "taskA");

    // when
    final CoverageReport report =
        coverageCollector.collectTestRunCoverage(GivenRunTest.class, "run-1", testData);

    // then
    assertThat(report.getSuites())
        .singleElement()
        .satisfies(
            suite -> {
              assertThat(suite.getId()).isEqualTo(GivenRunTest.class.getName());
              assertThat(suite.getRuns())
                  .singleElement()
                  .satisfies(
                      run -> {
                        assertThat(run.getName()).isEqualTo("run-1");
                        assertThat(run.getProcessCoverages())
                            .singleElement()
                            .satisfies(
                                coverage -> {
                                  assertThat(coverage.getProcessDefinitionId())
                                      .isEqualTo(processDefinitionId);
                                  assertThat(coverage.getCompletedElements()).contains("taskA");
                                });
                      });
            });

    assertThat(report.getProcessModels())
        .singleElement()
        .satisfies(
            model -> {
              assertThat(model.getProcessDefinitionId()).isEqualTo(processDefinitionId);
              assertThat(model.getXml()).contains("id=\"" + processDefinitionId + "\"");
            });
  }

  @Test
  void shouldIncludeCollectedDataInAggregatedReport() {
    // given
    final CoverageCollector coverageCollector = CoverageCollector.newBuilder().build();
    coverageCollector.collectTestRunCoverage(
        AggregatedReportTest.class, "run-1", createProcessCoverageTestData("process-a", "taskA"));
    final CoverageReport report =
        coverageCollector.collectTestRunCoverage(
            AggregatedReportTest.class,
            "run-2",
            createProcessCoverageTestData("process-b", "taskB"));

    // then
    assertThat(report.getSuites())
        .anySatisfy(
            suite -> {
              if (suite.getId().equals(AggregatedReportTest.class.getName())) {
                assertThat(suite.getRuns())
                    .extracting(run -> run.getName())
                    .containsExactly("run-1", "run-2");
              }
            });
    assertThat(report.getProcessCoverages()).hasSize(2);
    assertThat(report.getProcessModels())
        .extracting(model -> model.getProcessDefinitionId())
        .containsExactlyInAnyOrder("process-a", "process-b");
  }

  private CoverageTestData createProcessCoverageTestData(
      final String processDefinitionId, final String completedElementId) {
    final ProcessInstance processInstance = mock(ProcessInstance.class);
    when(processInstance.getProcessDefinitionId()).thenReturn(processDefinitionId);

    final ElementInstance elementInstance = mock(ElementInstance.class);
    when(elementInstance.getElementId()).thenReturn(completedElementId);
    when(elementInstance.getType()).thenReturn(ElementInstanceType.SERVICE_TASK);
    when(elementInstance.getState()).thenReturn(ElementInstanceState.COMPLETED);

    final ProcessInstanceSequenceFlow sequenceFlow = mock(ProcessInstanceSequenceFlow.class);
    when(sequenceFlow.getElementId()).thenReturn("flow-" + completedElementId);

    final ProcessDefinition processDefinition = mock(ProcessDefinition.class);
    when(processDefinition.getProcessDefinitionId()).thenReturn(processDefinitionId);
    when(processDefinition.getVersion()).thenReturn(1);

    return ImmutableCoverageTestData.builder()
        .addProcessInstanceData(
            ImmutableCoverageProcessInstanceData.builder()
                .processInstance(processInstance)
                .addAllElementInstances(java.util.Collections.singletonList(elementInstance))
                .addAllSequenceFlows(java.util.Collections.singletonList(sequenceFlow))
                .build())
        .addProcessDefinitionData(
            ImmutableCoverageProcessDefinitionData.builder()
                .processDefinition(processDefinition)
                .xml(
                    processXml(
                        processDefinitionId, completedElementId, "flow-" + completedElementId))
                .build())
        .build();
  }

  private String processXml(
      final String processDefinitionId, final String completedElementId, final String flowId) {
    return Bpmn.convertToString(
        Bpmn.createExecutableProcess(processDefinitionId)
            .startEvent("start")
            .serviceTask(completedElementId)
            .endEvent("end")
            .moveToNode("start")
            .sequenceFlowId("flow-start")
            .connectTo(completedElementId)
            .moveToNode(completedElementId)
            .sequenceFlowId(flowId)
            .connectTo("end")
            .done());
  }

  private static final class GivenRunTest {}

  private static final class AggregatedReportTest {}

  private static final class ExclusionTest {}
}
