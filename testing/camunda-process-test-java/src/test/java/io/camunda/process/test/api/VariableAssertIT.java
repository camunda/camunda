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

import static io.camunda.process.test.api.CamundaAssert.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
public class VariableAssertIT {

  private CamundaProcessTestContext processTestContext;
  private CamundaClient client;

  @Test
  void shouldMatchLocalVariables() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithLocalVariables());

    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // Then
    assertThat(processInstanceEvent).hasLocalVariable("local_variable", "value");

    processTestContext.completeUserTask(t -> t.getName().equals("User Task"));

    assertThat(processInstanceEvent).isCompleted();

    Assertions.assertThatThrownBy(
            () -> assertThat(processInstanceEvent).hasVariable("local_variable", "value"))
        .hasMessage(
            "Process instance [key: 2251799813685294] should have a variable "
                + "'local_variable' with value '\"value\"' but the variable doesn't exist.");
  }

  @Test
  void shouldMatchShadowingLocalVariables() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithLocalVariablesB());

    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // Then
    processTestContext.completeJob("test");

    assertThat(processInstanceEvent).hasVariable("var_key", "parent_value");
    assertThat(processInstanceEvent).hasLocalVariable("var_key", "child_value");

    processTestContext.completeUserTask(t -> t.getName().equals("User Task"));

    assertThat(processInstanceEvent).isCompleted().hasVariable("var_key", "parent_value");
  }

  @Test
  void shouldMatchShadowingLocalVariablesInSubProcess() {
    // Given
    final long subprocessDefinitionKey = deployProcessModel(childProcessModelWithLocalVariables());
    final long processDefinitionKey =
        deployProcessModel(processModelWithLocalVariablesAndChildProcess());

    // When
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // Then
    assertThat(processInstanceEvent)
        .isActive()
        .hasVariable("parent_key", "parent_value")
        .hasVariable("shadowed_variable", "A")
        .hasLocalVariable("local_variable", "parent_ut_value");

    Assertions.assertThatThrownBy(
            () -> assertThat(processInstanceEvent).hasVariableNames("local_variable"))
        .hasMessage(
            "Process instance [key: 2251799813685296] should have the variables "
                + "['local_variable'] but ['local_variable'] don't exist.");

    processTestContext.completeUserTask(t -> t.getName().equals("Parent User Task"));

    assertThat(ProcessInstanceSelectors.byProcessId("child-process-1"))
        .isActive()
        .hasVariable("parent_key", "parent_value")
        .hasVariable("shadowed_variable", "A")
        .hasLocalVariable("child_local_variable", "child_value")
        .hasLocalVariable("shadowed_variable", "B");

    Assertions.assertThatThrownBy(
            () -> assertThat(processInstanceEvent).hasVariable("shadowed_variable", "B"))
        .hasMessage(
            "Process instance [key: 2251799813685296] should have a variable "
                + "'shadowed_variable' with value '\"B\"' but was '\"A\"'.");

    processTestContext.completeUserTask(t -> t.getName().equalsIgnoreCase("Child User Task"));

    assertThat(processInstanceEvent)
        .isCompleted()
        .hasVariable("parent_key", "parent_value")
        .hasVariable("shadowed_variable", "B")
        .hasVariable("child_key", "child_value");

    Assertions.assertThatThrownBy(
            () -> assertThat(processInstanceEvent).hasVariableNames("child_local_variable"))
        .hasMessage(
            "Process instance [key: 2251799813685296] should have the variables "
                + "['child_local_variable'] but ['child_local_variable'] don't exist.");
  }

  /**
   * Deploys a process model and waits until it is accessible via the API.
   *
   * @return the process definition key
   */
  private long deployProcessModel(final BpmnModelInstance processModel) {
    final DeploymentEvent deploymentEvent =
        client
            .newDeployResourceCommand()
            .addProcessModel(processModel, "test-process.bpmn")
            .send()
            .join();
    return deploymentEvent.getProcesses().stream().findFirst().get().getProcessDefinitionKey();
  }

  private BpmnModelInstance processModelWithLocalVariablesAndChildProcess() {
    return Bpmn.createExecutableProcess("test-process")
        .startEvent()
        .zeebeOutputExpression("\"parent_value\"", "parent_key")
        .zeebeOutputExpression("\"A\"", "shadowed_variable")
        .userTask("user-task-parent-id")
        .name("Parent User Task")
        .zeebeInputExpression("\"parent_ut_value\"", "local_variable")
        .zeebeUserTask()
        .callActivity()
        .zeebeProcessId("child-process-1")
        .endEvent("success-end")
        .done();
  }

  private BpmnModelInstance childProcessModelWithLocalVariables() {
    return Bpmn.createExecutableProcess("child-process-1")
        .startEvent()
        .userTask("user-task-child-id")
        .name("Child User Task")
        .zeebeInputExpression("\"child_value\"", "child_local_variable")
        .zeebeInputExpression("\"B\"", "shadowed_variable")
        .zeebeOutputExpression("\"child_value\"", "child_key")
        .zeebeOutputExpression("\"B\"", "shadowed_variable")
        .zeebeUserTask()
        .endEvent("child-end")
        .done();
  }

  private BpmnModelInstance processModelWithLocalVariables() {
    return Bpmn.createExecutableProcess("test-process-local-variables")
        .startEvent()
        .userTask("user-task-id")
        .name("User Task")
        .zeebeInputExpression("\"value\"", "local_variable")
        .zeebeUserTask()
        .endEvent("success-end")
        .done();
  }

  private BpmnModelInstance processModelWithLocalVariablesB() {
    return Bpmn.createExecutableProcess("test-process-local-variables")
        .startEvent()
        .serviceTask("service-task-id")
        .zeebeJobType("test")
        .zeebeOutputExpression("\"parent_value\"", "var_key")
        .userTask("user-task-id")
        .name("User Task")
        .zeebeInputExpression("\"child_value\"", "var_key")
        .zeebeUserTask()
        .endEvent("success-end")
        .done();
  }
}
