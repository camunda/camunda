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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
public class CamundaProcessTestContextIT {
  private static final int TIMEOUT = 40;
  private CamundaProcessTestContext processTestContext;
  private CamundaClient client;

  @Test
  void shouldThrowBpmnErrorWithoutVariables() {
    // Given
    processTestContext.mockJobWorker("test").thenThrowBpmnError("bpmn-error");
    final long processDefinitionKey = deployProcessModel(processModelWithServiceTask());

    // When
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // Then
    CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("error-end");
  }

  @Test
  void shouldThrowBpmnErrorWithVariables() {
    // Given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("abc", 123);
    processTestContext.mockJobWorker("test").thenThrowBpmnError("bpmn-error", variables);
    final long processDefinitionKey = deployProcessModel(processModelWithServiceTask());

    // When
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // Then
    CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("error-end");
    CamundaAssert.assertThat(processInstanceEvent).hasVariables(variables);
  }

  @Test
  void shouldMockJobWorkerWithoutVariables() {
    // Given
    processTestContext.mockJobWorker("test").thenComplete();
    final long processDefinitionKey = deployProcessModel(processModelWithServiceTask());

    // When
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // Then
    CamundaAssert.assertThat(processInstanceEvent).isCompleted();
    CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("success-end");
  }

  @Test
  void shouldMockJobWorkerWithVariables() {
    // Given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("abc", 123);
    processTestContext.mockJobWorker("test").thenComplete(variables);
    final long processDefinitionKey = deployProcessModel(processModelWithServiceTask());

    // When
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // Then
    CamundaAssert.assertThat(processInstanceEvent).isCompleted();
    CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("success-end");
    CamundaAssert.assertThat(processInstanceEvent).hasVariables(variables);
  }

  @Test
  void shouldMockJobWorkerWithJobHandlerBpmnError() {
    // Given
    processTestContext
        .mockJobWorker("test")
        .withHandler(
            (jobClient, job) -> {
              jobClient.newThrowErrorCommand(job).errorCode("bpmn-error").send().join();
            });
    final long processDefinitionKey = deployProcessModel(processModelWithServiceTask());

    // When
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // Then
    CamundaAssert.assertThat(processInstanceEvent).isCompleted();
    CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("error-end");
  }

  @Test
  void shouldMockJobWorkerWithJobHandlerSuccess() {
    // Given
    processTestContext
        .mockJobWorker("test")
        .withHandler(
            (jobClient, job) -> {
              jobClient.newCompleteCommand(job).send().join();
            });
    final long processDefinitionKey = deployProcessModel(processModelWithServiceTask());

    // When
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // Then
    CamundaAssert.assertThat(processInstanceEvent).isCompleted();
    CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("success-end");
  }

  @Test
  void shouldCompleteJob() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithServiceTask());
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // When
    processTestContext.completeJob("test");

    // Then
    CamundaAssert.assertThat(processInstanceEvent).isCompleted();
    CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("success-end");
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
    CamundaAssert.assertThat(processInstanceEvent).isCompleted();
    CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("success-end");
    CamundaAssert.assertThat(processInstanceEvent).hasVariables(variables);
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
    CamundaAssert.assertThat(processInstanceEvent).isCompleted();
    CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("error-end");
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
    CamundaAssert.assertThat(processInstanceEvent).isCompleted();
    CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("error-end");
    CamundaAssert.assertThat(processInstanceEvent).hasVariables(variables);
  }

  @Test
  void shouldCompleteUserTask() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithUserTask());
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // When
    processTestContext.completeUserTask("user-task");

    // Then
    CamundaAssert.assertThat(processInstanceEvent).isCompleted();
    CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("success-end");
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
    processTestContext.completeUserTask("user-task", variables);

    // Then
    CamundaAssert.assertThat(processInstanceEvent).isCompleted();
    CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("success-end");
    CamundaAssert.assertThat(processInstanceEvent).hasVariables(variables);
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
    CamundaAssert.assertThat(processInstanceEvent).isCompleted();
    CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("success-end");
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
    CamundaAssert.assertThat(processInstanceEvent).isCompleted();
    CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("success-end");
    CamundaAssert.assertThat(processInstanceEvent).hasVariables(variables);
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
    CamundaAssert.assertThat(processInstanceEvent).isCompleted();
    CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("success-end");
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
    CamundaAssert.assertThat(processInstanceEvent).isCompleted();
    CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("success-end");
    CamundaAssert.assertThat(processInstanceEvent).hasVariables(variables);
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
    CamundaAssert.assertThat(processInstanceEvent).isCompleted();
    CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("success-end");
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
    CamundaAssert.assertThat(processInstanceEvent).isCompleted();
    CamundaAssert.assertThat(processInstanceEvent).hasCompletedElements("success-end");
    CamundaAssert.assertThat(processInstanceEvent).hasVariables(variables);
  }

  @Test
  void shouldFindUserTaskByElementId() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithUserTask());

    client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // Then
    CamundaAssert.assertThat(UserTaskSelectors.byElementId("user-task-1")).isCreated();
  }

  @Test
  void shouldFindUserTaskByElementIdAndProcessDefinitionKey() {
    // Given
    final long firstInstanceKey = deployProcessModel(processModelWithUserTask());
    final long secondInstanceKey = deployProcessModel(processModelWithUserTask());
    final long thirdInstanceKey = deployProcessModel(processModelWithUserTask());

    client.newCreateInstanceCommand().processDefinitionKey(firstInstanceKey).send().join();
    client.newCreateInstanceCommand().processDefinitionKey(secondInstanceKey).send().join();
    client.newCreateInstanceCommand().processDefinitionKey(thirdInstanceKey).send().join();

    // Then
    CamundaAssert.assertThat(UserTaskSelectors.byElementId("user-task-1", secondInstanceKey))
        .hasProcessInstanceKey(secondInstanceKey);
  }

  @Test
  void shouldFindUserTaskByTaskName() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithUserTask());

    client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // Then
    CamundaAssert.assertThat(UserTaskSelectors.byTaskName("User Task")).isCreated();
  }

  @Test
  void shouldFindUserTaskByTaskNameAndProcessDefinitionKey() {
    // Given
    final long firstInstanceKey = deployProcessModel(processModelWithUserTask());
    final long secondInstanceKey = deployProcessModel(processModelWithUserTask());
    final long thirdInstanceKey = deployProcessModel(processModelWithUserTask());

    client.newCreateInstanceCommand().processDefinitionKey(firstInstanceKey).send().join();
    client.newCreateInstanceCommand().processDefinitionKey(secondInstanceKey).send().join();
    client.newCreateInstanceCommand().processDefinitionKey(thirdInstanceKey).send().join();

    // Then
    CamundaAssert.assertThat(UserTaskSelectors.byTaskName("User Task", secondInstanceKey))
        .hasProcessInstanceKey(secondInstanceKey);
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

  private BpmnModelInstance processModelWithServiceTask() {
    return Bpmn.createExecutableProcess("test-process")
        .startEvent()
        .serviceTask("service-task-1")
        .zeebeJobType("test")
        .boundaryEvent("error-boundary-event")
        .error("bpmn-error")
        .endEvent("error-end")
        .moveToActivity("service-task-1")
        .endEvent("success-end")
        .done();
  }

  private BpmnModelInstance processModelWithUserTask() {
    return processModelWithUserTask("User Task", "user-task-1");
  }

  private BpmnModelInstance processModelWithUserTask(
      final String taskName, final String elementId) {
    return Bpmn.createExecutableProcess("test-process")
        .startEvent()
        .userTask(elementId)
        .name(taskName)
        .zeebeUserTask()
        .name("user-task")
        .boundaryEvent("error-boundary-event")
        .error("bpmn-error")
        .endEvent("error-end")
        .moveToActivity("user-task-1")
        .endEvent("success-end")
        .done();
  }
}
