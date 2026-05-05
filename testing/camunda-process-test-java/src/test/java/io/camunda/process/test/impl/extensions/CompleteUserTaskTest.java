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
package io.camunda.process.test.impl.extensions;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.CompleteUserTaskResponse;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.filter.VariableFilter;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import io.camunda.process.test.impl.client.CamundaClockClient;
import io.camunda.process.test.impl.extension.CamundaProcessTestContextImpl;
import io.camunda.process.test.impl.extension.ConditionalBehaviorEngine;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntime;
import io.camunda.process.test.utils.DevAwaitBehavior;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CompleteUserTaskTest {

  private static final Long USER_TASK_KEY = 100L;
  private static final String USER_TASK_ELEMENT_ID = "task1";

  @Mock private CamundaProcessTestRuntime camundaProcessTestRuntime;
  @Mock private Consumer<AutoCloseable> clientCreationCallback;
  @Mock private CamundaClockClient clockClient;
  @Mock private JsonMapper jsonMapper;

  @Mock private CamundaClientBuilderFactory camundaClientBuilderFactory;
  @Mock private CamundaClientBuilder camundaClientBuilder;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private UserTask userTask;

  @Captor private ArgumentCaptor<Consumer<UserTaskFilter>> userTaskFilterCaptor;
  @Mock private UserTaskFilter userTaskFilter;

  private CamundaProcessTestContext camundaProcessTestContext;

  @BeforeEach
  void configureMocks() {
    when(camundaProcessTestRuntime.getCamundaClientBuilderFactory())
        .thenReturn(camundaClientBuilderFactory);
    when(camundaClientBuilderFactory.get()).thenReturn(camundaClientBuilder);
    when(camundaClientBuilder.build()).thenReturn(camundaClient);
  }

  @Nested
  class HappyCases {

    @BeforeEach
    void setup() {
      camundaProcessTestContext =
          new CamundaProcessTestContextImpl(
              camundaProcessTestRuntime,
              clientCreationCallback,
              clockClient,
              DevAwaitBehavior::expectSuccess,
              jsonMapper,
              new ConditionalBehaviorEngine());

      when(camundaClient
              .newUserTaskSearchRequest()
              .filter(userTaskFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.singletonList(userTask));

      when(userTask.getUserTaskKey()).thenReturn(USER_TASK_KEY);
      when(userTask.getElementId()).thenReturn(USER_TASK_ELEMENT_ID);
    }

    @Test
    void shouldCompleteUserTask() {
      // when
      camundaProcessTestContext.completeUserTask(USER_TASK_ELEMENT_ID);

      // then
      verify(
              camundaClient
                  .newCompleteUserTaskCommand(USER_TASK_KEY)
                  .variables(Collections.emptyMap()))
          .send();
    }

    @Test
    void shouldCompleteUserTaskWithVariables() {
      // given
      final Map<String, Object> variables = Collections.singletonMap("result", "okay");

      // when
      camundaProcessTestContext.completeUserTask(USER_TASK_ELEMENT_ID, variables);

      // then
      verify(camundaClient.newCompleteUserTaskCommand(USER_TASK_KEY).variables(variables)).send();
    }

    @Test
    void shouldSearchByElementId() {
      // when
      camundaProcessTestContext.completeUserTask(USER_TASK_ELEMENT_ID);

      // then
      userTaskFilterCaptor.getValue().accept(userTaskFilter);
      verify(userTaskFilter).elementId(USER_TASK_ELEMENT_ID);
      verify(userTaskFilter).state(UserTaskState.CREATED);

      verifyNoMoreInteractions(userTaskFilter);
    }

    @Test
    void shouldSearchBySelector() {
      // when
      camundaProcessTestContext.completeUserTask(
          UserTaskSelectors.byElementId(USER_TASK_ELEMENT_ID));

      // then
      userTaskFilterCaptor.getValue().accept(userTaskFilter);
      verify(userTaskFilter).elementId(USER_TASK_ELEMENT_ID);
      verify(userTaskFilter).state(UserTaskState.CREATED);

      verifyNoMoreInteractions(userTaskFilter);
    }

    @Test
    void shouldAwaitUntilUserTaskIsPresent() {
      // given
      when(camundaClient
              .newUserTaskSearchRequest()
              .filter(userTaskFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.singletonList(userTask));

      clearInvocations(camundaClient);

      // when
      camundaProcessTestContext.completeUserTask(USER_TASK_ELEMENT_ID);

      // then
      verify(camundaClient, times(2)).newUserTaskSearchRequest();
    }

    @Test
    void shouldRetryCompletion() {
      // given
      when(camundaClient
              .newCompleteUserTaskCommand(USER_TASK_KEY)
              .variables(Collections.emptyMap())
              .send()
              .join())
          .thenThrow(new ClientException("expected"))
          .thenReturn(mock(CompleteUserTaskResponse.class));

      clearInvocations(camundaClient);

      // when
      camundaProcessTestContext.completeUserTask(USER_TASK_ELEMENT_ID);

      // then
      verify(camundaClient, times(2)).newCompleteUserTaskCommand(USER_TASK_KEY);
    }
  }

  @Nested
  class FailureCases {

    @BeforeEach
    void setup() {
      camundaProcessTestContext =
          new CamundaProcessTestContextImpl(
              camundaProcessTestRuntime,
              clientCreationCallback,
              clockClient,
              DevAwaitBehavior::expectFailure,
              jsonMapper,
              new ConditionalBehaviorEngine());
    }

    @Test
    void shouldFailIfNoUserTaskIsPresent() {
      // given
      when(camundaClient
              .newUserTaskSearchRequest()
              .filter(userTaskFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList());

      // when/then
      assertThatThrownBy(() -> camundaProcessTestContext.completeUserTask(USER_TASK_ELEMENT_ID))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining(
              "Expected to complete user task [elementId: %s] but no user task is available.",
              USER_TASK_ELEMENT_ID);
    }
  }

  @Nested
  @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
  class CompleteUserTaskWithVariableMapper {

    private static final long PROCESS_INSTANCE_KEY = 200L;
    private static final long ELEMENT_INSTANCE_KEY = 300L;

    @Captor private ArgumentCaptor<Consumer<VariableFilter>> variableFilterCaptor;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private VariableFilter variableFilter;

    @BeforeEach
    void setup() {
      camundaProcessTestContext =
          new CamundaProcessTestContextImpl(
              camundaProcessTestRuntime,
              clientCreationCallback,
              clockClient,
              DevAwaitBehavior::expectSuccess,
              jsonMapper,
              new ConditionalBehaviorEngine());

      when(camundaClient
              .newUserTaskSearchRequest()
              .filter(userTaskFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.singletonList(userTask));

      when(userTask.getUserTaskKey()).thenReturn(USER_TASK_KEY);
      when(userTask.getElementId()).thenReturn(USER_TASK_ELEMENT_ID);
      when(userTask.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
      when(userTask.getElementInstanceKey()).thenReturn(ELEMENT_INSTANCE_KEY);

      when(camundaClient
              .newVariableSearchRequest()
              .filter(variableFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList());
    }

    private Variable variable(
        final String name, final String value, final long scopeKey, final boolean truncated) {
      final Variable variable = mock(Variable.class);
      when(variable.getName()).thenReturn(name);
      when(variable.getValue()).thenReturn(value);
      when(variable.getScopeKey()).thenReturn(scopeKey);
      when(variable.isTruncated()).thenReturn(truncated);
      return variable;
    }

    @Test
    void shouldCompleteUserTaskWithVariableMapper() {
      // given
      final Variable idVariable = variable("id", "1", PROCESS_INSTANCE_KEY, false);
      final Variable localVariable = variable("local", "\"hello\"", ELEMENT_INSTANCE_KEY, false);
      when(camundaClient
              .newVariableSearchRequest()
              .filter(variableFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Arrays.asList(idVariable, localVariable));
      when(jsonMapper.fromJson("1", Object.class)).thenReturn(1);
      when(jsonMapper.fromJson("\"hello\"", Object.class)).thenReturn("hello");

      final Map<String, Object> capturedInput = new HashMap<>();
      final Map<String, Object> outputVariables = Collections.singletonMap("user", "Alice");

      // when
      camundaProcessTestContext.completeUserTask(
          USER_TASK_ELEMENT_ID,
          inputVars -> {
            capturedInput.putAll(inputVars);
            return outputVariables;
          });

      // then
      org.assertj.core.api.Assertions.assertThat(capturedInput)
          .containsEntry("id", 1)
          .containsEntry("local", "hello");
      verify(camundaClient.newCompleteUserTaskCommand(USER_TASK_KEY).variables(outputVariables))
          .send();
    }

    @Test
    void shouldCompleteUserTaskWithVariableMapperBySelector() {
      // given
      final Map<String, Object> outputVariables = Collections.singletonMap("user", "Bob");

      // when
      camundaProcessTestContext.completeUserTask(
          UserTaskSelectors.byElementId(USER_TASK_ELEMENT_ID), inputVars -> outputVariables);

      // then
      verify(camundaClient.newCompleteUserTaskCommand(USER_TASK_KEY).variables(outputVariables))
          .send();
    }

    @Test
    void shouldCompleteUserTaskWithEmptyInputVariables() {
      // given - the default mock returns an empty list of variables
      final Map<String, Object> capturedInput = new HashMap<>();
      capturedInput.put("sentinel", "untouched");

      // when
      camundaProcessTestContext.completeUserTask(
          USER_TASK_ELEMENT_ID,
          inputVars -> {
            capturedInput.clear();
            capturedInput.putAll(inputVars);
            return Collections.emptyMap();
          });

      // then
      org.assertj.core.api.Assertions.assertThat(capturedInput).isEmpty();
      verify(
              camundaClient
                  .newCompleteUserTaskCommand(USER_TASK_KEY)
                  .variables(Collections.emptyMap()))
          .send();
    }

    @Test
    void shouldPreferLocalOverGlobalVariable() {
      // given - same name 'id' at both scopes; local must win
      final Variable globalId = variable("id", "1", PROCESS_INSTANCE_KEY, false);
      final Variable localId = variable("id", "2", ELEMENT_INSTANCE_KEY, false);
      when(camundaClient
              .newVariableSearchRequest()
              .filter(variableFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Arrays.asList(globalId, localId));
      when(jsonMapper.fromJson("2", Object.class)).thenReturn(2);

      final Map<String, Object> capturedInput = new HashMap<>();

      // when
      camundaProcessTestContext.completeUserTask(
          USER_TASK_ELEMENT_ID,
          inputVars -> {
            capturedInput.putAll(inputVars);
            return Collections.emptyMap();
          });

      // then
      org.assertj.core.api.Assertions.assertThat(capturedInput).containsEntry("id", 2);
    }

    @Test
    void shouldFilterVariablesByProcessInstanceKey() {
      // when
      camundaProcessTestContext.completeUserTask(
          USER_TASK_ELEMENT_ID, inputVars -> Collections.emptyMap());

      // then
      variableFilterCaptor.getValue().accept(variableFilter);
      verify(variableFilter).processInstanceKey(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfVariableMapperIsNull() {
      // given
      clearInvocations(camundaClient);

      // when/then
      assertThatThrownBy(
              () ->
                  camundaProcessTestContext.completeUserTask(
                      USER_TASK_ELEMENT_ID,
                      (Function<Map<String, Object>, Map<String, Object>>) null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("variableMapper");

      // and: no search/completion took place
      verify(camundaClient, org.mockito.Mockito.never()).newUserTaskSearchRequest();
    }

    @Test
    void shouldRetryCompletionWithoutReinvokingMapper() {
      // given
      when(camundaClient
              .newCompleteUserTaskCommand(USER_TASK_KEY)
              .variables(org.mockito.ArgumentMatchers.<Map<String, Object>>any())
              .send()
              .join())
          .thenThrow(new ClientException("expected"))
          .thenReturn(mock(CompleteUserTaskResponse.class));

      final java.util.concurrent.atomic.AtomicInteger mapperInvocations =
          new java.util.concurrent.atomic.AtomicInteger();

      clearInvocations(camundaClient);

      // when
      camundaProcessTestContext.completeUserTask(
          USER_TASK_ELEMENT_ID,
          inputVars -> {
            mapperInvocations.incrementAndGet();
            return Collections.emptyMap();
          });

      // then
      org.assertj.core.api.Assertions.assertThat(mapperInvocations.get()).isEqualTo(1);
      verify(camundaClient, times(2)).newCompleteUserTaskCommand(USER_TASK_KEY);
    }
  }

  @Nested
  @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
  class CompleteUserTaskWithVariableMapperFailureCases {

    private static final long PROCESS_INSTANCE_KEY = 200L;
    private static final long ELEMENT_INSTANCE_KEY = 300L;

    @Captor private ArgumentCaptor<Consumer<VariableFilter>> variableFilterCaptor;

    @BeforeEach
    void setup() {
      camundaProcessTestContext =
          new CamundaProcessTestContextImpl(
              camundaProcessTestRuntime,
              clientCreationCallback,
              clockClient,
              DevAwaitBehavior::expectFailure,
              jsonMapper,
              new ConditionalBehaviorEngine());
    }

    private Variable variable(
        final String name, final String value, final long scopeKey, final boolean truncated) {
      final Variable variable = mock(Variable.class);
      when(variable.getName()).thenReturn(name);
      when(variable.getValue()).thenReturn(value);
      when(variable.getScopeKey()).thenReturn(scopeKey);
      when(variable.isTruncated()).thenReturn(truncated);
      return variable;
    }

    private void mockUserTaskFound() {
      when(camundaClient
              .newUserTaskSearchRequest()
              .filter(userTaskFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.singletonList(userTask));
      when(userTask.getUserTaskKey()).thenReturn(USER_TASK_KEY);
      when(userTask.getElementId()).thenReturn(USER_TASK_ELEMENT_ID);
      when(userTask.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
      when(userTask.getElementInstanceKey()).thenReturn(ELEMENT_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfNoUserTaskIsPresentForVariableMapper() {
      // given
      when(camundaClient
              .newUserTaskSearchRequest()
              .filter(userTaskFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList());

      final java.util.concurrent.atomic.AtomicInteger mapperInvocations =
          new java.util.concurrent.atomic.AtomicInteger();

      // when/then
      assertThatThrownBy(
              () ->
                  camundaProcessTestContext.completeUserTask(
                      USER_TASK_ELEMENT_ID,
                      inputVars -> {
                        mapperInvocations.incrementAndGet();
                        return Collections.emptyMap();
                      }))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining(
              "Expected to complete user task [elementId: %s] but no user task is available.",
              USER_TASK_ELEMENT_ID);

      // and: mapper was never invoked
      org.assertj.core.api.Assertions.assertThat(mapperInvocations.get()).isZero();
    }

    @Test
    void shouldFailIfVariableMapperReturnsNull() {
      // given
      mockUserTaskFound();
      when(camundaClient
              .newVariableSearchRequest()
              .filter(variableFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList());

      // when/then
      assertThatThrownBy(
              () ->
                  camundaProcessTestContext.completeUserTask(
                      USER_TASK_ELEMENT_ID, inputVars -> null))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining(
              "Expected to complete user task [elementId: %s] but the variableMapper returned null.",
              USER_TASK_ELEMENT_ID);

      // and: completion command was never sent
      verify(camundaClient, org.mockito.Mockito.never()).newCompleteUserTaskCommand(USER_TASK_KEY);
    }

    @Test
    void shouldPropagateExceptionFromVariableMapper() {
      // given
      mockUserTaskFound();
      when(camundaClient
              .newVariableSearchRequest()
              .filter(variableFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList());

      // when/then
      assertThatThrownBy(
              () ->
                  camundaProcessTestContext.completeUserTask(
                      USER_TASK_ELEMENT_ID,
                      inputVars -> {
                        throw new IllegalStateException("boom");
                      }))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("boom");

      // and: completion command was never sent
      verify(camundaClient, org.mockito.Mockito.never()).newCompleteUserTaskCommand(USER_TASK_KEY);
    }

    @Test
    void shouldFailIfInputVariableIsTruncated() {
      // given
      mockUserTaskFound();
      final Variable truncated = variable("big", "\"...\"", PROCESS_INSTANCE_KEY, true);
      when(camundaClient
              .newVariableSearchRequest()
              .filter(variableFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.singletonList(truncated));

      // when/then
      assertThatThrownBy(
              () ->
                  camundaProcessTestContext.completeUserTask(
                      USER_TASK_ELEMENT_ID, inputVars -> Collections.emptyMap()))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("variable 'big' is truncated");

      // and: completion command was never sent
      verify(camundaClient, org.mockito.Mockito.never()).newCompleteUserTaskCommand(USER_TASK_KEY);
    }
  }
}
