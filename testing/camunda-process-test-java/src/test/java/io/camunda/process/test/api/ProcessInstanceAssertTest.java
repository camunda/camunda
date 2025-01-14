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

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.response.ProcessInstanceResult;
import io.camunda.client.api.search.response.FlowNodeInstanceState;
import io.camunda.process.test.api.assertions.ElementSelectors;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.client.FlowNodeInstanceDto;
import io.camunda.process.test.impl.client.ProcessInstanceDto;
import io.camunda.process.test.impl.client.ProcessInstanceState;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProcessInstanceAssertTest {

  private static final long PROCESS_INSTANCE_KEY = 1L;
  private static final String BPMN_PROCESS_ID = "process";
  private static final String START_DATE = "2024-07-01T09:45:00";
  private static final String END_DATE = "2024-07-01T10:00:00";

  @Mock private CamundaDataSource camundaDataSource;
  @Mock private ProcessInstanceEvent processInstanceEvent;

  @BeforeEach
  void configureAssertions() {
    CamundaAssert.initialize(camundaDataSource);
    CamundaAssert.setAssertionInterval(Duration.ZERO);
    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(1));
  }

  @AfterEach
  void resetAssertions() {
    CamundaAssert.setAssertionInterval(CamundaAssert.DEFAULT_ASSERTION_INTERVAL);
    CamundaAssert.setAssertionTimeout(CamundaAssert.DEFAULT_ASSERTION_TIMEOUT);
  }

  private static ProcessInstanceDto newActiveProcessInstance(final long processInstanceKey) {
    final ProcessInstanceDto processInstance = new ProcessInstanceDto();
    processInstance.setKey(processInstanceKey);
    processInstance.setBpmnProcessId(BPMN_PROCESS_ID);
    processInstance.setState(ProcessInstanceState.ACTIVE);
    processInstance.setStartDate(START_DATE);
    return processInstance;
  }

  private static ProcessInstanceDto newCompletedProcessInstance(final long processInstanceKey) {
    final ProcessInstanceDto processInstance = newActiveProcessInstance(processInstanceKey);
    processInstance.setState(ProcessInstanceState.COMPLETED);
    processInstance.setEndDate(END_DATE);
    return processInstance;
  }

  private static ProcessInstanceDto newTerminatedProcessInstance(final long processInstanceKey) {
    final ProcessInstanceDto processInstance = newActiveProcessInstance(processInstanceKey);
    processInstance.setState(ProcessInstanceState.CANCELED);
    processInstance.setEndDate(END_DATE);
    return processInstance;
  }

  private static FlowNodeInstanceDto newActiveFlowNodeInstance(final String elementId) {
    final FlowNodeInstanceDto flowNodeInstance = new FlowNodeInstanceDto();
    flowNodeInstance.setFlowNodeId(elementId);
    flowNodeInstance.setFlowNodeName("element_" + elementId);
    flowNodeInstance.setProcessInstanceKey(PROCESS_INSTANCE_KEY);
    flowNodeInstance.setState(FlowNodeInstanceState.ACTIVE);
    flowNodeInstance.setStartDate(START_DATE);
    return flowNodeInstance;
  }

  private static FlowNodeInstanceDto newCompletedFlowNodeInstance(final String elementId) {
    final FlowNodeInstanceDto flowNodeInstance = newActiveFlowNodeInstance(elementId);
    flowNodeInstance.setState(FlowNodeInstanceState.COMPLETED);
    flowNodeInstance.setEndDate(END_DATE);
    return flowNodeInstance;
  }

  private static FlowNodeInstanceDto newTerminatedFlowNodeInstance(final String elementId) {
    final FlowNodeInstanceDto flowNodeInstance = newActiveFlowNodeInstance(elementId);
    flowNodeInstance.setState(FlowNodeInstanceState.TERMINATED);
    flowNodeInstance.setEndDate(END_DATE);
    return flowNodeInstance;
  }

  @Nested
  class ProcessInstanceSource {

    private static final long ACTIVE_PROCESS_INSTANCE_KEY = 1L;
    private static final long COMPLETED_PROCESS_INSTANCE_KEY = 2L;

    @Mock private ProcessInstanceResult processInstanceResult;

    @BeforeEach
    void configureMocks() throws IOException {
      final ProcessInstanceDto activeProcessInstance =
          newActiveProcessInstance(ACTIVE_PROCESS_INSTANCE_KEY);
      activeProcessInstance.setBpmnProcessId("active-process");

      final ProcessInstanceDto completedProcessInstance =
          newCompletedProcessInstance(COMPLETED_PROCESS_INSTANCE_KEY);
      completedProcessInstance.setBpmnProcessId("completed-process");

      when(camundaDataSource.findProcessInstances())
          .thenReturn(Arrays.asList(activeProcessInstance, completedProcessInstance));
    }

    @Test
    void shouldUseProcessInstanceEvent() throws IOException {
      // given
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(ACTIVE_PROCESS_INSTANCE_KEY);

      // when
      CamundaAssert.assertThat(processInstanceEvent).isActive();

      // then
      verify(camundaDataSource).findProcessInstances();
    }

    @Test
    void shouldFailWithProcessInstanceEvent() throws IOException {
      // given
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(COMPLETED_PROCESS_INSTANCE_KEY);

      // when
      Assertions.assertThatThrownBy(() -> CamundaAssert.assertThat(processInstanceEvent).isActive())
          .hasMessage(
              "Process instance [key: %d] should be active but was completed.",
              COMPLETED_PROCESS_INSTANCE_KEY);

      // then
      verify(camundaDataSource).findProcessInstances();
    }

    @Test
    void shouldUseProcessInstanceResult() throws IOException {
      // given
      when(processInstanceResult.getProcessInstanceKey()).thenReturn(ACTIVE_PROCESS_INSTANCE_KEY);

      // when
      CamundaAssert.assertThat(processInstanceResult).isActive();

      // then
      verify(camundaDataSource).findProcessInstances();
    }

    @Test
    void shouldFailWithProcessInstanceResult() throws IOException {
      // given
      when(processInstanceResult.getProcessInstanceKey())
          .thenReturn(COMPLETED_PROCESS_INSTANCE_KEY);

      // when
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceResult).isActive())
          .hasMessage(
              "Process instance [key: %d] should be active but was completed.",
              COMPLETED_PROCESS_INSTANCE_KEY);

      // then
      verify(camundaDataSource).findProcessInstances();
    }

    @Test
    void shouldUseByKeySelector() throws IOException {
      // when
      CamundaAssert.assertThat(ProcessInstanceSelectors.byKey(ACTIVE_PROCESS_INSTANCE_KEY))
          .isActive();

      // then
      verify(camundaDataSource).findProcessInstances();
    }

    @Test
    void shouldFailWithByKeySelector() throws IOException {
      // when
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThat(
                          ProcessInstanceSelectors.byKey(COMPLETED_PROCESS_INSTANCE_KEY))
                      .isActive())
          .hasMessage(
              "Process instance [key: %d] should be active but was completed.",
              COMPLETED_PROCESS_INSTANCE_KEY);

      // then
      verify(camundaDataSource).findProcessInstances();
    }

    @Test
    void shouldUseByProcessIdSelector() throws IOException {
      // when
      CamundaAssert.assertThat(ProcessInstanceSelectors.byProcessId("active-process")).isActive();

      // then
      verify(camundaDataSource).findProcessInstances();
    }

    @Test
    void shouldFailWithByProcessIdSelector() throws IOException {
      // when
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThat(
                          ProcessInstanceSelectors.byProcessId("completed-process"))
                      .isActive())
          .hasMessage(
              "Process instance [process-id: '%s'] should be active but was completed.",
              "completed-process");

      // then
      verify(camundaDataSource).findProcessInstances();
    }
  }

  @Nested
  class IsActive {

    @Test
    void shouldBeActive() throws IOException {
      // given
      final ProcessInstanceDto processInstance = newActiveProcessInstance(PROCESS_INSTANCE_KEY);
      when(camundaDataSource.findProcessInstances())
          .thenReturn(Collections.singletonList(processInstance));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).isActive();
    }

    @Test
    void shouldFailIfCompleted() throws IOException {
      // given
      final ProcessInstanceDto processInstance = newCompletedProcessInstance(PROCESS_INSTANCE_KEY);
      when(camundaDataSource.findProcessInstances())
          .thenReturn(Collections.singletonList(processInstance));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(() -> CamundaAssert.assertThat(processInstanceEvent).isActive())
          .hasMessage(
              "Process instance [key: %d] should be active but was completed.",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfTerminated() throws IOException {
      // given
      final ProcessInstanceDto processInstance = newTerminatedProcessInstance(PROCESS_INSTANCE_KEY);
      when(camundaDataSource.findProcessInstances())
          .thenReturn(Collections.singletonList(processInstance));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(() -> CamundaAssert.assertThat(processInstanceEvent).isActive())
          .hasMessage(
              "Process instance [key: %d] should be active but was terminated.",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfNotFound() throws IOException {
      // given
      when(camundaDataSource.findProcessInstances()).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(() -> CamundaAssert.assertThat(processInstanceEvent).isActive())
          .hasMessage(
              "Process instance [key: %d] should be active but was not activated.",
              PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class IsCompleted {

    @Test
    void shouldBeCompleted() throws IOException {
      // given
      final ProcessInstanceDto processInstance = newCompletedProcessInstance(PROCESS_INSTANCE_KEY);
      when(camundaDataSource.findProcessInstances())
          .thenReturn(Collections.singletonList(processInstance));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).isCompleted();
    }

    @Test
    void shouldFailIfTerminated() throws IOException {
      // given
      final ProcessInstanceDto processInstance = newTerminatedProcessInstance(PROCESS_INSTANCE_KEY);
      when(camundaDataSource.findProcessInstances())
          .thenReturn(Collections.singletonList(processInstance));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).isCompleted())
          .hasMessage(
              "Process instance [key: %d] should be completed but was terminated.",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldWaitUntilCompleted() throws IOException {
      // given
      final ProcessInstanceDto processInstanceActive =
          newActiveProcessInstance(PROCESS_INSTANCE_KEY);
      final ProcessInstanceDto processInstanceCompleted =
          newCompletedProcessInstance(PROCESS_INSTANCE_KEY);

      when(camundaDataSource.findProcessInstances())
          .thenReturn(Collections.singletonList(processInstanceActive))
          .thenReturn(Collections.singletonList(processInstanceCompleted));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).isCompleted();

      verify(camundaDataSource, times(2)).findProcessInstances();
    }

    @Test
    void shouldFailIfActive() throws IOException {
      // given
      final ProcessInstanceDto processInstance = newActiveProcessInstance(PROCESS_INSTANCE_KEY);

      when(camundaDataSource.findProcessInstances())
          .thenReturn(Collections.singletonList(processInstance));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).isCompleted())
          .hasMessage(
              "Process instance [key: %d] should be completed but was active.",
              PROCESS_INSTANCE_KEY);

      verify(camundaDataSource, atLeast(2)).findProcessInstances();
    }

    @Test
    void shouldFailIfNotFound() throws IOException {
      // given
      when(camundaDataSource.findProcessInstances()).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).isCompleted())
          .hasMessage(
              "Process instance [key: %d] should be completed but was not activated.",
              PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class IsTerminated {

    @Test
    void shouldBeTerminated() throws IOException {
      // given
      final ProcessInstanceDto processInstance = newTerminatedProcessInstance(PROCESS_INSTANCE_KEY);
      when(camundaDataSource.findProcessInstances())
          .thenReturn(Collections.singletonList(processInstance));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).isTerminated();
    }

    @Test
    void shouldFailIfCompleted() throws IOException {
      // given
      final ProcessInstanceDto processInstance = newCompletedProcessInstance(PROCESS_INSTANCE_KEY);
      when(camundaDataSource.findProcessInstances())
          .thenReturn(Collections.singletonList(processInstance));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).isTerminated())
          .hasMessage(
              "Process instance [key: %d] should be terminated but was completed.",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldWaitUntilTerminated() throws IOException {
      // given
      final ProcessInstanceDto processInstanceActive =
          newActiveProcessInstance(PROCESS_INSTANCE_KEY);
      final ProcessInstanceDto processInstanceTerminated =
          newTerminatedProcessInstance(PROCESS_INSTANCE_KEY);

      when(camundaDataSource.findProcessInstances())
          .thenReturn(Collections.singletonList(processInstanceActive))
          .thenReturn(Collections.singletonList(processInstanceTerminated));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).isTerminated();

      verify(camundaDataSource, times(2)).findProcessInstances();
    }

    @Test
    void shouldFailIfActive() throws IOException {
      // given
      final ProcessInstanceDto processInstance = newActiveProcessInstance(PROCESS_INSTANCE_KEY);

      when(camundaDataSource.findProcessInstances())
          .thenReturn(Collections.singletonList(processInstance));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).isTerminated())
          .hasMessage(
              "Process instance [key: %d] should be terminated but was active.",
              PROCESS_INSTANCE_KEY);

      verify(camundaDataSource, atLeast(2)).findProcessInstances();
    }

    @Test
    void shouldFailIfNotFound() throws IOException {
      // given
      when(camundaDataSource.findProcessInstances()).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).isTerminated())
          .hasMessage(
              "Process instance [key: %d] should be terminated but was not activated.",
              PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class HasActiveElements {

    @BeforeEach
    void configureMocks() throws IOException {
      when(camundaDataSource.findProcessInstances())
          .thenReturn(Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY)));
    }

    @Test
    void shouldHasActiveElements() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceA = newActiveFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceB = newActiveFlowNodeInstance("B");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasActiveElements("A", "B");
    }

    @Test
    void shouldHasTwoActiveElements() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceActive = newActiveFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceCompleted = newCompletedFlowNodeInstance("A");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceCompleted, flowNodeInstanceActive));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasActiveElements("A", "A");
    }

    @Test
    void shouldWaitUntilHasActiveElements() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceA = newActiveFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceB = newActiveFlowNodeInstance("B");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(flowNodeInstanceA))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasActiveElements("A", "B");

      verify(camundaDataSource, times(2))
          .getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfElementsNotFound() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceA = newActiveFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceB = newActiveFlowNodeInstance("B");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasActiveElements("A", "C", "D"))
          .hasMessage(
              "Process instance [key: %d] should have active elements ['A', 'C', 'D'] but the following elements were not active:\n"
                  + "\t- 'C': not activated\n"
                  + "\t- 'D': not activated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfElementsNotActive() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceA = newActiveFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceB = newCompletedFlowNodeInstance("B");
      final FlowNodeInstanceDto flowNodeInstanceC = newTerminatedFlowNodeInstance("C");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB, flowNodeInstanceC));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasActiveElements("A", "B", "C"))
          .hasMessage(
              "Process instance [key: %d] should have active elements ['A', 'B', 'C'] but the following elements were not active:\n"
                  + "\t- 'B': completed\n"
                  + "\t- 'C': terminated",
              PROCESS_INSTANCE_KEY);

      verify(camundaDataSource).getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailWithSameElementId() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceA = newActiveFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceA2 = newActiveFlowNodeInstance("A");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceA2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasActiveElements("B"))
          .hasMessage(
              "Process instance [key: %d] should have active elements ['B'] but the following elements were not active:\n"
                  + "\t- 'B': not activated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldUseElementSelectorById() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceA = newActiveFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceB = newActiveFlowNodeInstance("B");
      final FlowNodeInstanceDto flowNodeInstanceC = newCompletedFlowNodeInstance("C");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB, flowNodeInstanceC));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent)
          .hasActiveElements(ElementSelectors.byId("A"), ElementSelectors.byId("B"));

      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThat(processInstanceEvent)
                      .hasActiveElements(
                          ElementSelectors.byId("A"),
                          ElementSelectors.byId("C"),
                          ElementSelectors.byId("D")))
          .hasMessage(
              "Process instance [key: %d] should have active elements ['A', 'C', 'D'] but the following elements were not active:\n"
                  + "\t- 'C': completed\n"
                  + "\t- 'D': not activated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldUseElementSelectorByName() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceA = newActiveFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceB = newActiveFlowNodeInstance("B");
      final FlowNodeInstanceDto flowNodeInstanceC = newCompletedFlowNodeInstance("C");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB, flowNodeInstanceC));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent)
          .hasActiveElements(
              ElementSelectors.byName("element_A"), ElementSelectors.byName("element_B"));

      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThat(processInstanceEvent)
                      .hasActiveElements(
                          ElementSelectors.byName("element_A"),
                          ElementSelectors.byName("element_C"),
                          ElementSelectors.byName("element_D")))
          .hasMessage(
              "Process instance [key: %d] should have active elements ['element_A', 'element_C', 'element_D'] but the following elements were not active:\n"
                  + "\t- 'element_C': completed\n"
                  + "\t- 'element_D': not activated",
              PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class HasCompletedElements {

    @BeforeEach
    void configureMocks() throws IOException {
      when(camundaDataSource.findProcessInstances())
          .thenReturn(Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY)));
    }

    @Test
    void shouldHasCompletedElements() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceA = newCompletedFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceB = newCompletedFlowNodeInstance("B");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("A", "B");
    }

    @Test
    void shouldHasTwoCompletedElements() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceActive = newCompletedFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceCompleted = newActiveFlowNodeInstance("A");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceCompleted, flowNodeInstanceActive));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("A", "A");
    }

    @Test
    void shouldWaitUntilHasCompletedElements() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceA = newCompletedFlowNodeInstance("A");
      final FlowNodeInstanceDto activeFlowNodeInstanceB = newActiveFlowNodeInstance("B");
      final FlowNodeInstanceDto completedFlowNodeInstanceB = newCompletedFlowNodeInstance("B");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceA, activeFlowNodeInstanceB))
          .thenReturn(Arrays.asList(flowNodeInstanceA, completedFlowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("A", "B");

      verify(camundaDataSource, times(2))
          .getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfElementsNotFound() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceA = newCompletedFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceB = newCompletedFlowNodeInstance("B");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThat(processInstanceEvent)
                      .hasCompletedElements("A", "C", "D"))
          .hasMessage(
              "Process instance [key: %d] should have completed elements ['A', 'C', 'D'] but the following elements were not completed:\n"
                  + "\t- 'C': not activated\n"
                  + "\t- 'D': not activated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfElementsNotCompleted() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceA = newCompletedFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceB = newTerminatedFlowNodeInstance("B");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("A", "B"))
          .hasMessage(
              "Process instance [key: %d] should have completed elements ['A', 'B'] but the following elements were not completed:\n"
                  + "\t- 'B': terminated",
              PROCESS_INSTANCE_KEY);

      verify(camundaDataSource).getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailWithSameElementId() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceA = newCompletedFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceA2 = newCompletedFlowNodeInstance("A");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceA2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("B"))
          .hasMessage(
              "Process instance [key: %d] should have completed elements ['B'] but the following elements were not completed:\n"
                  + "\t- 'B': not activated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldUseElementSelectorById() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceA = newCompletedFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceB = newCompletedFlowNodeInstance("B");
      final FlowNodeInstanceDto flowNodeInstanceC = newActiveFlowNodeInstance("C");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB, flowNodeInstanceC));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent)
          .hasCompletedElements(ElementSelectors.byId("A"), ElementSelectors.byId("B"));

      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThat(processInstanceEvent)
                      .hasCompletedElements(
                          ElementSelectors.byId("A"),
                          ElementSelectors.byId("C"),
                          ElementSelectors.byId("D")))
          .hasMessage(
              "Process instance [key: %d] should have completed elements ['A', 'C', 'D'] but the following elements were not completed:\n"
                  + "\t- 'C': active\n"
                  + "\t- 'D': not activated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldUseElementSelectorByName() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceA = newCompletedFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceB = newCompletedFlowNodeInstance("B");
      final FlowNodeInstanceDto flowNodeInstanceC = newActiveFlowNodeInstance("C");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB, flowNodeInstanceC));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent)
          .hasCompletedElements(
              ElementSelectors.byName("element_A"), ElementSelectors.byName("element_B"));

      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThat(processInstanceEvent)
                      .hasCompletedElements(
                          ElementSelectors.byName("element_A"),
                          ElementSelectors.byName("element_C"),
                          ElementSelectors.byName("element_D")))
          .hasMessage(
              "Process instance [key: %d] should have completed elements ['element_A', 'element_C', 'element_D'] but the following elements were not completed:\n"
                  + "\t- 'element_C': active\n"
                  + "\t- 'element_D': not activated",
              PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class HasTerminatedElements {

    @BeforeEach
    void configureMocks() throws IOException {
      when(camundaDataSource.findProcessInstances())
          .thenReturn(Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY)));
    }

    @Test
    void shouldHasTerminatedElements() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceA = newTerminatedFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceB = newTerminatedFlowNodeInstance("B");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasTerminatedElements("A", "B");
    }

    @Test
    void shouldHasTwoTerminatedElements() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceActive = newTerminatedFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceCompleted = newActiveFlowNodeInstance("A");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceCompleted, flowNodeInstanceActive));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasTerminatedElements("A", "A");
    }

    @Test
    void shouldWaitUntilHasTerminatedElements() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceA = newTerminatedFlowNodeInstance("A");
      final FlowNodeInstanceDto activeFlowNodeInstanceB = newActiveFlowNodeInstance("B");
      final FlowNodeInstanceDto terminatedFlowNodeInstanceB = newTerminatedFlowNodeInstance("B");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceA, activeFlowNodeInstanceB))
          .thenReturn(Arrays.asList(flowNodeInstanceA, terminatedFlowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasTerminatedElements("A", "B");

      verify(camundaDataSource, times(2))
          .getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfElementsNotFound() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceA = newTerminatedFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceB = newTerminatedFlowNodeInstance("B");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThat(processInstanceEvent)
                      .hasTerminatedElements("A", "C", "D"))
          .hasMessage(
              "Process instance [key: %d] should have terminated elements ['A', 'C', 'D'] but the following elements were not terminated:\n"
                  + "\t- 'C': not activated\n"
                  + "\t- 'D': not activated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfElementsNotTerminated() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceA = newCompletedFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceB = newTerminatedFlowNodeInstance("B");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasTerminatedElements("A", "B"))
          .hasMessage(
              "Process instance [key: %d] should have terminated elements ['A', 'B'] but the following elements were not terminated:\n"
                  + "\t- 'A': completed",
              PROCESS_INSTANCE_KEY);

      verify(camundaDataSource).getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailWithSameElementId() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceA = newCompletedFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceA2 = newCompletedFlowNodeInstance("A");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceA2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasTerminatedElements("B"))
          .hasMessage(
              "Process instance [key: %d] should have terminated elements ['B'] but the following elements were not terminated:\n"
                  + "\t- 'B': not activated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldUseElementSelectorById() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceA = newTerminatedFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceB = newTerminatedFlowNodeInstance("B");
      final FlowNodeInstanceDto flowNodeInstanceC = newActiveFlowNodeInstance("C");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB, flowNodeInstanceC));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent)
          .hasTerminatedElements(ElementSelectors.byId("A"), ElementSelectors.byId("B"));

      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThat(processInstanceEvent)
                      .hasTerminatedElements(
                          ElementSelectors.byId("A"),
                          ElementSelectors.byId("C"),
                          ElementSelectors.byId("D")))
          .hasMessage(
              "Process instance [key: %d] should have terminated elements ['A', 'C', 'D'] but the following elements were not terminated:\n"
                  + "\t- 'C': active\n"
                  + "\t- 'D': not activated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldUseElementSelectorByName() throws IOException {
      // given
      final FlowNodeInstanceDto flowNodeInstanceA = newTerminatedFlowNodeInstance("A");
      final FlowNodeInstanceDto flowNodeInstanceB = newTerminatedFlowNodeInstance("B");
      final FlowNodeInstanceDto flowNodeInstanceC = newActiveFlowNodeInstance("C");

      when(camundaDataSource.getFlowNodeInstancesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB, flowNodeInstanceC));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent)
          .hasTerminatedElements(
              ElementSelectors.byName("element_A"), ElementSelectors.byName("element_B"));

      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThat(processInstanceEvent)
                      .hasTerminatedElements(
                          ElementSelectors.byName("element_A"),
                          ElementSelectors.byName("element_C"),
                          ElementSelectors.byName("element_D")))
          .hasMessage(
              "Process instance [key: %d] should have terminated elements ['element_A', 'element_C', 'element_D'] but the following elements were not terminated:\n"
                  + "\t- 'element_C': active\n"
                  + "\t- 'element_D': not activated",
              PROCESS_INSTANCE_KEY);
    }
  }
}
