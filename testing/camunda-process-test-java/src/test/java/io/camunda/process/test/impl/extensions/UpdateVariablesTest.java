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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.ElementSelectors;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
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
public class UpdateVariablesTest {

  private static final Long PROCESS_INSTANCE_KEY = 100L;
  private static final Long ELEMENT_INSTANCE_KEY = 200L;
  private static final String PROCESS_DEFINITION_ID = "test-process";
  private static final String ELEMENT_ID = "test-element";

  @Mock private CamundaProcessTestRuntime camundaProcessTestRuntime;
  @Mock private Consumer<AutoCloseable> clientCreationCallback;
  @Mock private CamundaManagementClient camundaManagementClient;
  @Mock private JsonMapper jsonMapper;
  @Mock private io.camunda.zeebe.client.api.JsonMapper zeebeJsonMapper;

  @Mock private CamundaClientBuilderFactory camundaClientBuilderFactory;
  @Mock private CamundaClientBuilder camundaClientBuilder;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private ProcessInstance processInstance;
  @Mock private ElementInstance elementInstance;

  @Captor private ArgumentCaptor<Consumer<ProcessInstanceFilter>> processInstanceFilterCaptor;
  @Captor private ArgumentCaptor<Consumer<ElementInstanceFilter>> elementInstanceFilterCaptor;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ProcessInstanceFilter processInstanceFilter;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ElementInstanceFilter elementInstanceFilter;

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

      when(processInstance.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
      when(processInstance.getProcessDefinitionId()).thenReturn(PROCESS_DEFINITION_ID);
    }

    @Test
    void shouldUpdateGlobalVariables() {
      // given
      final Map<String, Object> variables = Collections.singletonMap("status", "ready");

      when(camundaClient
              .newProcessInstanceSearchRequest()
              .filter(processInstanceFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.singletonList(processInstance));

      // when
      camundaProcessTestContext.updateVariables(
          ProcessInstanceSelectors.byProcessId(PROCESS_DEFINITION_ID), variables);

      // then
      verify(camundaClient.newSetVariablesCommand(PROCESS_INSTANCE_KEY).variables(variables))
          .send();
    }

    @Test
    void shouldUpdateLocalVariables() {
      // given
      final Map<String, Object> variables = Collections.singletonMap("localVar", "localValue");

      when(camundaClient
              .newProcessInstanceSearchRequest()
              .filter(processInstanceFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.singletonList(processInstance));

      when(elementInstance.getElementInstanceKey()).thenReturn(ELEMENT_INSTANCE_KEY);
      when(elementInstance.getElementId()).thenReturn(ELEMENT_ID);

      when(camundaClient
              .newElementInstanceSearchRequest()
              .filter(elementInstanceFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.singletonList(elementInstance));

      // when
      camundaProcessTestContext.updateLocalVariables(
          ProcessInstanceSelectors.byProcessId(PROCESS_DEFINITION_ID),
          ElementSelectors.byId(ELEMENT_ID),
          variables);

      // then
      verify(camundaClient.newSetVariablesCommand(ELEMENT_INSTANCE_KEY).variables(variables))
          .send();
    }

    @Test
    void shouldAwaitUntilProcessInstanceIsPresent() {
      // given
      final Map<String, Object> variables = Collections.singletonMap("status", "ready");

      when(camundaClient
              .newProcessInstanceSearchRequest()
              .filter(processInstanceFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.singletonList(processInstance));

      clearInvocations(camundaClient);

      // when
      camundaProcessTestContext.updateVariables(
          ProcessInstanceSelectors.byProcessId(PROCESS_DEFINITION_ID), variables);

      // then
      verify(camundaClient, times(2)).newProcessInstanceSearchRequest();
    }

    @Test
    void shouldRetryVariableUpdate() {
      // given
      final Map<String, Object> variables = Collections.singletonMap("status", "ready");

      when(camundaClient
              .newProcessInstanceSearchRequest()
              .filter(processInstanceFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.singletonList(processInstance));

      when(camundaClient
              .newSetVariablesCommand(PROCESS_INSTANCE_KEY)
              .variables(variables)
              .send()
              .join())
          .thenThrow(new ClientException("expected"))
          .thenReturn(null);

      clearInvocations(camundaClient);

      // when
      camundaProcessTestContext.updateVariables(
          ProcessInstanceSelectors.byProcessId(PROCESS_DEFINITION_ID), variables);

      // then
      verify(camundaClient, times(2)).newSetVariablesCommand(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldAwaitUntilElementInstanceIsPresent() {
      // given
      final Map<String, Object> variables = Collections.singletonMap("localVar", "localValue");

      when(camundaClient
              .newProcessInstanceSearchRequest()
              .filter(processInstanceFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.singletonList(processInstance));

      when(processInstance.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
      when(processInstance.getProcessDefinitionId()).thenReturn(PROCESS_DEFINITION_ID);

      when(elementInstance.getElementInstanceKey()).thenReturn(ELEMENT_INSTANCE_KEY);
      when(elementInstance.getElementId()).thenReturn(ELEMENT_ID);

      when(camundaClient
              .newElementInstanceSearchRequest()
              .filter(elementInstanceFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.singletonList(elementInstance));

      clearInvocations(camundaClient);

      // when
      camundaProcessTestContext.updateLocalVariables(
          ProcessInstanceSelectors.byProcessId(PROCESS_DEFINITION_ID),
          ElementSelectors.byId(ELEMENT_ID),
          variables);

      // then
      verify(camundaClient, times(2)).newElementInstanceSearchRequest();
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
    void shouldFailIfNoProcessInstanceIsPresent() {
      // given
      final Map<String, Object> variables = Collections.singletonMap("status", "ready");

      when(camundaClient
              .newProcessInstanceSearchRequest()
              .filter(processInstanceFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList());

      // when/then
      assertThatThrownBy(
              () ->
                  camundaProcessTestContext.updateVariables(
                      ProcessInstanceSelectors.byProcessId(PROCESS_DEFINITION_ID),
                      variables))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining(
              "Expected to update variables for process instance [process-id: '%s'] but no process instance is available.",
              PROCESS_DEFINITION_ID);
    }

    @Test
    void shouldFailIfNoElementInstanceIsPresent() {
      // given
      final Map<String, Object> variables = Collections.singletonMap("localVar", "localValue");

      when(camundaClient
              .newProcessInstanceSearchRequest()
              .filter(processInstanceFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.singletonList(processInstance));

      when(processInstance.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
      when(processInstance.getProcessDefinitionId()).thenReturn(PROCESS_DEFINITION_ID);

      when(camundaClient
              .newElementInstanceSearchRequest()
              .filter(elementInstanceFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList());

      // when/then
      assertThatThrownBy(
              () ->
                  camundaProcessTestContext.updateLocalVariables(
                      ProcessInstanceSelectors.byProcessId(PROCESS_DEFINITION_ID),
                      ElementSelectors.byId(ELEMENT_ID),
                      variables))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining(
              "Expected to update local variables for element [%s] in process instance [processInstanceKey: %s] but no element is available.",
              ELEMENT_ID,
              PROCESS_INSTANCE_KEY);
    }
  }
}
