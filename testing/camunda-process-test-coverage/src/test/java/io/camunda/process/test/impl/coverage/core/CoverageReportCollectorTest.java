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

  /** A smaller model that an earlier test deploys under the same process definition id. */
  private static final String SMALL_XML =
      Bpmn.convertToString(
          Bpmn.createExecutableProcess(PROCESS_ID).startEvent("start").endEvent("end").done());

  /** The stub that {@code MOCK_CHILD_PROCESS} deploys under the mocked process id. */
  private static final String MOCK_STUB_XML =
      Bpmn.convertToString(
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent("child-start")
              .endEvent("child-end")
              .done());

  /**
   * A test class can deploy different models under one process definition id, for example when a
   * later test deploys a fixture with more elements. Only the most complete model describes the
   * process.
   */
  @Test
  void shouldKeepTheMostCompleteModelWhenARunDeploysASmallerModel() {
    // given
    final CoverageReportCollector collector =
        new CoverageReportCollector(
            CoverageReportCollectorTest.class, Collections.emptyList(), Collections.emptyList());

    // when: the run that deploys the smaller model comes first
    collector.collectTestRunCoverage(
        "smallModelRun", null, testDataOf(111L, SMALL_XML), Collections.emptyList());
    collector.collectTestRunCoverage(
        "realRun", null, testDataOf(222L, REAL_XML), Collections.emptyList());

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

    // when: the run deployed the stub as the process definition with the key 111
    collector.collectTestRunCoverage(
        "mockingRun", null, testDataOf(111L, MOCK_STUB_XML), Collections.singletonList(111L));

    // then: the stub contributes neither a model nor coverage
    assertThat(collector.getModels()).isEmpty();
    assertThat(collector.getSuite().getRuns())
        .singleElement()
        .extracting(CoverageRunReport::getProcessCoverages)
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .isEmpty();
  }

  /**
   * The instances of a mocked process outlive the run that mocked it when the test data between
   * runs is kept. A later run that does not mock the process must not count them either: they are
   * instances of the stub, and counting them reports elements of the stub as coverage of the
   * process.
   */
  @Test
  void shouldNotCollectCoverageOfAStubInstanceOfAnEarlierRun() {
    // given: a run that mocked the child process
    final CoverageReportCollector collector =
        new CoverageReportCollector(
            CoverageReportCollectorTest.class, Collections.emptyList(), Collections.emptyList());
    collector.collectTestRunCoverage(
        "mockingRun", null, testDataOf(111L, MOCK_STUB_XML), Collections.singletonList(111L));

    // when: a later run does not mock it, but the stub instance of the earlier run is still around
    collector.collectTestRunCoverage(
        "realRun",
        null,
        ImmutableCoverageTestData.builder()
            .addProcessInstanceData(processInstanceDataOf(111L))
            .addProcessInstanceData(processInstanceDataOf(222L))
            .addProcessDefinitionData(processDefinitionDataOf(1, 111L, MOCK_STUB_XML))
            .addProcessDefinitionData(processDefinitionDataOf(2, 222L, REAL_XML))
            .build(),
        Collections.emptyList());

    // then: only the instance of the real process is coverage of it
    assertThat(collector.getModels())
        .singleElement()
        .extracting(ProcessModel::getXml)
        .asString()
        .contains("realTask");
    assertThat(collector.getSuite().getRuns())
        .filteredOn(run -> run.getName().equals("realRun"))
        .singleElement()
        .extracting(CoverageRunReport::getProcessCoverages)
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .hasSize(1);
  }

  /** Builds the data of a test run that ran a single instance of the given process model. */
  private static CoverageTestData testDataOf(final long processDefinitionKey, final String xml) {
    return ImmutableCoverageTestData.builder()
        .addProcessInstanceData(processInstanceDataOf(processDefinitionKey))
        .addProcessDefinitionData(processDefinitionDataOf(1, processDefinitionKey, xml))
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

  /**
   * A mock deploys its stub under the id of the process it mocks, so a run can have two deployments
   * of one process definition id. The coverage of an instance must be measured against the
   * deployment that the instance ran, otherwise it is measured against a process it never ran.
   */
  @Test
  void shouldCollectCoverageAgainstTheDeploymentThatRan() {
    // given
    final CoverageReportCollector collector =
        new CoverageReportCollector(
            CoverageReportCollectorTest.class, Collections.emptyList(), Collections.emptyList());

    // when: the instance ran the real deployment, which is deployed after the stub
    collector.collectTestRunCoverage(
        "realRun",
        null,
        ImmutableCoverageTestData.builder()
            .addProcessInstanceData(processInstanceDataOf(222L))
            .addProcessDefinitionData(processDefinitionDataOf(1, 111L, MOCK_STUB_XML))
            .addProcessDefinitionData(processDefinitionDataOf(2, 222L, REAL_XML))
            .build(),
        Collections.emptyList());

    // then
    assertThat(collector.getModels())
        .singleElement()
        .extracting(ProcessModel::getXml)
        .asString()
        .contains("realTask");
  }

  /**
   * A mock deploys its stub under the id of the decision it mocks, so a run can have two
   * deployments of one decision definition id. The coverage of an evaluation must be measured
   * against the table that was evaluated, otherwise it is measured against a table it never
   * evaluated.
   */
  @Test
  void shouldCollectCoverageAgainstTheTableThatWasEvaluated() {
    // given
    final CoverageReportCollector collector =
        new CoverageReportCollector(
            CoverageReportCollectorTest.class, Collections.emptyList(), Collections.emptyList());

    // when: the instance evaluated the five-rule table, which is deployed after the two-rule table
    collector.collectTestRunCoverage(
        "evaluationRun",
        null,
        ImmutableCoverageTestData.builder()
            .addDecisionInstanceData(decisionInstanceDataOf(222L))
            .addDecisionDefinitionData(decisionDefinitionDataOf(1, 111L, 2))
            .addDecisionDefinitionData(decisionDefinitionDataOf(2, 222L, 5))
            .build(),
        Collections.emptyList());

    // then
    assertThat(collector.getDecisionModels())
        .singleElement()
        .extracting(DecisionModel::getTotalRuleCount)
        .isEqualTo(5);
  }

  private static ImmutableCoverageProcessInstanceData processInstanceDataOf(
      final long processDefinitionKey) {
    final ProcessInstance processInstance = mock(ProcessInstance.class);
    when(processInstance.getProcessDefinitionId()).thenReturn(PROCESS_ID);
    when(processInstance.getProcessDefinitionKey()).thenReturn(processDefinitionKey);

    return ImmutableCoverageProcessInstanceData.builder().processInstance(processInstance).build();
  }

  private static ImmutableCoverageProcessDefinitionData processDefinitionDataOf(
      final int version, final long processDefinitionKey, final String xml) {
    final ProcessDefinition processDefinition = mock(ProcessDefinition.class);
    when(processDefinition.getProcessDefinitionId()).thenReturn(PROCESS_ID);
    when(processDefinition.getVersion()).thenReturn(version);
    when(processDefinition.getProcessDefinitionKey()).thenReturn(processDefinitionKey);

    return ImmutableCoverageProcessDefinitionData.builder()
        .processDefinition(processDefinition)
        .xml(xml)
        .build();
  }

  private static ImmutableCoverageDecisionInstanceData decisionInstanceDataOf(
      final long decisionDefinitionKey) {
    final DecisionInstance decisionInstance = mock(DecisionInstance.class);
    when(decisionInstance.getDecisionDefinitionId()).thenReturn(DECISION_ID);
    when(decisionInstance.getDecisionDefinitionKey()).thenReturn(decisionDefinitionKey);
    when(decisionInstance.getDecisionDefinitionType())
        .thenReturn(DecisionDefinitionType.DECISION_TABLE);

    return ImmutableCoverageDecisionInstanceData.builder()
        .decisionInstance(decisionInstance)
        .build();
  }

  private static ImmutableCoverageDecisionDefinitionData decisionDefinitionDataOf(
      final int version, final long decisionKey, final int ruleCount) {
    final DecisionDefinition decisionDefinition = mock(DecisionDefinition.class);
    when(decisionDefinition.getDmnDecisionId()).thenReturn(DECISION_ID);
    when(decisionDefinition.getDmnDecisionName()).thenReturn(DECISION_ID);
    when(decisionDefinition.getVersion()).thenReturn(version);
    when(decisionDefinition.getDecisionKey()).thenReturn(decisionKey);

    return ImmutableCoverageDecisionDefinitionData.builder()
        .decisionDefinition(decisionDefinition)
        .xml(DecisionModelCreatorTest.buildDmnXml(DECISION_ID, DECISION_ID, ruleCount))
        .build();
  }
}
