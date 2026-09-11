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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.process.test.api.coverage.model.ImmutableProcessModel;
import io.camunda.process.test.api.coverage.model.ProcessModel;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageProcessDefinitionData;
import io.camunda.process.test.impl.coverage.data.ImmutableCoverageTestData;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Test;

class ModelCreatorTest {

  @Test
  void shouldCreateModelWithProcessIdNameVersionAndXml() {
    // given
    final ProcessDefinition processDefinition = mock(ProcessDefinition.class);
    when(processDefinition.getProcessDefinitionId()).thenReturn("my-process");
    when(processDefinition.getName()).thenReturn("My Process");
    when(processDefinition.getVersion()).thenReturn(1);

    final BpmnModelInstance bpmnModel =
        Bpmn.createExecutableProcess("my-process")
            .name("My Process")
            .startEvent("start")
            .endEvent("end")
            .done();
    final String bpmnXml = Bpmn.convertToString(bpmnModel);

    final ImmutableCoverageTestData testData =
        ImmutableCoverageTestData.builder()
            .addProcessDefinitionData(
                ImmutableCoverageProcessDefinitionData.builder()
                    .processDefinition(processDefinition)
                    .xml(bpmnXml)
                    .build())
            .build();

    // when
    final ProcessModel model = ModelCreator.createModel(testData, "my-process");

    // then
    assertThat(model.getProcessDefinitionId()).isEqualTo("my-process");
    assertThat(model.getProcessName()).isEqualTo("My Process");
    assertThat(model.getVersion()).isEqualTo("1");
    assertThat(model.getXml()).contains("my-process");
  }

  @Test
  void shouldCountFlowNodesAndSequenceFlows() {
    // given: start → serviceTask → end = 3 nodes + 2 flows = 5 coverable elements
    final ProcessDefinition processDefinition = mock(ProcessDefinition.class);
    when(processDefinition.getProcessDefinitionId()).thenReturn("process");
    when(processDefinition.getVersion()).thenReturn(1);

    final BpmnModelInstance bpmnModel =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task")
            .endEvent("end")
            .done();

    final ImmutableCoverageTestData testData =
        ImmutableCoverageTestData.builder()
            .addProcessDefinitionData(
                ImmutableCoverageProcessDefinitionData.builder()
                    .processDefinition(processDefinition)
                    .xml(Bpmn.convertToString(bpmnModel))
                    .build())
            .build();

    // when
    final ProcessModel model = ModelCreator.createModel(testData, "process");

    // then: 3 flow nodes + 2 sequence flows = 5
    assertThat(model.getTotalElementCount()).isEqualTo(5);
  }

  @Test
  void shouldCountOnlyElementsOfExecutableProcess() {
    // given: a collaboration with one executable and one non-executable process
    final ProcessDefinition processDefinition = mock(ProcessDefinition.class);
    when(processDefinition.getProcessDefinitionId()).thenReturn("executable-process");
    when(processDefinition.getVersion()).thenReturn(1);

    // Build a model with two processes: one executable, one not
    final BpmnModelInstance bpmnModel =
        Bpmn.createExecutableProcess("executable-process")
            .startEvent("start")
            .endEvent("end")
            .done();

    final ImmutableCoverageTestData testData =
        ImmutableCoverageTestData.builder()
            .addProcessDefinitionData(
                ImmutableCoverageProcessDefinitionData.builder()
                    .processDefinition(processDefinition)
                    .xml(Bpmn.convertToString(bpmnModel))
                    .build())
            .build();

    // when
    final ProcessModel model = ModelCreator.createModel(testData, "executable-process");

    // then: 2 flow nodes (start + end) + 1 sequence flow = 3
    assertThat(model.getTotalElementCount()).isEqualTo(3);
  }

  @Test
  void shouldReturnNullProcessNameWhenNotDefined() {
    // given: process with no name attribute
    final ProcessDefinition processDefinition = mock(ProcessDefinition.class);
    when(processDefinition.getProcessDefinitionId()).thenReturn("unnamed-process");
    when(processDefinition.getVersion()).thenReturn(2);
    when(processDefinition.getName()).thenReturn(null);

    final BpmnModelInstance bpmnModel =
        Bpmn.createExecutableProcess("unnamed-process").startEvent().endEvent().done();

    final ImmutableCoverageTestData testData =
        ImmutableCoverageTestData.builder()
            .addProcessDefinitionData(
                ImmutableCoverageProcessDefinitionData.builder()
                    .processDefinition(processDefinition)
                    .xml(Bpmn.convertToString(bpmnModel))
                    .build())
            .build();

    // when
    final ProcessModel model = ModelCreator.createModel(testData, "unnamed-process");

    // then
    assertThat(model.getProcessName()).isNull();
    assertThat(model.getVersion()).isEqualTo("2");
  }

  @Test
  void shouldThrowWhenProcessDefinitionNotFound() {
    // given
    final ImmutableCoverageTestData testData = ImmutableCoverageTestData.builder().build();

    // then
    assertThatThrownBy(() -> ModelCreator.createModel(testData, "unknown-process"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No process definition data found for ID: unknown-process");
  }

  /**
   * A process definition id can be deployed more than once within a test run, for example when a
   * mock deploys a stub of a process that is also deployed for real. The model must describe the
   * deployment that the instance ran, not whichever deployment the test data happens to list first.
   */
  @Test
  void shouldCreateModelOfTheDeploymentThatRan() {
    // given: a stub deployment is listed before the real deployment of the same process id
    final ImmutableCoverageTestData testData =
        ImmutableCoverageTestData.builder()
            .addProcessDefinitionData(
                processDefinitionDataOf(
                    "process",
                    1,
                    111L,
                    Bpmn.createExecutableProcess("process")
                        .startEvent("child-start")
                        .endEvent("child-end")
                        .done()))
            .addProcessDefinitionData(
                processDefinitionDataOf(
                    "process",
                    2,
                    222L,
                    Bpmn.createExecutableProcess("process")
                        .startEvent("start")
                        .serviceTask("realTask")
                        .endEvent("end")
                        .done()))
            .build();

    // when: the instance ran the real deployment
    final ProcessModel model = ModelCreator.createModel(testData, "process", 222L);

    // then
    assertThat(model.getXml()).contains("realTask");
    assertThat(model.getVersion()).isEqualTo("2");
  }

  /**
   * Another deployment of the id describes a different process, so reporting it would explain the
   * instance by a model it never ran. Failing keeps that model out of the report.
   */
  @Test
  void shouldRejectADeploymentThatTheTestDataDoesNotDescribe() {
    // given
    final ImmutableCoverageTestData testData =
        ImmutableCoverageTestData.builder()
            .addProcessDefinitionData(
                processDefinitionDataOf(
                    "process",
                    1,
                    111L,
                    Bpmn.createExecutableProcess("process")
                        .startEvent("start")
                        .endEvent("end")
                        .done()))
            .build();

    // when: the instance ran a deployment that the test data does not describe
    // then
    assertThatThrownBy(() -> ModelCreator.createModel(testData, "process", 999L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("process")
        .hasMessageContaining("999");
  }

  @Test
  void shouldCollectTheElementsOfEachDeploymentOfAProcessDefinitionId() {
    // given: two deployments of one process definition id that differ in their elements
    final ProcessModel firstDeployment =
        processModelOf(
            Bpmn.createExecutableProcess("shared-id").startEvent("start").endEvent("end").done());
    final ProcessModel secondDeployment =
        processModelOf(
            Bpmn.createExecutableProcess("shared-id")
                .startEvent("start")
                .serviceTask("task", t -> t.zeebeJobType("work"))
                .endEvent("end")
                .done());

    // when
    final CoverableElements firstElements = ModelCreator.coverableElements(firstDeployment);
    final CoverableElements secondElements = ModelCreator.coverableElements(secondDeployment);

    // then
    assertThat(firstElements.getFlowNodeIds()).containsExactlyInAnyOrder("start", "end");
    assertThat(secondElements.getFlowNodeIds()).containsExactlyInAnyOrder("start", "task", "end");
  }

  @Test
  void shouldTellTheFlowNodesOfAModelFromItsSequenceFlows() {
    // given
    final ProcessModel model =
        processModelOf(
            Bpmn.createExecutableProcess("shared-id")
                .startEvent("start")
                .sequenceFlowId("flow")
                .endEvent("end")
                .done());

    // when
    final CoverableElements elements = ModelCreator.coverableElements(model);

    // then: an id is coverable as the kind of element it is, so that it cannot count twice
    assertThat(elements.getFlowNodeIds()).containsExactlyInAnyOrder("start", "end");
    assertThat(elements.getSequenceFlowIds()).containsExactly("flow");
    assertThat(elements.count()).isEqualTo(3);
  }

  private static ProcessModel processModelOf(final BpmnModelInstance bpmnModel) {
    return ImmutableProcessModel.builder()
        .processDefinitionId("shared-id")
        .version("1")
        .totalElementCount(0)
        .xml(Bpmn.convertToString(bpmnModel))
        .build();
  }

  private static ImmutableCoverageProcessDefinitionData processDefinitionDataOf(
      final String processDefinitionId,
      final int version,
      final long processDefinitionKey,
      final BpmnModelInstance bpmnModel) {

    final ProcessDefinition processDefinition = mock(ProcessDefinition.class);
    when(processDefinition.getProcessDefinitionId()).thenReturn(processDefinitionId);
    when(processDefinition.getVersion()).thenReturn(version);
    when(processDefinition.getProcessDefinitionKey()).thenReturn(processDefinitionKey);

    return ImmutableCoverageProcessDefinitionData.builder()
        .processDefinition(processDefinition)
        .xml(Bpmn.convertToString(bpmnModel))
        .build();
  }
}
