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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.JsonMapper;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.impl.client.CamundaClockClient;
import io.camunda.process.test.impl.extension.CamundaProcessTestContextImpl;
import io.camunda.process.test.impl.extension.ConditionalBehaviorEngine;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntime;
import io.camunda.process.test.utils.DevAwaitBehavior;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeOutput;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MockChildProcessTest {

  private static final String CHILD_PROCESS_ID = "child-process-1";

  @Mock private CamundaProcessTestRuntime camundaProcessTestRuntime;
  @Mock private Consumer<AutoCloseable> clientCreationCallback;
  @Mock private CamundaClockClient clockClient;
  @Mock private JsonMapper jsonMapper;
  @Mock private CamundaClientBuilderFactory camundaClientBuilderFactory;
  @Mock private CamundaClientBuilder camundaClientBuilder;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Captor private ArgumentCaptor<BpmnModelInstance> processModelCaptor;

  private CamundaProcessTestContext processTestContext;

  @BeforeEach
  void configureMocks() {
    when(camundaProcessTestRuntime.getCamundaClientBuilderFactory())
        .thenReturn(camundaClientBuilderFactory);
    when(camundaClientBuilderFactory.get()).thenReturn(camundaClientBuilder);
    when(camundaClientBuilder.build()).thenReturn(camundaClient);

    processTestContext =
        new CamundaProcessTestContextImpl(
            camundaProcessTestRuntime,
            clientCreationCallback,
            clockClient,
            DevAwaitBehavior::expectSuccess,
            jsonMapper,
            new ConditionalBehaviorEngine());
  }

  @Test
  void shouldMockChildProcess() {
    // when
    processTestContext.mockChildProcess(CHILD_PROCESS_ID);

    // then: a simple start → end process is deployed with the child process ID
    verify(camundaClient.newDeployResourceCommand())
        .addProcessModel(processModelCaptor.capture(), eq(CHILD_PROCESS_ID + ".bpmn"));

    final BpmnModelInstance deployedModel = processModelCaptor.getValue();

    // the process has the correct ID
    assertThat(deployedModel.getModelElementsByType(Process.class))
        .hasSize(1)
        .first()
        .satisfies(process -> assertThat(process.getId()).isEqualTo(CHILD_PROCESS_ID));

    // the process has no service tasks (it's a simple start → end)
    assertThat(deployedModel.getModelElementsByType(ServiceTask.class)).isEmpty();
  }

  @Test
  void shouldMockChildProcessWithVariables() {
    // given
    final Map<String, Object> variables = Collections.singletonMap("result", "ok");
    when(camundaClient.getConfiguration().getJsonMapper().toJson("ok")).thenReturn("\"ok\"");

    // when
    processTestContext.mockChildProcess(CHILD_PROCESS_ID, variables);

    // then: a start → end process with output variables is deployed
    verify(camundaClient.newDeployResourceCommand())
        .addProcessModel(processModelCaptor.capture(), eq(CHILD_PROCESS_ID + ".bpmn"));

    final BpmnModelInstance deployedModel = processModelCaptor.getValue();

    // the process has the correct ID
    assertThat(deployedModel.getModelElementsByType(Process.class))
        .hasSize(1)
        .first()
        .satisfies(process -> assertThat(process.getId()).isEqualTo(CHILD_PROCESS_ID));

    // the process has no service tasks (variables are set as end event outputs)
    assertThat(deployedModel.getModelElementsByType(ServiceTask.class)).isEmpty();

    // the end event has output mappings for each variable
    final EndEvent endEvent = deployedModel.getModelElementById("child-end");
    assertThat(endEvent).isNotNull();
    final ZeebeIoMapping ioMapping = endEvent.getSingleExtensionElement(ZeebeIoMapping.class);
    assertThat(ioMapping).isNotNull();
    final Collection<ZeebeOutput> outputs = ioMapping.getOutputs();
    assertThat(outputs)
        .hasSize(1)
        .first()
        .satisfies(
            output -> {
              assertThat(output.getSource()).isEqualTo("=\"ok\"");
              assertThat(output.getTarget()).isEqualTo("result");
            });
  }

  @Test
  void shouldMockChildProcessWithVariableSupplier() {
    // given
    final Function<Map<String, Object>, Map<String, Object>> variableSupplier =
        inputVars -> Collections.singletonMap("result", inputVars.getOrDefault("input", "default"));

    // when
    processTestContext.mockChildProcess(CHILD_PROCESS_ID, variableSupplier);

    // then: a process with a service task for the variable supplier is deployed
    verify(camundaClient.newDeployResourceCommand())
        .addProcessModel(processModelCaptor.capture(), eq(CHILD_PROCESS_ID + ".bpmn"));

    final BpmnModelInstance deployedModel = processModelCaptor.getValue();

    // the process has the correct ID
    assertThat(deployedModel.getModelElementsByType(Process.class))
        .hasSize(1)
        .first()
        .satisfies(process -> assertThat(process.getId()).isEqualTo(CHILD_PROCESS_ID));

    // the process has a service task used to supply variables
    assertThat(deployedModel.getModelElementsByType(ServiceTask.class))
        .hasSize(1)
        .first()
        .satisfies(
            serviceTask ->
                assertThat(
                        serviceTask.getSingleExtensionElement(ZeebeTaskDefinition.class).getType())
                    .isEqualTo("variableSupplier_" + CHILD_PROCESS_ID));

    // and the worker for the variable supplier is opened
    verify(camundaClient.newWorker().jobType("variableSupplier_" + CHILD_PROCESS_ID).handler(any()))
        .open();
  }
}
