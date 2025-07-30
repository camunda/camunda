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
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.process.test.api.assertions.ElementSelectors;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.utils.CamundaAssertExpectFailure;
import io.camunda.process.test.utils.CamundaAssertExtension;
import io.camunda.process.test.utils.ElementInstanceBuilder;
import io.camunda.process.test.utils.ProcessInstanceBuilder;
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

@ExtendWith({CamundaAssertExtension.class, MockitoExtension.class})
public class ElementAssertTest {

  private static final long PROCESS_INSTANCE_KEY = 1L;

  @Mock private CamundaDataSource camundaDataSource;
  @Mock private ProcessInstanceEvent processInstanceEvent;

  @BeforeEach
  void configureAssertions() {
    CamundaAssert.initialize(camundaDataSource);
  }

  @BeforeEach
  void configureMocks() {
    when(camundaDataSource.findProcessInstances(any()))
        .thenReturn(
            Collections.singletonList(
                ProcessInstanceBuilder.newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));
  }

  private static ElementInstance newActiveElementInstance(final String elementId) {
    return ElementInstanceBuilder.newActiveElementInstance(elementId, PROCESS_INSTANCE_KEY).build();
  }

  private static ElementInstance newCompletedElementInstance(final String elementId) {
    return ElementInstanceBuilder.newCompletedElementInstance(elementId, PROCESS_INSTANCE_KEY)
        .build();
  }

  private static ElementInstance newTerminatedElementInstance(final String elementId) {
    return ElementInstanceBuilder.newTerminatedElementInstance(elementId, PROCESS_INSTANCE_KEY)
        .build();
  }

  @Nested
  class ElementSource {

    @Mock private ElementInstanceFilter elementInstanceFilter;
    @Captor private ArgumentCaptor<Consumer<ElementInstanceFilter>> elementInstanceFilterCapture;

    @BeforeEach
    void configureMocks() {
      final ElementInstance elementInstanceA = newActiveElementInstance("A");
      final ElementInstance elementInstanceB = newActiveElementInstance("B");
      final ElementInstance elementInstanceC = newCompletedElementInstance("C");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA, elementInstanceB, elementInstanceC));
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
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasActiveElements("A");

      verify(camundaDataSource).findElementInstances(elementInstanceFilterCapture.capture());

      elementInstanceFilterCapture.getValue().accept(elementInstanceFilter);
      verify(elementInstanceFilter).processInstanceKey(PROCESS_INSTANCE_KEY);
      verify(elementInstanceFilter).elementId("A");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWithStringSelector() {
      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasActiveElements("A", "C", "D"))
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
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasActiveElements("element_A", "element_B");
    }

    @Test
    void shouldUseByIdSelector() {
      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasActiveElements(ElementSelectors.byId("A"));

      verify(camundaDataSource).findElementInstances(elementInstanceFilterCapture.capture());

      elementInstanceFilterCapture.getValue().accept(elementInstanceFilter);
      verify(elementInstanceFilter).processInstanceKey(PROCESS_INSTANCE_KEY);
      verify(elementInstanceFilter).elementId("A");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWithByIdSelector() {
      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
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
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasActiveElements(ElementSelectors.byName("element_A"));

      verify(camundaDataSource).findElementInstances(elementInstanceFilterCapture.capture());

      elementInstanceFilterCapture.getValue().accept(elementInstanceFilter);
      verify(elementInstanceFilter).processInstanceKey(PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWithByNameSelector() {
      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasActiveElements(
              ElementSelectors.byName("element_A"), ElementSelectors.byName("element_B"));

      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
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
      final ElementInstance elementInstanceA = newActiveElementInstance("A");
      final ElementInstance elementInstanceB = newActiveElementInstance("B");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA, elementInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasActiveElements("A", "B");
    }

    @Test
    void shouldHasTwoActiveElements() {
      // given
      final ElementInstance elementInstanceActive = newActiveElementInstance("A");
      final ElementInstance elementInstanceCompleted = newCompletedElementInstance("A");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceCompleted, elementInstanceActive));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasActiveElements("A", "A");
    }

    @Test
    void shouldWaitUntilHasActiveElements() {
      // given
      final ElementInstance elementInstanceA = newActiveElementInstance("A");
      final ElementInstance elementInstanceB = newActiveElementInstance("B");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Collections.singletonList(elementInstanceA))
          .thenReturn(Arrays.asList(elementInstanceA, elementInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasActiveElements("A", "B");

      verify(camundaDataSource, times(2)).findElementInstances(any());
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfElementsNotFound() {
      // given
      final ElementInstance elementInstanceA = newActiveElementInstance("A");
      final ElementInstance elementInstanceB = newActiveElementInstance("B");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA, elementInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasActiveElements("A", "C", "D"))
          .hasMessage(
              "Process instance [key: %d] should have active elements ['A', 'C', 'D'] but the following elements were not active:\n"
                  + "\t- 'C': not activated\n"
                  + "\t- 'D': not activated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfElementsNotActive() {
      // given
      final ElementInstance elementInstanceA = newActiveElementInstance("A");
      final ElementInstance elementInstanceB = newCompletedElementInstance("B");
      final ElementInstance elementInstanceC = newTerminatedElementInstance("C");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA, elementInstanceB, elementInstanceC));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasActiveElements("A", "B", "C"))
          .hasMessage(
              "Process instance [key: %d] should have active elements ['A', 'B', 'C'] but the following elements were not active:\n"
                  + "\t- 'B': completed\n"
                  + "\t- 'C': terminated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWithSameElementId() {
      // given
      final ElementInstance elementInstanceA = newActiveElementInstance("A");
      final ElementInstance elementInstanceA2 = newActiveElementInstance("A");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA, elementInstanceA2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasActiveElements("B"))
          .hasMessage(
              "Process instance [key: %d] should have active elements ['B'] but the following elements were not active:\n"
                  + "\t- 'B': not activated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfProcessInstanceNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasActiveElements("A"))
          .hasMessage("No process instance [key: %d] found.", PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class HasCompletedElements {

    @Test
    void shouldHasCompletedElements() {
      // given
      final ElementInstance elementInstanceA = newCompletedElementInstance("A");
      final ElementInstance elementInstanceB = newCompletedElementInstance("B");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA, elementInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasCompletedElements("A", "B");
    }

    @Test
    void shouldHasTwoCompletedElements() {
      // given
      final ElementInstance elementInstanceActive = newCompletedElementInstance("A");
      final ElementInstance elementInstanceCompleted = newActiveElementInstance("A");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceCompleted, elementInstanceActive));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasCompletedElements("A", "A");
    }

    @Test
    void shouldWaitUntilHasCompletedElements() {
      // given
      final ElementInstance elementInstanceA = newCompletedElementInstance("A");
      final ElementInstance activeElementInstanceB = newActiveElementInstance("B");
      final ElementInstance completedElementInstanceB = newCompletedElementInstance("B");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA, activeElementInstanceB))
          .thenReturn(Arrays.asList(elementInstanceA, completedElementInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasCompletedElements("A", "B");

      verify(camundaDataSource, times(2)).findElementInstances(any());
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfElementsNotFound() {
      // given
      final ElementInstance elementInstanceA = newCompletedElementInstance("A");
      final ElementInstance elementInstanceB = newCompletedElementInstance("B");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA, elementInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasCompletedElements("A", "C", "D"))
          .hasMessage(
              "Process instance [key: %d] should have completed elements ['A', 'C', 'D'] but the following elements were not completed:\n"
                  + "\t- 'C': not activated\n"
                  + "\t- 'D': not activated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfElementsNotCompleted() {
      // given
      final ElementInstance elementInstanceA = newCompletedElementInstance("A");
      final ElementInstance elementInstanceB = newTerminatedElementInstance("B");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA, elementInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasCompletedElements("A", "B"))
          .hasMessage(
              "Process instance [key: %d] should have completed elements ['A', 'B'] but the following elements were not completed:\n"
                  + "\t- 'B': terminated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWithSameElementId() {
      // given
      final ElementInstance elementInstanceA = newCompletedElementInstance("A");
      final ElementInstance elementInstanceA2 = newCompletedElementInstance("A");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA, elementInstanceA2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasCompletedElements("B"))
          .hasMessage(
              "Process instance [key: %d] should have completed elements ['B'] but the following elements were not completed:\n"
                  + "\t- 'B': not activated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfProcessInstanceNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasCompletedElements("A"))
          .hasMessage("No process instance [key: %d] found.", PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class HasTerminatedElements {

    @Test
    void shouldHasTerminatedElements() {
      // given
      final ElementInstance elementInstanceA = newTerminatedElementInstance("A");
      final ElementInstance elementInstanceB = newTerminatedElementInstance("B");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA, elementInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasTerminatedElements("A", "B");
    }

    @Test
    void shouldHasTwoTerminatedElements() {
      // given
      final ElementInstance elementInstanceActive = newTerminatedElementInstance("A");
      final ElementInstance elementInstanceCompleted = newActiveElementInstance("A");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceCompleted, elementInstanceActive));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasTerminatedElements("A", "A");
    }

    @Test
    void shouldWaitUntilHasTerminatedElements() {
      // given
      final ElementInstance elementInstanceA = newTerminatedElementInstance("A");
      final ElementInstance activeElementInstanceB = newActiveElementInstance("B");
      final ElementInstance terminatedElementInstanceB = newTerminatedElementInstance("B");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA, activeElementInstanceB))
          .thenReturn(Arrays.asList(elementInstanceA, terminatedElementInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasTerminatedElements("A", "B");

      verify(camundaDataSource, times(2)).findElementInstances(any());
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfElementsNotFound() {
      // given
      final ElementInstance elementInstanceA = newTerminatedElementInstance("A");
      final ElementInstance elementInstanceB = newTerminatedElementInstance("B");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA, elementInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasTerminatedElements("A", "C", "D"))
          .hasMessage(
              "Process instance [key: %d] should have terminated elements ['A', 'C', 'D'] but the following elements were not terminated:\n"
                  + "\t- 'C': not activated\n"
                  + "\t- 'D': not activated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfElementsNotTerminated() {
      // given
      final ElementInstance elementInstanceA = newCompletedElementInstance("A");
      final ElementInstance elementInstanceB = newTerminatedElementInstance("B");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA, elementInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasTerminatedElements("A", "B"))
          .hasMessage(
              "Process instance [key: %d] should have terminated elements ['A', 'B'] but the following elements were not terminated:\n"
                  + "\t- 'A': completed",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWithSameElementId() {
      // given
      final ElementInstance elementInstanceA = newCompletedElementInstance("A");
      final ElementInstance elementInstanceA2 = newCompletedElementInstance("A");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA, elementInstanceA2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasTerminatedElements("B"))
          .hasMessage(
              "Process instance [key: %d] should have terminated elements ['B'] but the following elements were not terminated:\n"
                  + "\t- 'B': not activated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfProcessInstanceNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasTerminatedElements("A"))
          .hasMessage("No process instance [key: %d] found.", PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class HasActiveElement {

    @Test
    void shouldHasActiveElement() {
      // given
      final ElementInstance elementInstanceA = newActiveElementInstance("A");

      final ElementInstance elementInstanceB1 = newActiveElementInstance("B");
      final ElementInstance elementInstanceB2 = newActiveElementInstance("B");

      final ElementInstance elementInstanceC1 = newActiveElementInstance("C");
      final ElementInstance elementInstanceC2 = newActiveElementInstance("C");
      final ElementInstance elementInstanceC3 = newActiveElementInstance("C");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Arrays.asList(
                  elementInstanceA,
                  elementInstanceB1,
                  elementInstanceB2,
                  elementInstanceC1,
                  elementInstanceC2,
                  elementInstanceC3));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasActiveElement("A", 1)
          .hasActiveElement("B", 2)
          .hasActiveElement(ElementSelectors.byId("C"), 3);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfNumberIsGreater() {
      // given
      final ElementInstance elementInstance1 = newActiveElementInstance("A");
      final ElementInstance elementInstance2 = newActiveElementInstance("A");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstance1, elementInstance2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasActiveElement("A", 1))
          .hasMessage(
              "Process instance [key: %d] should have active element 'A' 1 times but was 2. Element instances:\n"
                  + "\t- 'A': active\n"
                  + "\t- 'A': active",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfNumberIsLess() {
      // given
      final ElementInstance elementInstanceA = newActiveElementInstance("A");
      final ElementInstance elementInstanceB = newActiveElementInstance("B");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA, elementInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasActiveElement("A", 2))
          .hasMessage(
              "Process instance [key: %d] should have active element 'A' 2 times but was 1. Element instances:\n"
                  + "\t- 'A': active",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfNotActive() {
      // given
      final ElementInstance elementInstanceA1 = newCompletedElementInstance("A");
      final ElementInstance elementInstanceA2 = newTerminatedElementInstance("A");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA1, elementInstanceA2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasActiveElement("A", 2))
          .hasMessage(
              "Process instance [key: %d] should have active element 'A' 2 times but was 0. Element instances:\n"
                  + "\t- 'A': completed\n"
                  + "\t- 'A': terminated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfNotCreated() {
      // given
      when(camundaDataSource.findElementInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasActiveElement("A", 2))
          .hasMessage(
              "Process instance [key: %d] should have active element 'A' 2 times but was 0. Element instances:\n"
                  + "<None>",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldHasZeroActiveElements() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Arrays.asList(newCompletedElementInstance("A"), newTerminatedElementInstance("B")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasActiveElement("A", 0)
          .hasActiveElement("B", 0)
          .hasActiveElement("C", 0);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfExpectedZero() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Collections.singletonList(newActiveElementInstance("A")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasActiveElement("A", 0))
          .hasMessage(
              "Process instance [key: %d] should have active element 'A' 0 times but was 1. Element instances:\n"
                  + "\t- 'A': active",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfNumberIsNegative() {
      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasActiveElement("A", -1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("The amount must be greater than or equal to zero.");
    }

    @Test
    void shouldWaitUntilHasActiveElement() {
      // given
      final ElementInstance elementInstanceA1 = newActiveElementInstance("A");
      final ElementInstance elementInstanceB = newActiveElementInstance("B");
      final ElementInstance elementInstanceA2 = newActiveElementInstance("A");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA1, elementInstanceB))
          .thenReturn(Arrays.asList(elementInstanceA1, elementInstanceB, elementInstanceA2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasActiveElement("A", 2);

      verify(camundaDataSource, times(2)).findElementInstances(any());
    }
  }

  @Nested
  class HasCompletedElement {

    @Test
    void shouldHasCompletedElement() {
      // given
      final ElementInstance elementInstanceA = newCompletedElementInstance("A");

      final ElementInstance elementInstanceB1 = newCompletedElementInstance("B");
      final ElementInstance elementInstanceB2 = newCompletedElementInstance("B");

      final ElementInstance elementInstanceC1 = newCompletedElementInstance("C");
      final ElementInstance elementInstanceC2 = newCompletedElementInstance("C");
      final ElementInstance elementInstanceC3 = newCompletedElementInstance("C");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Arrays.asList(
                  elementInstanceA,
                  elementInstanceB1,
                  elementInstanceB2,
                  elementInstanceC1,
                  elementInstanceC2,
                  elementInstanceC3));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasCompletedElement("A", 1)
          .hasCompletedElement("B", 2)
          .hasCompletedElement(ElementSelectors.byId("C"), 3);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfNumberIsGreater() {
      // given
      final ElementInstance elementInstance1 = newCompletedElementInstance("A");
      final ElementInstance elementInstance2 = newCompletedElementInstance("A");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstance1, elementInstance2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasCompletedElement("A", 1))
          .hasMessage(
              "Process instance [key: %d] should have completed element 'A' 1 times but was 2. Element instances:\n"
                  + "\t- 'A': completed\n"
                  + "\t- 'A': completed",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfNumberIsLess() {
      // given
      final ElementInstance elementInstanceA = newCompletedElementInstance("A");
      final ElementInstance elementInstanceB = newCompletedElementInstance("B");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA, elementInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasCompletedElement("A", 2))
          .hasMessage(
              "Process instance [key: %d] should have completed element 'A' 2 times but was 1. Element instances:\n"
                  + "\t- 'A': completed",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfNotCompleted() {
      // given
      final ElementInstance elementInstanceA1 = newTerminatedElementInstance("A");
      final ElementInstance elementInstanceA2 = newActiveElementInstance("A");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA1, elementInstanceA2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasCompletedElement("A", 2))
          .hasMessage(
              "Process instance [key: %d] should have completed element 'A' 2 times but was 0. Element instances:\n"
                  + "\t- 'A': terminated\n"
                  + "\t- 'A': active",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfNotCreated() {
      // given
      when(camundaDataSource.findElementInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasCompletedElement("A", 2))
          .hasMessage(
              "Process instance [key: %d] should have completed element 'A' 2 times but was 0. Element instances:\n"
                  + "<None>",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldHasZeroCompletedElements() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Arrays.asList(newActiveElementInstance("A"), newTerminatedElementInstance("B")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasCompletedElement("A", 0)
          .hasCompletedElement("B", 0)
          .hasCompletedElement("C", 0);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfExpectedZero() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Collections.singletonList(newCompletedElementInstance("A")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasCompletedElement("A", 0))
          .hasMessage(
              "Process instance [key: %d] should have completed element 'A' 0 times but was 1. Element instances:\n"
                  + "\t- 'A': completed",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfNumberIsNegative() {
      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasCompletedElement("A", -1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("The amount must be greater than or equal to zero.");
    }

    @Test
    void shouldWaitUntilHasCompletedElement() {
      // given
      final ElementInstance elementInstanceA1 = newCompletedElementInstance("A");
      final ElementInstance elementInstanceA2 = newActiveElementInstance("A");
      final ElementInstance elementInstanceA3 = newCompletedElementInstance("A");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA1, elementInstanceA2))
          .thenReturn(Arrays.asList(elementInstanceA1, elementInstanceA3));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasCompletedElement("A", 2);

      verify(camundaDataSource, times(2)).findElementInstances(any());
    }
  }

  @Nested
  class HasTerminatedElement {

    @Test
    void shouldHasTerminatedElement() {
      // given
      final ElementInstance elementInstanceA = newTerminatedElementInstance("A");

      final ElementInstance elementInstanceB1 = newTerminatedElementInstance("B");
      final ElementInstance elementInstanceB2 = newTerminatedElementInstance("B");

      final ElementInstance elementInstanceC1 = newTerminatedElementInstance("C");
      final ElementInstance elementInstanceC2 = newTerminatedElementInstance("C");
      final ElementInstance elementInstanceC3 = newTerminatedElementInstance("C");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Arrays.asList(
                  elementInstanceA,
                  elementInstanceB1,
                  elementInstanceB2,
                  elementInstanceC1,
                  elementInstanceC2,
                  elementInstanceC3));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasTerminatedElement("A", 1)
          .hasTerminatedElement("B", 2)
          .hasTerminatedElement(ElementSelectors.byId("C"), 3);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfNumberIsGreater() {
      // given
      final ElementInstance elementInstance1 = newTerminatedElementInstance("A");
      final ElementInstance elementInstance2 = newTerminatedElementInstance("A");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstance1, elementInstance2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasTerminatedElement("A", 1))
          .hasMessage(
              "Process instance [key: %d] should have terminated element 'A' 1 times but was 2. Element instances:\n"
                  + "\t- 'A': terminated\n"
                  + "\t- 'A': terminated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfNumberIsLess() {
      // given
      final ElementInstance elementInstanceA = newTerminatedElementInstance("A");
      final ElementInstance elementInstanceB = newTerminatedElementInstance("B");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA, elementInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasTerminatedElement("A", 2))
          .hasMessage(
              "Process instance [key: %d] should have terminated element 'A' 2 times but was 1. Element instances:\n"
                  + "\t- 'A': terminated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfNotTerminated() {
      // given
      final ElementInstance elementInstanceA1 = newCompletedElementInstance("A");
      final ElementInstance elementInstanceA2 = newActiveElementInstance("A");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA1, elementInstanceA2));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasTerminatedElement("A", 2))
          .hasMessage(
              "Process instance [key: %d] should have terminated element 'A' 2 times but was 0. Element instances:\n"
                  + "\t- 'A': completed\n"
                  + "\t- 'A': active",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfNotCreated() {
      // given
      when(camundaDataSource.findElementInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasTerminatedElement("A", 2))
          .hasMessage(
              "Process instance [key: %d] should have terminated element 'A' 2 times but was 0. Element instances:\n"
                  + "<None>",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldHasZeroCompletedElements() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Arrays.asList(newActiveElementInstance("A"), newCompletedElementInstance("B")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasTerminatedElement("A", 0)
          .hasTerminatedElement("B", 0)
          .hasTerminatedElement("C", 0);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfExpectedZero() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Collections.singletonList(newTerminatedElementInstance("A")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasTerminatedElement("A", 0))
          .hasMessage(
              "Process instance [key: %d] should have terminated element 'A' 0 times but was 1. Element instances:\n"
                  + "\t- 'A': terminated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfNumberIsNegative() {
      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasTerminatedElement("A", -1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("The amount must be greater than or equal to zero.");
    }

    @Test
    void shouldWaitUntilHasTerminatedElement() {
      // given
      final ElementInstance elementInstanceA1 = newTerminatedElementInstance("A");
      final ElementInstance elementInstanceA2 = newActiveElementInstance("A");
      final ElementInstance elementInstanceA3 = newTerminatedElementInstance("A");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA1, elementInstanceA2))
          .thenReturn(Arrays.asList(elementInstanceA1, elementInstanceA3));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasTerminatedElement("A", 2);

      verify(camundaDataSource, times(2)).findElementInstances(any());
    }
  }

  @Nested
  class HasNotActivatedElements {

    @Test
    void shouldHasNotActivatedElements() {
      // given
      when(camundaDataSource.findElementInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasNotActivatedElements("A", "B");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfElementsAreActive() {
      // given
      final ElementInstance elementInstanceA = newActiveElementInstance("A");
      final ElementInstance elementInstanceB = newActiveElementInstance("B");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA, elementInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasNotActivatedElements("A", "B", "C"))
          .hasMessage(
              "Process instance [key: %d] should have not activated elements ['A', 'B', 'C'] but the following elements were activated:\n"
                  + "\t- 'A': active\n"
                  + "\t- 'B': active",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfElementsAreCompleted() {
      // given
      final ElementInstance elementInstanceA = newCompletedElementInstance("A");
      final ElementInstance elementInstanceB = newCompletedElementInstance("B");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA, elementInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasNotActivatedElements("A", "B", "C"))
          .hasMessage(
              "Process instance [key: %d] should have not activated elements ['A', 'B', 'C'] but the following elements were activated:\n"
                  + "\t- 'A': completed\n"
                  + "\t- 'B': completed",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfElementsAreTerminated() {
      // given
      final ElementInstance elementInstanceA = newTerminatedElementInstance("A");
      final ElementInstance elementInstanceB = newTerminatedElementInstance("B");

      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(elementInstanceA, elementInstanceB));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasNotActivatedElements("A", "B", "C"))
          .hasMessage(
              "Process instance [key: %d] should have not activated elements ['A', 'B', 'C'] but the following elements were activated:\n"
                  + "\t- 'A': terminated\n"
                  + "\t- 'B': terminated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfProcessInstanceNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasNotActivatedElements("A"))
          .hasMessage("No process instance [key: %d] found.", PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class HasNoActiveElements {

    @Mock private ElementInstanceFilter elementInstanceFilter;
    @Captor private ArgumentCaptor<Consumer<ElementInstanceFilter>> elementInstanceFilterCapture;

    @Test
    void shouldQueryOnlyActiveElements() {
      // given
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasNoActiveElements("A");

      // then
      verify(camundaDataSource).findElementInstances(elementInstanceFilterCapture.capture());

      elementInstanceFilterCapture.getValue().accept(elementInstanceFilter);
      verify(elementInstanceFilter).processInstanceKey(PROCESS_INSTANCE_KEY);
      verify(elementInstanceFilter).state(ElementInstanceState.ACTIVE);
      verify(elementInstanceFilter).elementId("A");
      verifyNoMoreInteractions(elementInstanceFilter);
    }

    @Test
    void shouldPassIfElementsAreNotActive() {
      // given
      when(camundaDataSource.findElementInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasNoActiveElements("A", "B");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfElementsAreActive() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(newActiveElementInstance("A"), newActiveElementInstance("B")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasNoActiveElements("A", "B", "C"))
          .hasMessage(
              "Process instance [key: %d] should have no active elements ['A', 'B', 'C'] but the following elements were active:\n"
                  + "\t- 'A': active\n"
                  + "\t- 'B': active",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfProcessInstanceNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasNoActiveElements("A"))
          .hasMessage("No process instance [key: %d] found.", PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldWaitUntilElementsAreEnded() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Collections.singletonList(newActiveElementInstance("A")))
          .thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasNoActiveElements("A");

      verify(camundaDataSource, times(2)).findElementInstances(any());
    }
  }

  @Nested
  class HasCompletedElementsInOrder {

    @Test
    void shouldPassIfElementsAreCompletedInOrder() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Arrays.asList(
                  newCompletedElementInstance("A"),
                  newCompletedElementInstance("B"),
                  newCompletedElementInstance("C")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasCompletedElementsInOrder("A", "B", "C");
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasCompletedElementsInOrder("A", "B");
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasCompletedElementsInOrder("A", "C");
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasCompletedElementsInOrder("B", "C");
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasCompletedElementsInOrder("A");
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasCompletedElementsInOrder("B");
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasCompletedElementsInOrder("C");
    }

    @Test
    void shouldPassIfElementsAreCompletedInOrderForTheSameInstance() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Arrays.asList(
                  newCompletedElementInstance("A"),
                  newCompletedElementInstance("A"),
                  newCompletedElementInstance("A")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasCompletedElementsInOrder("A", "A", "A");
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasCompletedElementsInOrder("A", "A");
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasCompletedElementsInOrder("A");
    }

    @Test
    void shouldPassIfElementsAreCompletedInOrderMultipleTimes() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Arrays.asList(
                  newCompletedElementInstance("A"),
                  newCompletedElementInstance("A"),
                  newCompletedElementInstance("B"),
                  newCompletedElementInstance("A")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasCompletedElementsInOrder("A", "B", "A");
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasCompletedElementsInOrder("B", "A");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfOneElementIsNotCompleted() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Arrays.asList(
                  newCompletedElementInstance("A"),
                  newCompletedElementInstance("B"),
                  newActiveElementInstance("A")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasCompletedElementsInOrder("A", "B", "A"))
          .hasMessage(
              "Process instance [key: %d] should have completed elements ['A', 'B', 'A'] in order, but only the following elements were completed:\n"
                  + "\t- 'A': completed\n"
                  + "\t- 'B': completed",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfElementsAreCompletedInADifferentOrder() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Arrays.asList(
                  newCompletedElementInstance("A"),
                  newCompletedElementInstance("B"),
                  newCompletedElementInstance("A")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasCompletedElementsInOrder("B", "A", "A"))
          .hasMessage(
              "Process instance [key: %d] should have completed elements ['B', 'A', 'A'] in order, but only the following elements were completed:\n"
                  + "\t- 'A': completed\n"
                  + "\t- 'B': completed\n"
                  + "\t- 'A': completed",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldWaitUntilAllElementsAreCompletedInOrder() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Arrays.asList(
                  newCompletedElementInstance("A"),
                  newCompletedElementInstance("B"),
                  newActiveElementInstance("A")))
          .thenReturn(
              Arrays.asList(
                  newCompletedElementInstance("A"),
                  newCompletedElementInstance("B"),
                  newCompletedElementInstance("A")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasCompletedElementsInOrder("A", "B", "A");
      verify(camundaDataSource, times(2)).findElementInstances(any());
    }
  }

  @Nested
  class HasActiveElementsExactly {

    @Test
    void shouldPassIfElementsAreActive() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(newActiveElementInstance("A"), newActiveElementInstance("B")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasActiveElementsExactly("A", "B");
    }

    @Test
    void shouldPassIfOtherElementsAreEnded() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Arrays.asList(
                  newActiveElementInstance("A"),
                  newCompletedElementInstance("B"),
                  newTerminatedElementInstance("C")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasActiveElementsExactly("A");
    }

    @Test
    void shouldPassIfElementsAreActiveMultipleTimes() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Arrays.asList(
                  newActiveElementInstance("A"),
                  newActiveElementInstance("B"),
                  newActiveElementInstance("B")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasActiveElementsExactly("A", "B");

      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasActiveElementsExactly("A", "B", "B");

      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasActiveElementsExactly("A", "A", "B");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfElementsAreNotActive() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Arrays.asList(
                  newActiveElementInstance("A"),
                  newCompletedElementInstance("B"),
                  newTerminatedElementInstance("C")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasActiveElementsExactly("A", "B", "C", "D"))
          .hasMessage(
              "Process instance [key: %d] should have active elements ['A', 'B', 'C', 'D'] but the following elements were not active:\n"
                  + "\t- 'B': completed\n"
                  + "\t- 'C': terminated\n"
                  + "\t- 'D': not activated",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfOtherElementsAreActive() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Arrays.asList(
                  newActiveElementInstance("A"),
                  newActiveElementInstance("B"),
                  newActiveElementInstance("C")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasActiveElementsExactly("A"))
          .hasMessage(
              "Process instance [key: %d] should have no active elements except ['A'] but the following elements were active:\n"
                  + "\t- 'B': active\n"
                  + "\t- 'C': active",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfElementsAreNotActiveAndOtherElementsAreActive() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(
              Arrays.asList(
                  newActiveElementInstance("A"),
                  newCompletedElementInstance("B"),
                  newActiveElementInstance("C")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasActiveElementsExactly("A", "B"))
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
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.singletonList(newActiveElementInstance("A")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasActiveElementsExactly("A");

      verify(camundaDataSource, times(2)).findElementInstances(any());
    }

    @Test
    void shouldWaitUntilOtherElementsAreEnded() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Arrays.asList(newActiveElementInstance("A"), newActiveElementInstance("B")))
          .thenReturn(
              Arrays.asList(newActiveElementInstance("A"), newCompletedElementInstance("B")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasActiveElementsExactly("A");

      verify(camundaDataSource, times(2)).findElementInstances(any());
    }
  }
}
