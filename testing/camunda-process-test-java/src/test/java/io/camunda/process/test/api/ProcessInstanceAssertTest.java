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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.client.CamundaClientNotFoundException;
import io.camunda.process.test.impl.client.FlowNodeInstanceDto;
import io.camunda.process.test.impl.client.FlowNodeInstanceState;
import io.camunda.process.test.impl.client.ProcessInstanceDto;
import io.camunda.process.test.impl.client.ProcessInstanceState;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
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
    processInstance.setState(ProcessInstanceState.TERMINATED);
    processInstance.setEndDate(END_DATE);
    return processInstance;
  }

  private static FlowNodeInstanceDto newActiveFlowNodeInstance(final String elementName) {
    final FlowNodeInstanceDto flowNodeInstance = new FlowNodeInstanceDto();
    flowNodeInstance.setFlowNodeName(elementName);
    flowNodeInstance.setProcessInstanceKey(PROCESS_INSTANCE_KEY);
    flowNodeInstance.setState(FlowNodeInstanceState.ACTIVE);
    flowNodeInstance.setStartDate(START_DATE);
    return flowNodeInstance;
  }

  private static FlowNodeInstanceDto newCompletedFlowNodeInstance(final String elementName) {
    final FlowNodeInstanceDto flowNodeInstance = newActiveFlowNodeInstance(elementName);
    flowNodeInstance.setState(FlowNodeInstanceState.COMPLETED);
    flowNodeInstance.setEndDate(END_DATE);
    return flowNodeInstance;
  }

  private static FlowNodeInstanceDto newTerminatedFlowNodeInstance(final String elementName) {
    final FlowNodeInstanceDto flowNodeInstance = newActiveFlowNodeInstance(elementName);
    flowNodeInstance.setState(FlowNodeInstanceState.TERMINATED);
    flowNodeInstance.setEndDate(END_DATE);
    return flowNodeInstance;
  }

  @Nested
  class ProcessInstanceSource {

    @Mock private ProcessInstanceResult processInstanceResult;

    @BeforeEach
    void configureMocks() throws IOException {
      final ProcessInstanceDto processInstance = newActiveProcessInstance(PROCESS_INSTANCE_KEY);
      when(camundaDataSource.getProcessInstance(PROCESS_INSTANCE_KEY)).thenReturn(processInstance);
    }

    @Test
    void shouldUseProcessInstanceEvent() throws IOException {
      // given
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when
      CamundaAssert.assertThat(processInstanceEvent).isActive();

      // then
      verify(camundaDataSource).getProcessInstance(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldUseProcessInstanceResult() throws IOException {
      // given
      when(processInstanceResult.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when
      CamundaAssert.assertThat(processInstanceResult).isActive();

      // then
      verify(camundaDataSource).getProcessInstance(PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class IsActive {

    @Test
    void shouldBeActive() throws IOException {
      // given
      final ProcessInstanceDto processInstance = newActiveProcessInstance(PROCESS_INSTANCE_KEY);
      when(camundaDataSource.getProcessInstance(PROCESS_INSTANCE_KEY)).thenReturn(processInstance);

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).isActive();
    }

    @Test
    void shouldFailIfCompleted() throws IOException {
      // given
      final ProcessInstanceDto processInstance = newCompletedProcessInstance(PROCESS_INSTANCE_KEY);
      when(camundaDataSource.getProcessInstance(PROCESS_INSTANCE_KEY)).thenReturn(processInstance);

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
      when(camundaDataSource.getProcessInstance(PROCESS_INSTANCE_KEY)).thenReturn(processInstance);

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
      doThrow(new CamundaClientNotFoundException())
          .when(camundaDataSource)
          .getProcessInstance(PROCESS_INSTANCE_KEY);

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
      when(camundaDataSource.getProcessInstance(PROCESS_INSTANCE_KEY)).thenReturn(processInstance);

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).isCompleted();
    }

    @Test
    void shouldFailIfTerminated() throws IOException {
      // given
      final ProcessInstanceDto processInstance = newTerminatedProcessInstance(PROCESS_INSTANCE_KEY);
      when(camundaDataSource.getProcessInstance(PROCESS_INSTANCE_KEY)).thenReturn(processInstance);

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

      when(camundaDataSource.getProcessInstance(PROCESS_INSTANCE_KEY))
          .thenReturn(processInstanceActive, processInstanceCompleted);

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).isCompleted();

      verify(camundaDataSource, times(2)).getProcessInstance(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfActive() throws IOException {
      // given
      final ProcessInstanceDto processInstance = newActiveProcessInstance(PROCESS_INSTANCE_KEY);

      when(camundaDataSource.getProcessInstance(PROCESS_INSTANCE_KEY)).thenReturn(processInstance);

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).isCompleted())
          .hasMessage(
              "Process instance [key: %d] should be completed but was active.",
              PROCESS_INSTANCE_KEY);

      verify(camundaDataSource, atLeast(2)).getProcessInstance(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfNotFound() throws IOException {
      // given
      doThrow(new CamundaClientNotFoundException())
          .when(camundaDataSource)
          .getProcessInstance(PROCESS_INSTANCE_KEY);

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
      when(camundaDataSource.getProcessInstance(PROCESS_INSTANCE_KEY)).thenReturn(processInstance);

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).isTerminated();
    }

    @Test
    void shouldFailIfCompleted() throws IOException {
      // given
      final ProcessInstanceDto processInstance = newCompletedProcessInstance(PROCESS_INSTANCE_KEY);
      when(camundaDataSource.getProcessInstance(PROCESS_INSTANCE_KEY)).thenReturn(processInstance);

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

      when(camundaDataSource.getProcessInstance(PROCESS_INSTANCE_KEY))
          .thenReturn(processInstanceActive, processInstanceTerminated);

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).isTerminated();

      verify(camundaDataSource, times(2)).getProcessInstance(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfActive() throws IOException {
      // given
      final ProcessInstanceDto processInstance = newActiveProcessInstance(PROCESS_INSTANCE_KEY);

      when(camundaDataSource.getProcessInstance(PROCESS_INSTANCE_KEY)).thenReturn(processInstance);

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).isTerminated())
          .hasMessage(
              "Process instance [key: %d] should be terminated but was active.",
              PROCESS_INSTANCE_KEY);

      verify(camundaDataSource, atLeast(2)).getProcessInstance(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfNotFound() throws IOException {
      // given
      doThrow(new CamundaClientNotFoundException())
          .when(camundaDataSource)
          .getProcessInstance(PROCESS_INSTANCE_KEY);

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
  }

  @Nested
  class HasCompletedElements {

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
  }

  @Nested
  class HasTerminatedElements {

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
  }
}
