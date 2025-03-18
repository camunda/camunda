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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.FlowNodeInstanceFilter;
import io.camunda.client.api.search.filter.FlownodeInstanceFilter;
import io.camunda.client.api.search.response.FlowNodeInstance;
import io.camunda.process.test.api.assertions.ElementSelectors;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.utils.FlowNodeInstanceBuilder;
import io.camunda.process.test.utils.ProcessInstanceBuilder;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ElementAssertTest {

  private static final long PROCESS_INSTANCE_KEY = 1L;

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

  @BeforeEach
  void configureMocks() {
    when(camundaDataSource.findProcessInstances(any()))
        .thenReturn(
            Collections.singletonList(
                ProcessInstanceBuilder.newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));
  }

  private static FlowNodeInstance newActiveFlowNodeInstance(final String elementId) {
    return FlowNodeInstanceBuilder.newActiveFlowNodeInstance(elementId, PROCESS_INSTANCE_KEY)
        .build();
  }

  private static FlowNodeInstance newCompletedFlowNodeInstance(final String elementId) {
    return FlowNodeInstanceBuilder.newCompletedFlowNodeInstance(elementId, PROCESS_INSTANCE_KEY)
        .build();
  }

  private static FlowNodeInstance newTerminatedFlowNodeInstance(final String elementId) {
    return FlowNodeInstanceBuilder.newTerminatedFlowNodeInstance(elementId, PROCESS_INSTANCE_KEY)
        .build();
  }

  @Nested
  class ElementSource {

    @Mock private FlownodeInstanceFilter flownodeInstanceFilter;
    @Captor private ArgumentCaptor<Consumer<FlownodeInstanceFilter>> flowNodeInstanceFilterCapture;

    @BeforeEach
    void configureMocks() {
      final FlowNodeInstance flowNodeInstanceA = newActiveFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceB = newActiveFlowNodeInstance("B");
      final FlowNodeInstance flowNodeInstanceC = newCompletedFlowNodeInstance("C");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB, flowNodeInstanceC));
    }

    @AfterEach
    void reset() {
      CamundaAssert.setElementSelector(CamundaAssert.DEFAULT_ELEMENT_SELECTOR);
    }

    @Test
    void shouldUseStringSelector() {
      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasActiveElements("A");

      verify(camundaDataSource).findFlowNodeInstances(flowNodeInstanceFilterCapture.capture());

      flowNodeInstanceFilterCapture.getValue().accept(flownodeInstanceFilter);
      verify(flownodeInstanceFilter).processInstanceKey(PROCESS_INSTANCE_KEY);
      verify(flownodeInstanceFilter).flowNodeId("A");
    }

    @Test
    void shouldFailWithStringSelector() {
      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasActiveElements("A", "C", "D"))
          .hasMessage(
              "Process instance [key: %d] should have active elements ['A', 'C', 'D'] but the following elements were not active:\n"
                  + "\t- 'C': completed\n"
                  + "\t- 'D': not activated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldChangeStringSelector() {
      // given
      CamundaAssert.setElementSelector(ElementSelectors::byName);

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasActiveElements("element_A", "element_B");
    }

    @Test
    void shouldUseByIdSelector() {
      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasActiveElements(ElementSelectors.byId("A"));

      verify(camundaDataSource).findFlowNodeInstances(flowNodeInstanceFilterCapture.capture());

      flowNodeInstanceFilterCapture.getValue().accept(flownodeInstanceFilter);
      verify(flownodeInstanceFilter).processInstanceKey(PROCESS_INSTANCE_KEY);
      verify(flownodeInstanceFilter).flowNodeId("A");
    }

    @Test
    void shouldFailWithByIdSelector() {
      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
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
    void shouldUseByNameSelector() {
      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent)
          .hasActiveElements(ElementSelectors.byName("element_A"));

      verify(camundaDataSource).findFlowNodeInstances(flowNodeInstanceFilterCapture.capture());

      flowNodeInstanceFilterCapture.getValue().accept(flownodeInstanceFilter);
      verify(flownodeInstanceFilter).processInstanceKey(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailWithByNameSelector() {
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
  class HasActiveElements {

    @Test
    void shouldHasActiveElements() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newActiveFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceB = newActiveFlowNodeInstance("B");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasActiveElements("A", "B");
    }

    @Test
    void shouldHasTwoActiveElements() {
      // given
      final FlowNodeInstance flowNodeInstanceActive = newActiveFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceCompleted = newCompletedFlowNodeInstance("A");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstanceCompleted, flowNodeInstanceActive));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasActiveElements("A", "A");
    }

    @Test
    void shouldWaitUntilHasActiveElements() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newActiveFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceB = newActiveFlowNodeInstance("B");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Collections.singletonList(flowNodeInstanceA))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasActiveElements("A", "B");

      verify(camundaDataSource, times(2)).findFlowNodeInstances(any());
    }

    @Test
    void shouldFailIfElementsNotFound() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newActiveFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceB = newActiveFlowNodeInstance("B");

      when(camundaDataSource.findFlowNodeInstances(any()))
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
    void shouldFailIfElementsNotActive() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newActiveFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceB = newCompletedFlowNodeInstance("B");
      final FlowNodeInstance flowNodeInstanceC = newTerminatedFlowNodeInstance("C");

      when(camundaDataSource.findFlowNodeInstances(any()))
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
    }

    @Test
    void shouldFailWithSameElementId() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newActiveFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceA2 = newActiveFlowNodeInstance("A");

      when(camundaDataSource.findFlowNodeInstances(any()))
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
    void shouldFailIfProcessInstanceNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasActiveElements("A"))
          .hasMessage("No process instance [key: %d] found.", PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class HasCompletedElements {

    @Test
    void shouldHasCompletedElements() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newCompletedFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceB = newCompletedFlowNodeInstance("B");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("A", "B");
    }

    @Test
    void shouldHasTwoCompletedElements() {
      // given
      final FlowNodeInstance flowNodeInstanceActive = newCompletedFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceCompleted = newActiveFlowNodeInstance("A");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstanceCompleted, flowNodeInstanceActive));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("A", "A");
    }

    @Test
    void shouldWaitUntilHasCompletedElements() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newCompletedFlowNodeInstance("A");
      final FlowNodeInstance activeFlowNodeInstanceB = newActiveFlowNodeInstance("B");
      final FlowNodeInstance completedFlowNodeInstanceB = newCompletedFlowNodeInstance("B");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstanceA, activeFlowNodeInstanceB))
          .thenReturn(Arrays.asList(flowNodeInstanceA, completedFlowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("A", "B");

      verify(camundaDataSource, times(2)).findFlowNodeInstances(any());
    }

    @Test
    void shouldFailIfElementsNotFound() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newCompletedFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceB = newCompletedFlowNodeInstance("B");

      when(camundaDataSource.findFlowNodeInstances(any()))
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
    void shouldFailIfElementsNotCompleted() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newCompletedFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceB = newTerminatedFlowNodeInstance("B");

      when(camundaDataSource.findFlowNodeInstances(any()))
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
    }

    @Test
    void shouldFailWithSameElementId() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newCompletedFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceA2 = newCompletedFlowNodeInstance("A");

      when(camundaDataSource.findFlowNodeInstances(any()))
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
    void shouldFailIfProcessInstanceNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("A"))
          .hasMessage("No process instance [key: %d] found.", PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class HasTerminatedElements {

    @Test
    void shouldHasTerminatedElements() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newTerminatedFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceB = newTerminatedFlowNodeInstance("B");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasTerminatedElements("A", "B");
    }

    @Test
    void shouldHasTwoTerminatedElements() {
      // given
      final FlowNodeInstance flowNodeInstanceActive = newTerminatedFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceCompleted = newActiveFlowNodeInstance("A");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstanceCompleted, flowNodeInstanceActive));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasTerminatedElements("A", "A");
    }

    @Test
    void shouldWaitUntilHasTerminatedElements() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newTerminatedFlowNodeInstance("A");
      final FlowNodeInstance activeFlowNodeInstanceB = newActiveFlowNodeInstance("B");
      final FlowNodeInstance terminatedFlowNodeInstanceB = newTerminatedFlowNodeInstance("B");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstanceA, activeFlowNodeInstanceB))
          .thenReturn(Arrays.asList(flowNodeInstanceA, terminatedFlowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasTerminatedElements("A", "B");

      verify(camundaDataSource, times(2)).findFlowNodeInstances(any());
    }

    @Test
    void shouldFailIfElementsNotFound() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newTerminatedFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceB = newTerminatedFlowNodeInstance("B");

      when(camundaDataSource.findFlowNodeInstances(any()))
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
    void shouldFailIfElementsNotTerminated() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newCompletedFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceB = newTerminatedFlowNodeInstance("B");

      when(camundaDataSource.findFlowNodeInstances(any()))
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
    }

    @Test
    void shouldFailWithSameElementId() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newCompletedFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceA2 = newCompletedFlowNodeInstance("A");

      when(camundaDataSource.findFlowNodeInstances(any()))
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
    void shouldFailIfProcessInstanceNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasTerminatedElements("A"))
          .hasMessage("No process instance [key: %d] found.", PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class HasActiveElement {

    @Test
    void shouldHasActiveElement() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newActiveFlowNodeInstance("A");

      final FlowNodeInstance flowNodeInstanceB1 = newActiveFlowNodeInstance("B");
      final FlowNodeInstance flowNodeInstanceB2 = newActiveFlowNodeInstance("B");

      final FlowNodeInstance flowNodeInstanceC1 = newActiveFlowNodeInstance("C");
      final FlowNodeInstance flowNodeInstanceC2 = newActiveFlowNodeInstance("C");
      final FlowNodeInstance flowNodeInstanceC3 = newActiveFlowNodeInstance("C");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(
              Arrays.asList(
                  flowNodeInstanceA,
                  flowNodeInstanceB1,
                  flowNodeInstanceB2,
                  flowNodeInstanceC1,
                  flowNodeInstanceC2,
                  flowNodeInstanceC3));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent)
          .hasActiveElement("A", 1)
          .hasActiveElement("B", 2)
          .hasActiveElement(ElementSelectors.byId("C"), 3);
    }

    @Test
    void shouldFailIfNumberIsGreater() {
      // given
      final FlowNodeInstance flowNodeInstance1 = newActiveFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstance2 = newActiveFlowNodeInstance("A");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstance1, flowNodeInstance2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasActiveElement("A", 1))
          .hasMessage(
              "Process instance [key: %d] should have active element 'A' 1 times but was 2. Element instances:\n"
                  + "\t- 'A': active\n"
                  + "\t- 'A': active",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfNumberIsLess() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newActiveFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceB = newActiveFlowNodeInstance("B");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasActiveElement("A", 2))
          .hasMessage(
              "Process instance [key: %d] should have active element 'A' 2 times but was 1. Element instances:\n"
                  + "\t- 'A': active",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfNotActive() {
      // given
      final FlowNodeInstance flowNodeInstanceA1 = newCompletedFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceA2 = newTerminatedFlowNodeInstance("A");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstanceA1, flowNodeInstanceA2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasActiveElement("A", 2))
          .hasMessage(
              "Process instance [key: %d] should have active element 'A' 2 times but was 0. Element instances:\n"
                  + "\t- 'A': completed\n"
                  + "\t- 'A': terminated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfNotCreated() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasActiveElement("A", 2))
          .hasMessage(
              "Process instance [key: %d] should have active element 'A' 2 times but was 0. Element instances:\n"
                  + "<None>",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldHasZeroActiveElements() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(
              Arrays.asList(newCompletedFlowNodeInstance("A"), newTerminatedFlowNodeInstance("B")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent)
          .hasActiveElement("A", 0)
          .hasActiveElement("B", 0)
          .hasActiveElement("C", 0);
    }

    @Test
    void shouldFailIfExpectedZero() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Collections.singletonList(newActiveFlowNodeInstance("A")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasActiveElement("A", 0))
          .hasMessage(
              "Process instance [key: %d] should have active element 'A' 0 times but was 1. Element instances:\n"
                  + "\t- 'A': active",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfNumberIsNegative() {
      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasActiveElement("A", -1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("The amount must be greater than or equal to zero.");
    }

    @Test
    void shouldWaitUntilHasActiveElement() {
      // given
      final FlowNodeInstance flowNodeInstanceA1 = newActiveFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceB = newActiveFlowNodeInstance("B");
      final FlowNodeInstance flowNodeInstanceA2 = newActiveFlowNodeInstance("A");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstanceA1, flowNodeInstanceB))
          .thenReturn(Arrays.asList(flowNodeInstanceA1, flowNodeInstanceB, flowNodeInstanceA2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasActiveElement("A", 2);

      verify(camundaDataSource, times(2)).findFlowNodeInstances(any());
    }
  }

  @Nested
  class HasCompletedElement {

    @Test
    void shouldHasCompletedElement() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newCompletedFlowNodeInstance("A");

      final FlowNodeInstance flowNodeInstanceB1 = newCompletedFlowNodeInstance("B");
      final FlowNodeInstance flowNodeInstanceB2 = newCompletedFlowNodeInstance("B");

      final FlowNodeInstance flowNodeInstanceC1 = newCompletedFlowNodeInstance("C");
      final FlowNodeInstance flowNodeInstanceC2 = newCompletedFlowNodeInstance("C");
      final FlowNodeInstance flowNodeInstanceC3 = newCompletedFlowNodeInstance("C");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(
              Arrays.asList(
                  flowNodeInstanceA,
                  flowNodeInstanceB1,
                  flowNodeInstanceB2,
                  flowNodeInstanceC1,
                  flowNodeInstanceC2,
                  flowNodeInstanceC3));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent)
          .hasCompletedElement("A", 1)
          .hasCompletedElement("B", 2)
          .hasCompletedElement(ElementSelectors.byId("C"), 3);
    }

    @Test
    void shouldFailIfNumberIsGreater() {
      // given
      final FlowNodeInstance flowNodeInstance1 = newCompletedFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstance2 = newCompletedFlowNodeInstance("A");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstance1, flowNodeInstance2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasCompletedElement("A", 1))
          .hasMessage(
              "Process instance [key: %d] should have completed element 'A' 1 times but was 2. Element instances:\n"
                  + "\t- 'A': completed\n"
                  + "\t- 'A': completed",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfNumberIsLess() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newCompletedFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceB = newCompletedFlowNodeInstance("B");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasCompletedElement("A", 2))
          .hasMessage(
              "Process instance [key: %d] should have completed element 'A' 2 times but was 1. Element instances:\n"
                  + "\t- 'A': completed",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfNotCompleted() {
      // given
      final FlowNodeInstance flowNodeInstanceA1 = newTerminatedFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceA2 = newActiveFlowNodeInstance("A");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstanceA1, flowNodeInstanceA2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasCompletedElement("A", 2))
          .hasMessage(
              "Process instance [key: %d] should have completed element 'A' 2 times but was 0. Element instances:\n"
                  + "\t- 'A': terminated\n"
                  + "\t- 'A': active",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfNotCreated() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasCompletedElement("A", 2))
          .hasMessage(
              "Process instance [key: %d] should have completed element 'A' 2 times but was 0. Element instances:\n"
                  + "<None>",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldHasZeroCompletedElements() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(
              Arrays.asList(newActiveFlowNodeInstance("A"), newTerminatedFlowNodeInstance("B")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent)
          .hasCompletedElement("A", 0)
          .hasCompletedElement("B", 0)
          .hasCompletedElement("C", 0);
    }

    @Test
    void shouldFailIfExpectedZero() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Collections.singletonList(newCompletedFlowNodeInstance("A")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasCompletedElement("A", 0))
          .hasMessage(
              "Process instance [key: %d] should have completed element 'A' 0 times but was 1. Element instances:\n"
                  + "\t- 'A': completed",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfNumberIsNegative() {
      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasCompletedElement("A", -1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("The amount must be greater than or equal to zero.");
    }

    @Test
    void shouldWaitUntilHasCompletedElement() {
      // given
      final FlowNodeInstance flowNodeInstanceA1 = newCompletedFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceA2 = newActiveFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceA3 = newCompletedFlowNodeInstance("A");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstanceA1, flowNodeInstanceA2))
          .thenReturn(Arrays.asList(flowNodeInstanceA1, flowNodeInstanceA3));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasCompletedElement("A", 2);

      verify(camundaDataSource, times(2)).findFlowNodeInstances(any());
    }
  }

  @Nested
  class HasTerminatedElement {

    @Test
    void shouldHasTerminatedElement() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newTerminatedFlowNodeInstance("A");

      final FlowNodeInstance flowNodeInstanceB1 = newTerminatedFlowNodeInstance("B");
      final FlowNodeInstance flowNodeInstanceB2 = newTerminatedFlowNodeInstance("B");

      final FlowNodeInstance flowNodeInstanceC1 = newTerminatedFlowNodeInstance("C");
      final FlowNodeInstance flowNodeInstanceC2 = newTerminatedFlowNodeInstance("C");
      final FlowNodeInstance flowNodeInstanceC3 = newTerminatedFlowNodeInstance("C");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(
              Arrays.asList(
                  flowNodeInstanceA,
                  flowNodeInstanceB1,
                  flowNodeInstanceB2,
                  flowNodeInstanceC1,
                  flowNodeInstanceC2,
                  flowNodeInstanceC3));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent)
          .hasTerminatedElement("A", 1)
          .hasTerminatedElement("B", 2)
          .hasTerminatedElement(ElementSelectors.byId("C"), 3);
    }

    @Test
    void shouldFailIfNumberIsGreater() {
      // given
      final FlowNodeInstance flowNodeInstance1 = newTerminatedFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstance2 = newTerminatedFlowNodeInstance("A");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstance1, flowNodeInstance2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasTerminatedElement("A", 1))
          .hasMessage(
              "Process instance [key: %d] should have terminated element 'A' 1 times but was 2. Element instances:\n"
                  + "\t- 'A': terminated\n"
                  + "\t- 'A': terminated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfNumberIsLess() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newTerminatedFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceB = newTerminatedFlowNodeInstance("B");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasTerminatedElement("A", 2))
          .hasMessage(
              "Process instance [key: %d] should have terminated element 'A' 2 times but was 1. Element instances:\n"
                  + "\t- 'A': terminated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfNotTerminated() {
      // given
      final FlowNodeInstance flowNodeInstanceA1 = newCompletedFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceA2 = newActiveFlowNodeInstance("A");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstanceA1, flowNodeInstanceA2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasTerminatedElement("A", 2))
          .hasMessage(
              "Process instance [key: %d] should have terminated element 'A' 2 times but was 0. Element instances:\n"
                  + "\t- 'A': completed\n"
                  + "\t- 'A': active",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfNotCreated() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasTerminatedElement("A", 2))
          .hasMessage(
              "Process instance [key: %d] should have terminated element 'A' 2 times but was 0. Element instances:\n"
                  + "<None>",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldHasZeroCompletedElements() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(
              Arrays.asList(newActiveFlowNodeInstance("A"), newCompletedFlowNodeInstance("B")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent)
          .hasTerminatedElement("A", 0)
          .hasTerminatedElement("B", 0)
          .hasTerminatedElement("C", 0);
    }

    @Test
    void shouldFailIfExpectedZero() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Collections.singletonList(newTerminatedFlowNodeInstance("A")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasTerminatedElement("A", 0))
          .hasMessage(
              "Process instance [key: %d] should have terminated element 'A' 0 times but was 1. Element instances:\n"
                  + "\t- 'A': terminated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfNumberIsNegative() {
      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasTerminatedElement("A", -1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("The amount must be greater than or equal to zero.");
    }

    @Test
    void shouldWaitUntilHasTerminatedElement() {
      // given
      final FlowNodeInstance flowNodeInstanceA1 = newTerminatedFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceA2 = newActiveFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceA3 = newTerminatedFlowNodeInstance("A");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstanceA1, flowNodeInstanceA2))
          .thenReturn(Arrays.asList(flowNodeInstanceA1, flowNodeInstanceA3));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasTerminatedElement("A", 2);

      verify(camundaDataSource, times(2)).findFlowNodeInstances(any());
    }
  }

  @Nested
  class HasNotActivatedElements {

    @Test
    void shouldHasNotActivatedElements() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasNotActivatedElements("A", "B");
    }

    @Test
    void shouldFailIfElementsAreActive() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newActiveFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceB = newActiveFlowNodeInstance("B");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThat(processInstanceEvent)
                      .hasNotActivatedElements("A", "B", "C"))
          .hasMessage(
              "Process instance [key: %d] should have not activated elements ['A', 'B', 'C'] but the following elements were activated:\n"
                  + "\t- 'A': active\n"
                  + "\t- 'B': active",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfElementsAreCompleted() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newCompletedFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceB = newCompletedFlowNodeInstance("B");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThat(processInstanceEvent)
                      .hasNotActivatedElements("A", "B", "C"))
          .hasMessage(
              "Process instance [key: %d] should have not activated elements ['A', 'B', 'C'] but the following elements were activated:\n"
                  + "\t- 'A': completed\n"
                  + "\t- 'B': completed",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfElementsAreTerminated() {
      // given
      final FlowNodeInstance flowNodeInstanceA = newTerminatedFlowNodeInstance("A");
      final FlowNodeInstance flowNodeInstanceB = newTerminatedFlowNodeInstance("B");

      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(flowNodeInstanceA, flowNodeInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThat(processInstanceEvent)
                      .hasNotActivatedElements("A", "B", "C"))
          .hasMessage(
              "Process instance [key: %d] should have not activated elements ['A', 'B', 'C'] but the following elements were activated:\n"
                  + "\t- 'A': terminated\n"
                  + "\t- 'B': terminated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfProcessInstanceNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasNotActivatedElements("A"))
          .hasMessage("No process instance [key: %d] found.", PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class HasNoActiveElements {

    @Mock private FlownodeInstanceFilter flownodeInstanceFilter;
    @Captor private ArgumentCaptor<Consumer<FlownodeInstanceFilter>> flowNodeInstanceFilterCapture;

    @Test
    void shouldQueryOnlyActiveElements() {
      // given
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when
      CamundaAssert.assertThat(processInstanceEvent).hasNoActiveElements("A");

      // then
      verify(camundaDataSource).findFlowNodeInstances(flowNodeInstanceFilterCapture.capture());

      flowNodeInstanceFilterCapture.getValue().accept(flownodeInstanceFilter);
      verify(flownodeInstanceFilter).processInstanceKey(PROCESS_INSTANCE_KEY);
      verify(flownodeInstanceFilter).state(FlowNodeInstanceFilter.State.ACTIVE);
      verify(flownodeInstanceFilter).flowNodeId("A");
      verifyNoMoreInteractions(flownodeInstanceFilter);
    }

    @Test
    void shouldPassIfElementsAreNotActive() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasNoActiveElements("A", "B");
    }

    @Test
    void shouldFailIfElementsAreActive() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(
              Arrays.asList(newActiveFlowNodeInstance("A"), newActiveFlowNodeInstance("B")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThat(processInstanceEvent).hasNoActiveElements("A", "B", "C"))
          .hasMessage(
              "Process instance [key: %d] should have no active elements ['A', 'B', 'C'] but the following elements were active:\n"
                  + "\t- 'A': active\n"
                  + "\t- 'B': active",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfProcessInstanceNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasNoActiveElements("A"))
          .hasMessage("No process instance [key: %d] found.", PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldWaitUntilElementsAreEnded() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Collections.singletonList(newActiveFlowNodeInstance("A")))
          .thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasNoActiveElements("A");

      verify(camundaDataSource, times(2)).findFlowNodeInstances(any());
    }
  }

  @Nested
  class HasActiveElementsExactly {

    @Test
    void shouldPassIfElementsAreActive() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(
              Arrays.asList(newActiveFlowNodeInstance("A"), newActiveFlowNodeInstance("B")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasActiveElementsExactly("A", "B");
    }

    @Test
    void shouldPassIfOtherElementsAreEnded() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(
              Arrays.asList(
                  newActiveFlowNodeInstance("A"),
                  newCompletedFlowNodeInstance("B"),
                  newTerminatedFlowNodeInstance("C")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasActiveElementsExactly("A");
    }

    @Test
    void shouldPassIfElementsAreActiveMultipleTimes() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(
              Arrays.asList(
                  newActiveFlowNodeInstance("A"),
                  newActiveFlowNodeInstance("B"),
                  newActiveFlowNodeInstance("B")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasActiveElementsExactly("A", "B");

      CamundaAssert.assertThat(processInstanceEvent).hasActiveElementsExactly("A", "B", "B");

      CamundaAssert.assertThat(processInstanceEvent).hasActiveElementsExactly("A", "A", "B");
    }

    @Test
    void shouldFailIfElementsAreNotActive() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(
              Arrays.asList(
                  newActiveFlowNodeInstance("A"),
                  newCompletedFlowNodeInstance("B"),
                  newTerminatedFlowNodeInstance("C")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThat(processInstanceEvent)
                      .hasActiveElementsExactly("A", "B", "C", "D"))
          .hasMessage(
              "Process instance [key: %d] should have active elements ['A', 'B', 'C', 'D'] but the following elements were not active:\n"
                  + "\t- 'B': completed\n"
                  + "\t- 'C': terminated\n"
                  + "\t- 'D': not activated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfOtherElementsAreActive() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(
              Arrays.asList(
                  newActiveFlowNodeInstance("A"),
                  newActiveFlowNodeInstance("B"),
                  newActiveFlowNodeInstance("C")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasActiveElementsExactly("A"))
          .hasMessage(
              "Process instance [key: %d] should have no active elements except ['A'] but the following elements were active:\n"
                  + "\t- 'B': active\n"
                  + "\t- 'C': active",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfElementsAreNotActiveAndOtherElementsAreActive() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(
              Arrays.asList(
                  newActiveFlowNodeInstance("A"),
                  newCompletedFlowNodeInstance("B"),
                  newActiveFlowNodeInstance("C")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThat(processInstanceEvent).hasActiveElementsExactly("A", "B"))
          .hasMessage(
              "Process instance [key: %d] should have active elements ['A', 'B'] but the following elements were not active:\n"
                  + "\t- 'B': completed\n"
                  + "\n"
                  + "Process instance [key: %d] should have no active elements except ['A', 'B'] but the following elements were active:\n"
                  + "\t- 'C': active",
              PROCESS_INSTANCE_KEY, PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldWaitUntilElementsAreActive() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.singletonList(newActiveFlowNodeInstance("A")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasActiveElementsExactly("A");

      verify(camundaDataSource, times(2)).findFlowNodeInstances(any());
    }

    @Test
    void shouldWaitUntilOtherElementsAreEnded() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Arrays.asList(newActiveFlowNodeInstance("A"), newActiveFlowNodeInstance("B")))
          .thenReturn(
              Arrays.asList(newActiveFlowNodeInstance("A"), newCompletedFlowNodeInstance("B")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasActiveElementsExactly("A");

      verify(camundaDataSource, times(2)).findFlowNodeInstances(any());
    }
  }
}
