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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.client.CamundaClockClient;
import io.camunda.process.test.impl.extension.CamundaProcessTestContextImpl;
import io.camunda.process.test.impl.extension.ConditionalBehaviorEngine;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntime;
import io.camunda.process.test.utils.DevAwaitBehavior;
import java.util.Collections;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TriggerTimerTest {

  private static final Long PROCESS_INSTANCE_KEY = 100L;
  private static final String PROCESS_DEFINITION_ID = "test-process";
  private static final String ELEMENT_ID = "escalation";

  @Mock private CamundaProcessTestRuntime camundaProcessTestRuntime;
  @Mock private Consumer<AutoCloseable> clientCreationCallback;
  @Mock private CamundaClockClient clockClient;
  @Mock private JsonMapper jsonMapper;

  @Mock private CamundaClientBuilderFactory camundaClientBuilderFactory;
  @Mock private CamundaClientBuilder camundaClientBuilder;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private ProcessInstance processInstance;

  @Captor private ArgumentCaptor<Consumer<ProcessInstanceFilter>> processInstanceFilterCaptor;

  private CamundaProcessTestContext camundaProcessTestContext;

  @BeforeEach
  void configureMocks() {
    when(camundaProcessTestRuntime.getCamundaClientBuilderFactory())
        .thenReturn(camundaClientBuilderFactory);
    when(camundaClientBuilderFactory.get()).thenReturn(camundaClientBuilder);
    when(camundaClientBuilder.build()).thenReturn(camundaClient);
  }

  private void createContext(final DevAwaitBehavior awaitBehavior) {
    camundaProcessTestContext =
        new CamundaProcessTestContextImpl(
            camundaProcessTestRuntime,
            clientCreationCallback,
            clockClient,
            () -> awaitBehavior,
            jsonMapper,
            new ConditionalBehaviorEngine(),
            () -> new CamundaDataSource(camundaClient));
  }

  @Test
  void shouldTriggerHeldTimerOfTheSelectedProcessInstance() {
    // given
    createContext(DevAwaitBehavior.expectSuccess());
    when(processInstance.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
    when(processInstance.getProcessDefinitionId()).thenReturn(PROCESS_DEFINITION_ID);

    when(camundaClient
            .newProcessInstanceSearchRequest()
            .filter(processInstanceFilterCaptor.capture())
            .sort(any(Consumer.class))
            .page(any(Consumer.class))
            .send()
            .join()
            .items())
        .thenReturn(Collections.singletonList(processInstance));

    // when
    camundaProcessTestContext.triggerTimer(
        ProcessInstanceSelectors.byProcessId(PROCESS_DEFINITION_ID), ELEMENT_ID);

    // then
    verify(camundaClient.newTriggerTimerCommand(PROCESS_INSTANCE_KEY).elementId(ELEMENT_ID)).send();
  }

  @Test
  void shouldTriggerHeldTimerOfTheGivenProcessInstance() {
    // given
    createContext(DevAwaitBehavior.expectSuccess());

    // when
    camundaProcessTestContext.triggerTimer(PROCESS_INSTANCE_KEY, ELEMENT_ID);

    // then
    verify(camundaClient.newTriggerTimerCommand(PROCESS_INSTANCE_KEY).elementId(ELEMENT_ID)).send();
    verify(camundaClient, never()).newProcessInstanceSearchRequest();
  }

  @Test
  void shouldFailIfNoProcessInstanceIsPresent() {
    // given
    createContext(DevAwaitBehavior.expectFailure());

    when(camundaClient
            .newProcessInstanceSearchRequest()
            .filter(processInstanceFilterCaptor.capture())
            .sort(any(Consumer.class))
            .page(any(Consumer.class))
            .send()
            .join()
            .items())
        .thenReturn(Collections.emptyList());

    // when/then
    assertThatThrownBy(
            () ->
                camundaProcessTestContext.triggerTimer(
                    ProcessInstanceSelectors.byProcessId(PROCESS_DEFINITION_ID), ELEMENT_ID))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(
            "Expected to act on process instance [process-id: '%s'] but no process instance is available.",
            PROCESS_DEFINITION_ID);
  }
}
