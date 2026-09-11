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
package io.camunda.process.test.impl.coverage.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.api.search.response.DecisionDefinition;
import io.camunda.client.api.search.response.DecisionDefinitionType;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.api.coverage.model.CoverageRunReport;
import io.camunda.process.test.api.coverage.model.DecisionModel;
import io.camunda.process.test.api.coverage.model.ProcessModel;
import io.camunda.process.test.impl.coverage.data.CoverageTestData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageDecisionDefinitionData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageDecisionInstanceData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageProcessDefinitionData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageProcessInstanceData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageTestData;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.util.Collections;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class CoverageReportCollectorTest {

  private static final String PROCESS_ID = "child-process";
  private static final String DECISION_ID = "decision-a";

  private static final String REAL_XML =
      Bpmn.convertToString(
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent("start")
              .serviceTask("realTask")
              .endEvent("end")
              .done());

  /** The stub that {@code MOCK_CHILD_PROCESS} deploys under the mocked process id. */
  private static final String MOCK_STUB_XML =
      Bpmn.convertToString(
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent("child-start")
              .endEvent("child-end")
              .done());

  /**
   * A test class can mock a child process in one test and run the real one in another. Both runs
   * report coverage for the same process definition id, but only the real model describes the
   * process.
   */
  @Test
  void shouldKeepTheMostCompleteModelWhenARunDeploysAMockStub() {
    // given
    final CoverageReportCollector collector =
        new CoverageReportCollector(
            CoverageReportCollectorTest.class, Collections.emptyList(), Collections.emptyList());

    // when: the run that mocks the child process comes first
    collector.collectTestRunCoverage(
        "mockingRun", null, testDataOf(MOCK_STUB_XML), Collections.emptyList());
    collector.collectTestRunCoverage(
        "realRun", null, testDataOf(REAL_XML), Collections.emptyList());

    // then
    assertThat(collector.getModels())
        .singleElement()
        .extracting(ProcessModel::getXml)
        .asString()
        .contains("realTask");
  }

  /** A mocked child process is a stub of the real process, so its run is not coverage of it. */
  @Test
  void shouldNotCollectCoverageOfAMockedProcess() {
    // given
    final CoverageReportCollector collector =
        new CoverageReportCollector(
            CoverageReportCollectorTest.class, Collections.emptyList(), Collections.emptyList());

    // when
    collector.collectTestRunCoverage(
        "mockingRun", null, testDataOf(MOCK_STUB_XML), Collections.singletonList(PROCESS_ID));

    // then: the stub contributes neither a model nor coverage
    assertThat(collector.getModels()).isEmpty();
    assertThat(collector.getSuite().getRuns())
        .singleElement()
        .extracting(CoverageRunReport::getProcessCoverages)
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .isEmpty();
  }

  /**
   * The report cannot tell a stub from the process it mocks by its model alone, so the collector
   * has to remember which processes its suite mocked - in any of its runs, as the run that reports
   * a stub model is not the run that mocked the process.
   */
  @Test
  void shouldRememberTheProcessesThatAnyRunMocked() {
    // given
    final CoverageReportCollector collector =
        new CoverageReportCollector(
            CoverageReportCollectorTest.class, Collections.emptyList(), Collections.emptyList());

    // when: only one of the runs mocks the child process
    collector.collectTestRunCoverage(
        "mockingRun", null, testDataOf(MOCK_STUB_XML), Collections.singletonList(PROCESS_ID));
    collector.collectTestRunCoverage(
        "realRun", null, testDataOf(REAL_XML), Collections.emptyList());

    // then
    assertThat(collector.getMockedProcessDefinitionIds()).containsExactly(PROCESS_ID);
  }

  /** Builds the data of a test run that ran a single instance of the given process model. */
  private static CoverageTestData testDataOf(final String xml) {
    final ProcessDefinition processDefinition = mock(ProcessDefinition.class);
    when(processDefinition.getProcessDefinitionId()).thenReturn(PROCESS_ID);
    when(processDefinition.getVersion()).thenReturn(1);

    final ProcessInstance processInstance = mock(ProcessInstance.class);
    when(processInstance.getProcessDefinitionId()).thenReturn(PROCESS_ID);

    return ImmutableCoverageTestData.builder()
        .addProcessInstanceData(
            ImmutableCoverageProcessInstanceData.builder().processInstance(processInstance).build())
        .addProcessDefinitionData(
            ImmutableCoverageProcessDefinitionData.builder()
                .processDefinition(processDefinition)
                .xml(xml)
                .build())
        .build();
  }

  /**
   * A test class can evaluate different tables of one decision definition id, for example when a
   * later test deploys a fixture with more rules. Only the most complete table describes the
   * decision.
   */
  @Test
  void shouldKeepTheMostCompleteTableWhenARunEvaluatesASmallerTable() {
    // given
    final CoverageReportCollector collector =
        new CoverageReportCollector(
            CoverageReportCollectorTest.class, Collections.emptyList(), Collections.emptyList());

    // when: the run that evaluates the smaller table comes first
    collector.collectTestRunCoverage(
        "smallTableRun", null, decisionTestDataOf(2), Collections.emptyList());
    collector.collectTestRunCoverage(
        "fullTableRun", null, decisionTestDataOf(5), Collections.emptyList());

    // then
    assertThat(collector.getDecisionModels())
        .singleElement()
        .extracting(DecisionModel::getTotalRuleCount)
        .isEqualTo(5);
  }

  /** Builds the data of a test run that evaluated a decision table with the given rule count. */
  private static CoverageTestData decisionTestDataOf(final int ruleCount) {
    final DecisionDefinition decisionDefinition = mock(DecisionDefinition.class);
    when(decisionDefinition.getDmnDecisionId()).thenReturn(DECISION_ID);
    when(decisionDefinition.getDmnDecisionName()).thenReturn(DECISION_ID);
    when(decisionDefinition.getVersion()).thenReturn(1);

    final DecisionInstance decisionInstance = mock(DecisionInstance.class);
    when(decisionInstance.getDecisionDefinitionId()).thenReturn(DECISION_ID);
    when(decisionInstance.getDecisionDefinitionType())
        .thenReturn(DecisionDefinitionType.DECISION_TABLE);

    return ImmutableCoverageTestData.builder()
        .addDecisionInstanceData(
            ImmutableCoverageDecisionInstanceData.builder()
                .decisionInstance(decisionInstance)
                .build())
        .addDecisionDefinitionData(
            ImmutableCoverageDecisionDefinitionData.builder()
                .decisionDefinition(decisionDefinition)
                .xml(DecisionModelCreatorTest.buildDmnXml(DECISION_ID, DECISION_ID, ruleCount))
                .build())
        .build();
  }
}
