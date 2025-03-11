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
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.response.ProcessInstanceResult;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.response.FlowNodeInstance;
import io.camunda.client.api.search.response.Variable;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.utils.FlowNodeInstanceBuilder;
import io.camunda.process.test.utils.ProcessInstanceBuilder;
import io.camunda.process.test.utils.VariableBuilder;
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
                  ProcessInstanceBuilder.newActiveProcessInstance(ACTIVE_PROCESS_INSTANCE_KEY)
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
      CamundaAssert.assertThat(processInstanceEvent).isActive();

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
      CamundaAssert.assertThat(processInstanceEvent).isActive();

      // then
      verify(camundaDataSource).findProcessInstances(processInstanceFilterCapture.capture());

      processInstanceFilterCapture.getValue().accept(processInstanceFilter);
      verify(processInstanceFilter).processInstanceKey(ACTIVE_PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailWithProcessInstanceEvent() {
      // given
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(COMPLETED_PROCESS_INSTANCE_KEY);

      // when
      Assertions.assertThatThrownBy(() -> CamundaAssert.assertThat(processInstanceEvent).isActive())
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
      CamundaAssert.assertThat(processInstanceResult).isActive();

      // then
      verify(camundaDataSource).findProcessInstances(processInstanceFilterCapture.capture());

      processInstanceFilterCapture.getValue().accept(processInstanceFilter);
      verify(processInstanceFilter).processInstanceKey(ACTIVE_PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailWithProcessInstanceResult() {
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
      verify(camundaDataSource).findProcessInstances(any());
    }

    @Test
    void shouldUseByKeySelector() {
      // when
      CamundaAssert.assertThat(ProcessInstanceSelectors.byKey(ACTIVE_PROCESS_INSTANCE_KEY))
          .isActive();

      // then
      verify(camundaDataSource).findProcessInstances(processInstanceFilterCapture.capture());

      processInstanceFilterCapture.getValue().accept(processInstanceFilter);
      verify(processInstanceFilter).processInstanceKey(ACTIVE_PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailWithByKeySelector() {
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
      verify(camundaDataSource).findProcessInstances(any());
    }

    @Test
    void shouldUseByProcessIdSelector() {
      // when
      CamundaAssert.assertThat(ProcessInstanceSelectors.byProcessId("active-process")).isActive();

      // then
      verify(camundaDataSource).findProcessInstances(processInstanceFilterCapture.capture());

      processInstanceFilterCapture.getValue().accept(processInstanceFilter);
      verify(processInstanceFilter).processDefinitionId("active-process");
    }

    @Test
    void shouldFailWithByProcessIdSelector() {
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
              Collections.singletonList(
                  ProcessInstanceBuilder.newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).isActive();
    }

    @Test
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
      Assertions.assertThatThrownBy(() -> CamundaAssert.assertThat(processInstanceEvent).isActive())
          .hasMessage(
              "Process instance [key: %d] should be active but was completed.",
              PROCESS_INSTANCE_KEY);
    }

    @Test
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
      Assertions.assertThatThrownBy(() -> CamundaAssert.assertThat(processInstanceEvent).isActive())
          .hasMessage(
              "Process instance [key: %d] should be active but was terminated.",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(() -> CamundaAssert.assertThat(processInstanceEvent).isActive())
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
      CamundaAssert.assertThat(processInstanceEvent).isCompleted();
    }

    @Test
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
              () -> CamundaAssert.assertThat(processInstanceEvent).isCompleted())
          .hasMessage(
              "Process instance [key: %d] should be completed but was terminated.",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldWaitUntilCompleted() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  ProcessInstanceBuilder.newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()))
          .thenReturn(
              Collections.singletonList(
                  ProcessInstanceBuilder.newCompletedProcessInstance(PROCESS_INSTANCE_KEY)
                      .build()));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).isCompleted();

      verify(camundaDataSource, times(2)).findProcessInstances(any());
    }

    @Test
    void shouldFailIfActive() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  ProcessInstanceBuilder.newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).isCompleted())
          .hasMessage(
              "Process instance [key: %d] should be completed but was active.",
              PROCESS_INSTANCE_KEY);

      verify(camundaDataSource, atLeast(2)).findProcessInstances(any());
    }

    @Test
    void shouldFailIfNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).isCompleted())
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
      CamundaAssert.assertThat(processInstanceEvent).isTerminated();
    }

    @Test
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
              () -> CamundaAssert.assertThat(processInstanceEvent).isTerminated())
          .hasMessage(
              "Process instance [key: %d] should be terminated but was completed.",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldWaitUntilTerminated() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  ProcessInstanceBuilder.newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()))
          .thenReturn(
              Collections.singletonList(
                  ProcessInstanceBuilder.newTerminatedProcessInstance(PROCESS_INSTANCE_KEY)
                      .build()));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).isTerminated();

      verify(camundaDataSource, times(2)).findProcessInstances(any());
    }

    @Test
    void shouldFailIfActive() {
      // given
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  ProcessInstanceBuilder.newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).isTerminated())
          .hasMessage(
              "Process instance [key: %d] should be terminated but was active.",
              PROCESS_INSTANCE_KEY);

      verify(camundaDataSource, atLeast(2)).findProcessInstances(any());
    }

    @Test
    void shouldFailIfNotFound() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).isTerminated())
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
              Collections.singletonList(
                  ProcessInstanceBuilder.newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).isCreated();
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
      CamundaAssert.assertThat(processInstanceEvent).isCreated();
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
      CamundaAssert.assertThat(processInstanceEvent).isCreated();
    }

    @Test
    void shouldFailIfNotCreated() {
      // given
      when(camundaDataSource.findProcessInstances(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).isCreated())
          .hasMessage(
              "Process instance [key: %d] should be created but was not created.",
              PROCESS_INSTANCE_KEY);
    }
  }

  @Nested
  class FluentAssertions {

    private final FlowNodeInstance activeFlowNodeInstance =
        FlowNodeInstanceBuilder.newActiveFlowNodeInstance("A", PROCESS_INSTANCE_KEY);

    private final Variable variable = VariableBuilder.newVariable("x", "1").build();

    @BeforeEach
    void configureMocks() {
      when(camundaDataSource.findProcessInstances(any()))
          .thenReturn(
              Collections.singletonList(
                  ProcessInstanceBuilder.newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));
    }

    @Test
    void shouldAssertStateAndElements() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Collections.singletonList(activeFlowNodeInstance));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).isActive().hasActiveElements("A");
    }

    @Test
    void shouldAssertStateAndVariables() {
      // given
      when(camundaDataSource.findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variable));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).isActive().hasVariable("x", 1);
    }

    @Test
    void shouldAssertElementsAndState() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Collections.singletonList(activeFlowNodeInstance));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasActiveElements("A").isActive();
    }

    @Test
    void shouldAssertVariablesAndState() {
      // given
      when(camundaDataSource.findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variable));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasVariable("x", 1).isActive();
    }

    @Test
    void shouldAssertElementsAndVariables() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Collections.singletonList(activeFlowNodeInstance));

      when(camundaDataSource.findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variable));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasActiveElements("A").hasVariable("x", 1);
    }

    @Test
    void shouldAssertVariablesAndElements() {
      // given
      when(camundaDataSource.findFlowNodeInstances(any()))
          .thenReturn(Collections.singletonList(activeFlowNodeInstance));

      when(camundaDataSource.findVariablesByProcessInstanceKey(PROCESS_INSTANCE_KEY))
          .thenReturn(Collections.singletonList(variable));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasVariable("x", 1).hasActiveElements("A");
    }
  }
}
