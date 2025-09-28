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

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
public class CamundaProcessTestCompletionApiIT {

  // to be injected
  private CamundaProcessTestContext processTestContext;
  private CamundaClient client;

  @Test
  void shouldCompleteJob() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithServiceTask());
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // When
    processTestContext.completeJob("test");

    // Then
    assertThatProcessInstance(processInstanceEvent).isCompleted();
    assertThatProcessInstance(processInstanceEvent).hasCompletedElements("success-end");
  }

  @Test
  void shouldCompleteJobWithVariables() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithServiceTask());
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();
    final Map<String, Object> variables = new HashMap<>();
    variables.put("abc", 123);

    // When
    processTestContext.completeJob("test", variables);

    // Then
    assertThatProcessInstance(processInstanceEvent).isCompleted();
    assertThatProcessInstance(processInstanceEvent).hasCompletedElements("success-end");
    assertThatProcessInstance(processInstanceEvent).hasVariables(variables);
  }

  @Test
  void shouldCompleteJobWithExampleData() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithExampleData());

    // When
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    processTestContext.completeJobWithExampleData("email");

    // Then
    final Map<String, Object> expectedVariables = new HashMap<>();
    expectedVariables.put("send_status", 200);

    assertThatProcessInstance(processInstanceEvent).isCompleted();
    assertThatProcessInstance(processInstanceEvent).hasVariables(expectedVariables);
  }

  @Test
  void shouldCompleteJobWithParallelTasksThatMayOrMayNotHaveExampleData() {
    // Given
    final long processDefinitionKey =
        deployProcessModel(processModelWithMultipleExampleDataCases());

    // When
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    processTestContext.completeUserTaskWithExampleData("user_task_write_email");
    processTestContext.completeJobWithExampleData("email_review");
    processTestContext.completeJobWithExampleData("email_review");
    processTestContext.completeJobWithExampleData("email_review");
    processTestContext.completeUserTaskWithExampleData("user_task_print_email");

    // Then
    final Map<String, Object> expectedVariables = new HashMap<>();
    expectedVariables.put("email_output", "Lorem ipsum");
    expectedVariables.put("printed_copies", 10);
    expectedVariables.put("email_quality", "A-");

    assertThatProcessInstance(processInstanceEvent).isCompleted();
    assertThatProcessInstance(processInstanceEvent).hasVariables(expectedVariables);
  }

  @Test
  void shouldThrowBpmnErrorFromJob() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithServiceTask());
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // When
    processTestContext.throwBpmnErrorFromJob("test", "bpmn-error");

    // Then
    assertThatProcessInstance(processInstanceEvent).isCompleted();
    assertThatProcessInstance(processInstanceEvent).hasCompletedElements("error-end");
  }

  @Test
  void shouldThrowBpmnErrorFromJobWithVariables() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithServiceTask());
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();
    final Map<String, Object> variables = new HashMap<>();
    variables.put("abc", 123);

    // When
    processTestContext.throwBpmnErrorFromJob("test", "bpmn-error", variables);

    // Then
    assertThatProcessInstance(processInstanceEvent).isCompleted();
    assertThatProcessInstance(processInstanceEvent).hasCompletedElements("error-end");

    final Map<String, Object> expectedVariables = new HashMap<>();
    expectedVariables.put("error_code", 123);
    assertThatProcessInstance(processInstanceEvent).hasVariables(expectedVariables);
  }

  @Test
  void shouldCompleteUserTask() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithUserTask());
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // When
    processTestContext.completeUserTask("user-task-1");

    // Then
    assertThatProcessInstance(processInstanceEvent).isCompleted();
    assertThatProcessInstance(processInstanceEvent).hasCompletedElements("success-end");
  }

  @Test
  void shouldCompleteUserTaskWithVariables() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithUserTask());
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();
    final Map<String, Object> variables = new HashMap<>();
    variables.put("abc", 123);

    // When
    processTestContext.completeUserTask("user-task-1", variables);

    // Then
    assertThatProcessInstance(processInstanceEvent).isCompleted();
    assertThatProcessInstance(processInstanceEvent).hasCompletedElements("success-end");
    assertThatProcessInstance(processInstanceEvent).hasVariables(variables);
  }

  @Test
  void shouldCompleteUserTaskWithExampleData() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithUserTaskAndExampleData());
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();
    final Map<String, Object> variables = new HashMap<>();
    variables.put("email_output", "Lorem ipsum");

    // When
    processTestContext.completeUserTaskWithExampleData("user_task_write_email");

    // Then
    assertThatProcessInstance(processInstanceEvent).isCompleted();
    assertThatProcessInstance(processInstanceEvent).hasVariables(variables);
  }

  @Test
  void shouldCompleteUserTaskByTaskName() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithUserTask());
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // When
    processTestContext.completeUserTask(UserTaskSelectors.byTaskName("user-task"));

    // Then
    assertThatProcessInstance(processInstanceEvent).isCompleted();
    assertThatProcessInstance(processInstanceEvent).hasCompletedElements("success-end");
  }

  @Test
  void shouldCompleteUserTaskByTaskNameWithVariable() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithUserTask());
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();
    final Map<String, Object> variables = new HashMap<>();
    variables.put("abc", 123);

    // When
    processTestContext.completeUserTask(UserTaskSelectors.byTaskName("user-task"), variables);

    // Then
    assertThatProcessInstance(processInstanceEvent).isCompleted();
    assertThatProcessInstance(processInstanceEvent).hasCompletedElements("success-end");
    assertThatProcessInstance(processInstanceEvent).hasVariables(variables);
  }

  @Test
  void shouldCompleteUserTaskByElementId() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithUserTask());
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // When
    processTestContext.completeUserTask(UserTaskSelectors.byElementId("user-task-1"));

    // Then
    assertThatProcessInstance(processInstanceEvent).isCompleted();
    assertThatProcessInstance(processInstanceEvent).hasCompletedElements("success-end");
  }

  @Test
  void shouldCompleteUserTaskByElementIdWithVariables() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithUserTask());
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();
    final Map<String, Object> variables = new HashMap<>();
    variables.put("abc", 123);

    // When
    processTestContext.completeUserTask(UserTaskSelectors.byElementId("user-task-1"), variables);

    // Then
    assertThatProcessInstance(processInstanceEvent).isCompleted();
    assertThatProcessInstance(processInstanceEvent).hasCompletedElements("success-end");
    assertThatProcessInstance(processInstanceEvent).hasVariables(variables);
  }

  @Test
  void shouldCompleteUserTaskBySelector() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithUserTask());
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // When
    processTestContext.completeUserTask(t -> t.getName().equals("user-task"));

    // Then
    assertThatProcessInstance(processInstanceEvent).isCompleted();
    assertThatProcessInstance(processInstanceEvent).hasCompletedElements("success-end");
  }

  @Test
  void shouldCompleteUserTaskBySelectorWithVariables() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithUserTask());
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();
    final Map<String, Object> variables = new HashMap<>();
    variables.put("abc", 123);

    // When
    processTestContext.completeUserTask(t -> t.getName().equals("user-task"), variables);

    // Then
    assertThatProcessInstance(processInstanceEvent).isCompleted();
    assertThatProcessInstance(processInstanceEvent).hasCompletedElements("success-end");
    assertThatProcessInstance(processInstanceEvent).hasVariables(variables);
  }

  @Test
  void shouldGiveHelpfulErrorMessageWhenCompleteUserTaskFails() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithUserTask());
    client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // When
    Assertions.assertThatThrownBy(
            () ->
                processTestContext.completeUserTask(UserTaskSelectors.byElementId("unknown-task")))
        .hasMessage(
            "Expected to complete user task [elementId: unknown-task] but no user task is available.");
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

  private BpmnModelInstance processModelWithExampleData() {
    return Bpmn.createExecutableProcess("process")
        .startEvent()
        .serviceTask(
            "send-email",
            t ->
                t.zeebeJobType("email")
                    .zeebeProperty("camundaModeler:exampleOutputJson", "{\"send_status\": 200}"))
        .endEvent()
        .done();
  }

  private BpmnModelInstance processModelWithUserTaskAndExampleData() {
    return Bpmn.createExecutableProcess("process")
        .startEvent()
        .userTask(
            "user_task_write_email",
            ut ->
                ut.zeebeUserTask()
                    .zeebeProperty(
                        "camundaModeler:exampleOutputJson", "{\"email_output\": \"Lorem ipsum\"}"))
        .endEvent()
        .done();
  }

  private BpmnModelInstance processModelWithMultipleExampleDataCases() {
    return Bpmn.createExecutableProcess("process")
        .startEvent()
        .userTask(
            "user_task_write_email",
            ut ->
                ut.zeebeUserTask()
                    .zeebeProperty(
                        "camundaModeler:exampleOutputJson", "{\"content\": \"Lorem ipsum\"}")
                    .zeebeOutput("=content", "email_output"))
        .parallelGateway()
        .serviceTask(
            "service_task_review_email_a",
            t -> t.zeebeJobType("email_review").zeebeOutput("=reviewer.grade", "email_quality"))
        .serviceTask(
            "service_task_review_email_b",
            t ->
                t.zeebeJobType("email_review")
                    .zeebeProperty("camundaModeler:exampleOutputJson", "")
                    .zeebeOutput("=reviewer.grade", "email_quality"))
        .serviceTask(
            "service_task_review_email_c",
            t ->
                t.zeebeJobType("email_review")
                    .zeebeProperty(
                        "camundaModeler:exampleOutputJson",
                        "{\"reviewer\":{\"name\":\"Josh\",\"grade\":\"A-\"}}")
                    .zeebeOutput("=reviewer.grade", "email_quality"))
        .parallelGateway()
        .userTask(
            "user_task_print_email",
            ut ->
                ut.zeebeUserTask()
                    .zeebeProperty("camundaModeler:exampleOutputJson", "{\"copies\": 10}")
                    .zeebeOutput("=copies", "printed_copies"))
        .endEvent()
        .done();
  }

  private BpmnModelInstance processModelWithServiceTask() {
    return Bpmn.createExecutableProcess("test-process-with-service-task")
        .startEvent("start-1")
        .serviceTask("service-task-1")
        .zeebeJobType("test")
        .boundaryEvent("error-boundary-event")
        .error("bpmn-error")
        .zeebeOutputExpression("abc", "error_code")
        .endEvent("error-end")
        .moveToActivity("service-task-1")
        .endEvent("success-end")
        .done();
  }

  private BpmnModelInstance processModelWithUserTask() {
    return processModelWithUserTask("user-task", "user-task-1");
  }

  private BpmnModelInstance processModelWithUserTask(
      final String taskName, final String elementId) {
    return Bpmn.createExecutableProcess("test-process-with-user-task")
        .startEvent("start-1")
        .userTask(elementId)
        .name(taskName)
        .zeebeUserTask()
        .boundaryEvent("error-boundary-event")
        .error("bpmn-error")
        .endEvent("error-end")
        .moveToActivity("user-task-1")
        .endEvent("success-end")
        .done();
  }
}
