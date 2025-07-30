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
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.ElementSelectors.byName;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
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

    // When
    processTestContext.completeUserTask(t -> t.getName().equals("User Task"));

    // Then
    assertThatProcessInstance(processInstanceEvent).isCompleted();

    withShortAwaitilityTimeouts(
        () -> {
          Assertions.assertThatThrownBy(
                  () ->
                      assertThatProcessInstance(processInstanceEvent)
                          .hasVariable("local_variable", "value"))
              .hasMessage(
                  "Process instance [key: %s] should have a variable "
                      + "'local_variable' with value '\"value\"' but the variable doesn't exist.",
                  processInstanceEvent.getProcessInstanceKey());
        });

    assertThatProcessInstance(processInstanceEvent)
        .hasLocalVariable(byName("User Task"), "local_variable", "value")
        .hasLocalVariable(byId("user-task-id"), "local_variable", "value")
        .hasLocalVariableNames(byName("User Task"), "local_variable")
        .hasLocalVariableNames("user-task-id", "local_variable");
  }

  @Test
  void shouldMatchShadowingLocalVariables() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithShadowingLocalVariables());
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // When
    processTestContext.completeJob("test");

    assertThatProcessInstance(processInstanceEvent).hasVariable("var_key", "parent_value");

    processTestContext.completeUserTask(t -> t.getName().equals("User Task"));

    // then
    assertThatProcessInstance(processInstanceEvent)
        .isCompleted()
        .hasVariable("var_key", "parent_value")
        .hasLocalVariableNames(byName("User Task"), "var_key")
        .hasLocalVariable(byName("User Task"), "var_key", "child_value")
        .hasLocalVariable("user-task-id", "var_key", "child_value");
  }

  @Test
  void unableToFindVariables() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithShadowingLocalVariables());

    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // Then
    processTestContext.completeJob("test");
    processTestContext.completeUserTask(t -> t.getName().equals("User Task"));

    withShortAwaitilityTimeouts(
        () -> {
          // Can't find key for valid element
          Assertions.assertThatThrownBy(
                  () ->
                      assertThatProcessInstance(processInstanceEvent)
                          .hasLocalVariableNames("user-task-id", "unknown_key"))
              .hasMessage(
                  "Process instance [key: %s] should have the variables ['unknown_key'] "
                      + "but ['unknown_key'] don't exist.",
                  processInstanceEvent.getProcessInstanceKey());

          // Can't find element for assertion
          Assertions.assertThatThrownBy(
                  () ->
                      assertThatProcessInstance(processInstanceEvent)
                          .hasLocalVariableNames("unknown-task-id", "unknown_key"))
              .hasMessage(
                  "No element [unknown-task-id] found for process instance [key: %s]",
                  processInstanceEvent.getProcessInstanceKey());
        });
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

    // Can find local variables while the process is running
    assertThatProcessInstance(processInstanceEvent)
        .isActive()
        .hasVariable("parent_key", "parent_value")
        .hasVariable("shadowed_variable", "A")
        .hasLocalVariable("user-task-parent-id", "local_variable", "parent_ut_value")
        .hasLocalVariable(byName("Parent User Task"), "local_variable", "parent_ut_value");

    withShortAwaitilityTimeouts(
        () ->
            // Local variables continue to not be in the process instance scope
            Assertions.assertThatThrownBy(
                    () ->
                        assertThatProcessInstance(processInstanceEvent)
                            .hasVariableNames("local_variable"))
                .hasMessage(
                    "Process instance [key: %s] should have the variables "
                        + "['local_variable'] but ['local_variable'] don't exist.",
                    processInstanceEvent.getProcessInstanceKey()));

    // Entering the child process
    processTestContext.completeUserTask(t -> t.getName().equals("Parent User Task"));

    final Map<String, Object> expectedVariables = new HashMap<String, Object>();
    expectedVariables.put("child_local_variable", "child_value");
    expectedVariables.put("shadowed_variable", "B");

    assertThatProcessInstance(ProcessInstanceSelectors.byProcessId("child-process-1"))
        .isActive()
        .hasVariable("parent_key", "parent_value")
        .hasVariable("shadowed_variable", "A")
        .hasLocalVariableNames("user-task-child-id", "child_local_variable", "shadowed_variable")
        .hasLocalVariableNames(
            byName("Child User Task"), "child_local_variable", "shadowed_variable")
        .hasLocalVariables("user-task-child-id", expectedVariables)
        .hasLocalVariables(byId("user-task-child-id"), expectedVariables);

    withShortAwaitilityTimeouts(
        () ->
            Assertions.assertThatThrownBy(
                    () ->
                        assertThatProcessInstance(processInstanceEvent)
                            .hasVariable("shadowed_variable", "B"))
                .hasMessage(
                    "Process instance [key: %s] should have a variable "
                        + "'shadowed_variable' with value '\"B\"' but was '\"A\"'.",
                    processInstanceEvent.getProcessInstanceKey()));

    // Exit the child process
    processTestContext.completeUserTask(t -> t.getName().equalsIgnoreCase("Child User Task"));

    assertThatProcessInstance(processInstanceEvent)
        .isCompleted()
        .hasVariable("parent_key", "parent_value")
        .hasVariable("shadowed_variable", "B")
        .hasVariable("child_key", "child_value");

    withShortAwaitilityTimeouts(
        () ->
            Assertions.assertThatThrownBy(
                    () ->
                        assertThatProcessInstance(processInstanceEvent)
                            .hasVariableNames("child_local_variable"))
                .hasMessage(
                    "Process instance [key: %s] should have the variables "
                        + "['child_local_variable'] but ['child_local_variable'] don't exist.",
                    processInstanceEvent.getProcessInstanceKey()));

    // Then
    // Can assert local variables of child process after process is finished
    assertThatProcessInstance(ProcessInstanceSelectors.byProcessId("child-process-1"))
        .isCompleted()
        .hasLocalVariableNames(
            byName("Child User Task"), "child_local_variable", "shadowed_variable")
        .hasLocalVariableNames(
            byId("user-task-child-id"), "child_local_variable", "shadowed_variable")
        .hasLocalVariables(byName("Child User Task"), expectedVariables)
        .hasLocalVariables("user-task-child-id", expectedVariables);
  }

  private void withShortAwaitilityTimeouts(final Runnable assertionFn) {
    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(5));

    assertionFn.run();

    CamundaAssert.setAssertionTimeout(CamundaAssert.DEFAULT_ASSERTION_TIMEOUT);
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
        .startEvent("start-event-id")
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

  private BpmnModelInstance processModelWithShadowingLocalVariables() {
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
