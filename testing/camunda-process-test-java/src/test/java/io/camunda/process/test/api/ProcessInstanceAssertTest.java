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

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byParentProcesInstanceKey;
import static io.camunda.process.test.utils.ProcessInstanceBuilder.newActiveChildProcessInstance;
import static io.camunda.process.test.utils.ProcessInstanceBuilder.newActiveProcessInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.response.ProcessInstanceResult;
import io.camunda.client.api.search.filter.CorrelatedMessageFilter;
import io.camunda.client.api.search.filter.MessageSubscriptionFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.Variable;
import io.camunda.client.impl.search.response.CorrelatedMessageImpl;
import io.camunda.client.impl.search.response.MessageSubscriptionImpl;
import io.camunda.client.protocol.rest.CorrelatedMessageResult;
import io.camunda.client.protocol.rest.MessageSubscriptionResult;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.utils.CamundaAssertExpectFailure;
import io.camunda.process.test.utils.CamundaAssertExtension;
import io.camunda.process.test.utils.ElementInstanceBuilder;
import io.camunda.process.test.utils.ProcessInstanceBuilder;
import io.camunda.process.test.utils.VariableBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({CamundaAssertExtension.class, MockitoExtension.class})
public class ProcessInstanceAssertTest {

  private static final long PROCESS_INSTANCE_KEY = 1L;
  private static final String BPMN_PROCESS_ID = "process";

  @Mock private CamundaDataSource camundaDataSource;
  @Mock private ProcessInstanceEvent processInstanceEvent;

  @BeforeEach
  void configureAssertions() {
    CamundaAssert.initialize(camundaDataSource);
  }

  @Nested
  public class CombineSelectors {
    @Test
    public void canCombineSelectors() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      final ProcessInstanceSelector combined =
          ProcessInstanceSelectors.byProcessId(BPMN_PROCESS_ID)
              .and(ProcessInstanceSelectors.byKey(processInstanceEvent.getProcessInstanceKey()));

      assertThatProcessInstance(combined).isActive();
    }

    @Test
    @CamundaAssertExpectFailure
    public void combinedSelectorsRequireAllTestsToPass() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      // then
      final ProcessInstanceSelector badCombination =
          ProcessInstanceSelectors.byProcessId(BPMN_PROCESS_ID)
              .and(ProcessInstanceSelectors.byKey(-1000));

      // then
      Assertions.assertThatThrownBy(() -> assertThatProcessInstance(badCombination).isActive())
          .hasMessage(
              "Process instance [process-id: 'process', key: -1000] should be active but was not created.");
    }
  }

  @Nested
  class ProcessInstanceSource {

    private static final long ACTIVE_PROCESS_INSTANCE_KEY = 1L;
    private static final long COMPLETED_PROCESS_INSTANCE_KEY = 2L;

    @Mock private ProcessInstanceResult processInstanceResult;
    @Mock private ProcessInstanceFilter processInstanceFilter;
    @Captor private ArgumentCaptor<Consumer<ProcessInstanceFilter>> processInstanceFilterCapture;

    @BeforeEach
    void configureMocks() {
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Arrays.asList(
                  newActiveProcessInstance(ACTIVE_PROCESS_INSTANCE_KEY)
                      .setProcessDefinitionId("active-process")
                      .build(),
                  ProcessInstanceBuilder.newCompletedProcessInstance(COMPLETED_PROCESS_INSTANCE_KEY)
                      .setProcessDefinitionId("completed-process")
                      .build()));
    }

    @Test
    void shouldUseProcessInstanceEvent() {
      // given
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(ACTIVE_PROCESS_INSTANCE_KEY);

      // when
      assertThatProcessInstance(processInstanceEvent).isActive();

      // then
      verify(camundaDataSource).findProcessInstances(processInstanceFilterCapture.capture());

      processInstanceFilterCapture.getValue().accept(processInstanceFilter);
      verify(processInstanceFilter).processInstanceKey(ACTIVE_PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldUseDeprecatedProcessInstanceEvent() {
      // given
      final io.camunda.zeebe.client.api.response.ProcessInstanceEvent processInstanceEvent =
          mock(io.camunda.zeebe.client.api.response.ProcessInstanceEvent.class);
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(ACTIVE_PROCESS_INSTANCE_KEY);

      // when
      assertThatProcessInstance(processInstanceEvent).isActive();

      // then
      verify(camundaDataSource).findProcessInstances(processInstanceFilterCapture.capture());

      processInstanceFilterCapture.getValue().accept(processInstanceFilter);
      verify(processInstanceFilter).processInstanceKey(ACTIVE_PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWithProcessInstanceEvent() {
      // given
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(COMPLETED_PROCESS_INSTANCE_KEY);

      // when
      Assertions.assertThatThrownBy(
              () -> assertThatProcessInstance(processInstanceEvent).isActive())
          .hasMessage(
              "Process instance [key: %d] should be active but was completed.",
              COMPLETED_PROCESS_INSTANCE_KEY);

      // then
      verify(camundaDataSource).findProcessInstances(any());
    }

    @Test
    void shouldUseProcessInstanceResult() {
      // given
      when(processInstanceResult.getProcessInstanceKey()).thenReturn(ACTIVE_PROCESS_INSTANCE_KEY);

      // when
      assertThatProcessInstance(processInstanceResult).isActive();

      // then
      verify(camundaDataSource).findProcessInstances(processInstanceFilterCapture.capture());

      processInstanceFilterCapture.getValue().accept(processInstanceFilter);
      verify(processInstanceFilter).processInstanceKey(ACTIVE_PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWithProcessInstanceResult() {
      // given
      when(processInstanceResult.getProcessInstanceKey())
          .thenReturn(COMPLETED_PROCESS_INSTANCE_KEY);

      // when
      Assertions.assertThatThrownBy(
              () -> assertThatProcessInstance(processInstanceResult).isActive())
          .hasMessage(
              "Process instance [key: %d] should be active but was completed.",
              COMPLETED_PROCESS_INSTANCE_KEY);

      // then
      verify(camundaDataSource).findProcessInstances(any());
    }

    @Test
    void shouldUseByKeySelector() {
      // when
      assertThatProcessInstance(ProcessInstanceSelectors.byKey(ACTIVE_PROCESS_INSTANCE_KEY))
          .isActive();

      // then
      verify(camundaDataSource).findProcessInstances(processInstanceFilterCapture.capture());

      processInstanceFilterCapture.getValue().accept(processInstanceFilter);
      verify(processInstanceFilter).processInstanceKey(ACTIVE_PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWithByKeySelector() {
      // when
      Assertions.assertThatThrownBy(
              () ->
                  assertThatProcessInstance(
                          ProcessInstanceSelectors.byKey(COMPLETED_PROCESS_INSTANCE_KEY))
                      .isActive())
          .hasMessage(
              "Process instance [key: %d] should be active but was completed.",
              COMPLETED_PROCESS_INSTANCE_KEY);

      // then
      verify(camundaDataSource).findProcessInstances(any());
    }

    @Test
    void shouldUseByProcessIdSelector() {
      // when
      assertThatProcessInstance(ProcessInstanceSelectors.byProcessId("active-process")).isActive();

      // then
      verify(camundaDataSource).findProcessInstances(processInstanceFilterCapture.capture());

      processInstanceFilterCapture.getValue().accept(processInstanceFilter);
      verify(processInstanceFilter).processDefinitionId("active-process");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailWithByProcessIdSelector() {
      // when
      Assertions.assertThatThrownBy(
              () ->
                  assertThatProcessInstance(
                          ProcessInstanceSelectors.byProcessId("completed-process"))
                      .isActive())
          .hasMessage(
              "Process instance [process-id: '%s'] should be active but was completed.",
              "completed-process");

      // then
      verify(camundaDataSource).findProcessInstances(any());
    }
  }

  @Nested
  class IsActive {

    @Test
    void shouldBeActive() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      assertThatProcessInstance(processInstanceEvent).isActive();
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfCompleted() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  ProcessInstanceBuilder.newCompletedProcessInstance(PROCESS_INSTANCE_KEY)
                      .build()));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatProcessInstance(processInstanceEvent).isActive())
          .hasMessage(
              "Process instance [key: %d] should be active but was completed.",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfTerminated() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  ProcessInstanceBuilder.newTerminatedProcessInstance(PROCESS_INSTANCE_KEY)
                      .build()));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatProcessInstance(processInstanceEvent).isActive())
          .hasMessage(
              "Process instance [key: %d] should be active but was terminated.",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatProcessInstance(processInstanceEvent).isActive())
          .hasMessage(
              "Process instance [key: %d] should be active but was not created.",
              PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class IsCompleted {

    @Test
    void shouldBeCompleted() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  ProcessInstanceBuilder.newCompletedProcessInstance(PROCESS_INSTANCE_KEY)
                      .build()));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      assertThatProcessInstance(processInstanceEvent).isCompleted();
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfTerminated() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  ProcessInstanceBuilder.newTerminatedProcessInstance(PROCESS_INSTANCE_KEY)
                      .build()));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatProcessInstance(processInstanceEvent).isCompleted())
          .hasMessage(
              "Process instance [key: %d] should be completed but was terminated.",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldWaitUntilCompleted() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()))
          .thenReturn(
              Collections.singletonList(
                  ProcessInstanceBuilder.newCompletedProcessInstance(PROCESS_INSTANCE_KEY)
                      .build()));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      assertThatProcessInstance(processInstanceEvent).isCompleted();

      verify(camundaDataSource, times(2)).findProcessInstances(any());
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfActive() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatProcessInstance(processInstanceEvent).isCompleted())
          .hasMessage(
              "Process instance [key: %d] should be completed but was active.",
              PROCESS_INSTANCE_KEY);

      verify(camundaDataSource).findProcessInstances(any());
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatProcessInstance(processInstanceEvent).isCompleted())
          .hasMessage(
              "Process instance [key: %d] should be completed but was not created.",
              PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class IsTerminated {

    @Test
    void shouldBeTerminated() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  ProcessInstanceBuilder.newTerminatedProcessInstance(PROCESS_INSTANCE_KEY)
                      .build()));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      assertThatProcessInstance(processInstanceEvent).isTerminated();
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfCompleted() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  ProcessInstanceBuilder.newCompletedProcessInstance(PROCESS_INSTANCE_KEY)
                      .build()));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatProcessInstance(processInstanceEvent).isTerminated())
          .hasMessage(
              "Process instance [key: %d] should be terminated but was completed.",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldWaitUntilTerminated() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()))
          .thenReturn(
              Collections.singletonList(
                  ProcessInstanceBuilder.newTerminatedProcessInstance(PROCESS_INSTANCE_KEY)
                      .build()));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      assertThatProcessInstance(processInstanceEvent).isTerminated();

      verify(camundaDataSource, times(2)).findProcessInstances(any());
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfActive() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatProcessInstance(processInstanceEvent).isTerminated())
          .hasMessage(
              "Process instance [key: %d] should be terminated but was active.",
              PROCESS_INSTANCE_KEY);

      verify(camundaDataSource).findProcessInstances(any());
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatProcessInstance(processInstanceEvent).isTerminated())
          .hasMessage(
              "Process instance [key: %d] should be terminated but was not created.",
              PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class IsCreated {

    @Test
    void shouldBeCreatedIfActive() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      assertThatProcessInstance(processInstanceEvent).isCreated();
    }

    @Test
    void shouldBeCreatedIfCompleted() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  ProcessInstanceBuilder.newCompletedProcessInstance(PROCESS_INSTANCE_KEY)
                      .build()));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      assertThatProcessInstance(processInstanceEvent).isCreated();
    }

    @Test
    void shouldBeCreatedIfTerminated() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  ProcessInstanceBuilder.newTerminatedProcessInstance(PROCESS_INSTANCE_KEY)
                      .build()));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      assertThatProcessInstance(processInstanceEvent).isCreated();
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfNotCreated() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatProcessInstance(processInstanceEvent).isCreated())
          .hasMessage(
              "Process instance [key: %d] should be created but was not created.",
              PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class FluentAssertions {

    private final ElementInstance activeElementInstance =
        ElementInstanceBuilder.newActiveElementInstance("A", PROCESS_INSTANCE_KEY);

    private final Variable variable = VariableBuilder.newVariable("x", "1").build();

    @BeforeEach
    void configureMocks() {
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));
    }

    @Test
    void shouldAssertStateAndElements() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Collections.singletonList(activeElementInstance));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      assertThatProcessInstance(processInstanceEvent).isActive().hasActiveElements("A");
    }

    @Test
    void shouldAssertStateAndVariables() {
      // given
      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variable));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      assertThatProcessInstance(processInstanceEvent).isActive().hasVariable("x", 1);
    }

    @Test
    void shouldAssertElementsAndState() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Collections.singletonList(activeElementInstance));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      assertThatProcessInstance(processInstanceEvent).hasActiveElements("A").isActive();
    }

    @Test
    void shouldAssertVariablesAndState() {
      // given
      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variable));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      assertThatProcessInstance(processInstanceEvent).hasVariable("x", 1).isActive();
    }

    @Test
    void shouldAssertElementsAndVariables() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Collections.singletonList(activeElementInstance));

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variable));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      assertThatProcessInstance(processInstanceEvent).hasActiveElements("A").hasVariable("x", 1);
    }

    @Test
    void shouldAssertVariablesAndElements() {
      // given
      when(camundaDataSource.findElementInstances(any()))
          .thenReturn(Collections.singletonList(activeElementInstance));

      when(camundaDataSource.findGlobalVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variable));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      assertThatProcessInstance(processInstanceEvent).hasVariable("x", 1).hasActiveElements("A");
    }
  }

  @Nested
  class ParentChildProcesses {

    private static final long PARENT_PROCESS_KEY = 2;
    private static final long BAD_PARENT_PROCESS_KEY = 777;

    @Test
    void shouldFindChildProcess() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  newActiveChildProcessInstance(PROCESS_INSTANCE_KEY, PARENT_PROCESS_KEY).build()));

      // then
      assertThatProcessInstance(byParentProcesInstanceKey(PARENT_PROCESS_KEY)).isCreated();
    }

    @Test
    void shouldReturnFirstValidChildProcess() {
      // given
      final List<ProcessInstance> searchResult = new ArrayList<>();
      searchResult.add(
          spy(newActiveChildProcessInstance(PROCESS_INSTANCE_KEY, PARENT_PROCESS_KEY).build()));
      searchResult.add(spy(newActiveChildProcessInstance(2, PARENT_PROCESS_KEY).build()));
      searchResult.add(spy(newActiveChildProcessInstance(3, PARENT_PROCESS_KEY).build()));

      when(camundaDataSource.findProcessInstances(any())).thenReturn(searchResult);

      // then
      assertThatProcessInstance(byParentProcesInstanceKey(PARENT_PROCESS_KEY)).isCreated();

      final ProcessInstance firstChildProcessInstance = searchResult.get(0);
      verify(firstChildProcessInstance, times(1)).getState();
      verifyNoInteractions(searchResult.get(1));
      verifyNoInteractions(searchResult.get(2));
    }

    @Test
    void shouldFilterOutOtherParentProcesses() {
      // given
      final List<ProcessInstance> searchResult = new ArrayList<>();
      searchResult.add(
          spy(newActiveChildProcessInstance(PROCESS_INSTANCE_KEY, PARENT_PROCESS_KEY).build()));
      searchResult.add(
          spy(newActiveChildProcessInstance(PROCESS_INSTANCE_KEY, BAD_PARENT_PROCESS_KEY).build()));

      when(camundaDataSource.findProcessInstances(any())).thenReturn(searchResult);

      // then
      assertThatProcessInstance(byParentProcesInstanceKey(PARENT_PROCESS_KEY)).isCreated();

      final ProcessInstance firstChildProcessInstance = searchResult.get(0);
      verify(firstChildProcessInstance, times(1)).getState();
      verifyNoInteractions(searchResult.get(1));
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldDescribeParentKey() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // then
      Assertions.assertThatThrownBy(
              () ->
                  assertThatProcessInstance(byParentProcesInstanceKey(PARENT_PROCESS_KEY))
                      .isCreated())
          .hasMessage(
              "Process instance [parent key: %d] should be created but was not created.",
              PARENT_PROCESS_KEY);
    }
  }

  @Nested
  class MessageSubscriptions {

    @Captor private ArgumentCaptor<Consumer<MessageSubscriptionFilter>> filterCaptor;

    @Mock(answer = Answers.RETURNS_SELF)
    private MessageSubscriptionFilter messageSubscriptionFilter;

    @Test
    void shouldFindMessageSubscription() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      when(camundaDataSource.getMessageSubscriptions(filterCaptor.capture()))
          .thenReturn(
              Collections.singletonList(
                  new MessageSubscriptionImpl(new MessageSubscriptionResult())));

      // then
      assertThatProcessInstance(processInstanceEvent).isWaitingForMessage("expected");

      filterCaptor.getValue().accept(messageSubscriptionFilter);
      verify(messageSubscriptionFilter).processInstanceKey(PROCESS_INSTANCE_KEY);
      verify(messageSubscriptionFilter).messageName("expected");
    }

    @Test
    void shouldFindMessageSubscriptionWithCorrelationKey() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      when(camundaDataSource.getMessageSubscriptions(filterCaptor.capture()))
          .thenReturn(
              Collections.singletonList(
                  new MessageSubscriptionImpl(new MessageSubscriptionResult())));

      // then
      assertThatProcessInstance(processInstanceEvent)
          .isWaitingForMessage("expected", "correlation-key");

      filterCaptor.getValue().accept(messageSubscriptionFilter);
      verify(messageSubscriptionFilter).processInstanceKey(PROCESS_INSTANCE_KEY);
      verify(messageSubscriptionFilter).messageName("expected");
      verify(messageSubscriptionFilter).correlationKey("correlation-key");
    }

    @Test
    void shouldAssertNoMessageSubscriptionFound() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      when(camundaDataSource.getMessageSubscriptions(filterCaptor.capture()))
          .thenReturn(Collections.emptyList());

      // then
      assertThatProcessInstance(processInstanceEvent).isNotWaitingForMessage("expected");

      filterCaptor.getValue().accept(messageSubscriptionFilter);
      verify(messageSubscriptionFilter).processInstanceKey(PROCESS_INSTANCE_KEY);
      verify(messageSubscriptionFilter).messageName("expected");
    }

    @Test
    void shouldAssertNoMessageSubscriptionFoundWithCorrelationKey() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      when(camundaDataSource.getMessageSubscriptions(filterCaptor.capture()))
          .thenReturn(Collections.emptyList());

      // then
      assertThatProcessInstance(processInstanceEvent)
          .isNotWaitingForMessage("expected", "correlation-key");

      filterCaptor.getValue().accept(messageSubscriptionFilter);
      verify(messageSubscriptionFilter).processInstanceKey(PROCESS_INSTANCE_KEY);
      verify(messageSubscriptionFilter).messageName("expected");
      verify(messageSubscriptionFilter).correlationKey("correlation-key");
    }

    @Test
    void shouldAwaitMessageSubscription() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.emptyList())
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      when(camundaDataSource.getMessageSubscriptions(filterCaptor.capture()))
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.emptyList())
          .thenReturn(
              Collections.singletonList(
                  new MessageSubscriptionImpl(new MessageSubscriptionResult())));

      // then
      assertThatProcessInstance(processInstanceEvent).isWaitingForMessage("expected");

      filterCaptor.getValue().accept(messageSubscriptionFilter);
      verify(messageSubscriptionFilter).processInstanceKey(PROCESS_INSTANCE_KEY);
      verify(messageSubscriptionFilter).messageName("expected");
    }

    @Test
    void shouldAwaitNoMessageSubscription() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.emptyList())
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      when(camundaDataSource.getMessageSubscriptions(filterCaptor.capture()))
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.emptyList());

      // then
      assertThatProcessInstance(processInstanceEvent).isNotWaitingForMessage("expected");

      filterCaptor.getValue().accept(messageSubscriptionFilter);
      verify(messageSubscriptionFilter).processInstanceKey(PROCESS_INSTANCE_KEY);
      verify(messageSubscriptionFilter).messageName("expected");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldErrorIfNoMessageSubscriptionWasFound() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      when(camundaDataSource.getMessageSubscriptions(filterCaptor.capture()))
          .thenReturn(Collections.emptyList());

      // then
      Assertions.assertThatThrownBy(
              () -> assertThatProcessInstance(processInstanceEvent).isWaitingForMessage("expected"))
          .hasMessage(
              "Process instance [key: 1] should have an active message subscription [message-name: 'expected'], but no such subscription was found.");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldErrorIfNoMessageSubscriptionWasFoundWithCorrelationKey() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      when(camundaDataSource.getMessageSubscriptions(filterCaptor.capture()))
          .thenReturn(Collections.emptyList());

      // then
      Assertions.assertThatThrownBy(
              () ->
                  assertThatProcessInstance(processInstanceEvent)
                      .isWaitingForMessage("expected", "correlation-key"))
          .hasMessage(
              "Process instance [key: 1] should have a message subscription [message-name: 'expected', correlation-key: 'correlation-key'], but no such subscription was found.");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldErrorIfMessageSubscriptionWasFoundDespiteNotExpectingOne() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      when(camundaDataSource.getMessageSubscriptions(filterCaptor.capture()))
          .thenReturn(
              Collections.singletonList(
                  new MessageSubscriptionImpl(
                      new MessageSubscriptionResult()
                          .messageName("expected")
                          .correlationKey("correlation-key"))));

      // then
      Assertions.assertThatThrownBy(
              () ->
                  assertThatProcessInstance(processInstanceEvent)
                      .isNotWaitingForMessage("expected"))
          .hasMessage(
              "Process instance [key: 1] should have no active message subscription [message-name: 'expected'], but the following subscriptions were active:\n"
                  + "\t- name: 'expected', correlation-key: 'correlation-key'");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldErrorIfMessageSubscriptionWithCorrelationKeyWasFoundDespiteNotExpectingOne() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      when(camundaDataSource.getMessageSubscriptions(filterCaptor.capture()))
          .thenReturn(
              Collections.singletonList(
                  new MessageSubscriptionImpl(
                      new MessageSubscriptionResult()
                          .messageName("expected")
                          .correlationKey("correlation-key"))));

      // then
      Assertions.assertThatThrownBy(
              () ->
                  assertThatProcessInstance(processInstanceEvent)
                      .isNotWaitingForMessage("expected", "correlation-key"))
          .hasMessage(
              "Process instance [key: 1] should have no active message subscription [message-name: 'expected', correlation-key: 'correlation-key'], but the following subscriptions were active:\n"
                  + "\t- name: 'expected', correlation-key: 'correlation-key'");
    }
  }

  @Nested
  class CorrelatedMessages {

    @Captor private ArgumentCaptor<Consumer<CorrelatedMessageFilter>> filterCaptor;

    @Mock(answer = Answers.RETURNS_SELF)
    private CorrelatedMessageFilter correlatedMessageFilter;

    @Test
    void shouldFindCorrelatedMessage() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      when(camundaDataSource.getCorrelatedMessages(filterCaptor.capture()))
          .thenReturn(
              Collections.singletonList(new CorrelatedMessageImpl(new CorrelatedMessageResult())));

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasCorrelatedMessage("expected");

      filterCaptor.getValue().accept(correlatedMessageFilter);
      verify(correlatedMessageFilter).processInstanceKey(PROCESS_INSTANCE_KEY);
      verify(correlatedMessageFilter).messageName("expected");
    }

    @Test
    void shouldFindCorrelatedMessageWithCorrelationKey() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      when(camundaDataSource.getCorrelatedMessages(filterCaptor.capture()))
          .thenReturn(
              Collections.singletonList(new CorrelatedMessageImpl(new CorrelatedMessageResult())));

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasCorrelatedMessage("expected", "correlation-key");

      filterCaptor.getValue().accept(correlatedMessageFilter);
      verify(correlatedMessageFilter).processInstanceKey(PROCESS_INSTANCE_KEY);
      verify(correlatedMessageFilter).messageName("expected");
      verify(correlatedMessageFilter).correlationKey("correlation-key");
    }

    @Test
    void shouldAwaitCorrelatedMessage() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.emptyList())
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      when(camundaDataSource.getCorrelatedMessages(filterCaptor.capture()))
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.emptyList())
          .thenReturn(
              Collections.singletonList(new CorrelatedMessageImpl(new CorrelatedMessageResult())));

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent)
          .hasCorrelatedMessage("expected");

      filterCaptor.getValue().accept(correlatedMessageFilter);
      verify(correlatedMessageFilter).processInstanceKey(PROCESS_INSTANCE_KEY);
      verify(correlatedMessageFilter).messageName("expected");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldErrorIfNoCorrelatedMessageWasFound() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      when(camundaDataSource.getCorrelatedMessages(filterCaptor.capture()))
          .thenReturn(Collections.emptyList());

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasCorrelatedMessage("expected"))
          .hasMessage(
              "Process instance [key: 1] should have at least one correlated message [message-name: 'expected'], but found none.");
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldErrorIfNoCorrelatedMessageWasFoundWithCorrelationKey() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      when(camundaDataSource.getCorrelatedMessages(filterCaptor.capture()))
          .thenReturn(Collections.emptyList());

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasCorrelatedMessage("expected", "correlation-key"))
          .hasMessage(
              "Process instance [key: 1] should have at least one correlated message [message-name: 'expected', correlation-key: 'correlation-key'], but found none.");
    }
  }
}
