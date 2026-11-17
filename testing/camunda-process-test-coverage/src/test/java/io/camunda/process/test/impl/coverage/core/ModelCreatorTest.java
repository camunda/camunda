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
}
