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
import io.camunda.client.api.search.response.UserTask;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import io.camunda.process.test.impl.extension.CamundaProcessTestContextImpl;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntime;
import io.camunda.process.test.utils.DevAwaitBehavior;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
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
  @Mock private CamundaManagementClient camundaManagementClient;
  @Mock private JsonMapper jsonMapper;
  @Mock private io.camunda.zeebe.client.api.JsonMapper zeebeJsonMapper;

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
              camundaManagementClient,
              DevAwaitBehavior.expectSuccess(),
              jsonMapper,
              zeebeJsonMapper);

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
              camundaManagementClient,
              DevAwaitBehavior.expectFailure(),
              jsonMapper,
              zeebeJsonMapper);
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
}
