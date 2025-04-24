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
    final long processDefinitionKey = deployProcessModel();

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
    final long processDefinitionKey = deployProcessModel();

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
    final long processDefinitionKey = deployProcessModel();

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
    final long processDefinitionKey = deployProcessModel();

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
    final long processDefinitionKey = deployProcessModel();

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
    final long processDefinitionKey = deployProcessModel();

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
    final long processDefinitionKey = deployProcessModel();
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
    final long processDefinitionKey = deployProcessModel();
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
    final long processDefinitionKey = deployProcessModel();
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
    final long processDefinitionKey = deployProcessModel();
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

  /**
   * Deploys a process model and waits until it is accessible via the API.
   *
   * @return the process definition key
   */
  private long deployProcessModel() {
    final BpmnModelInstance processModel =
        Bpmn.createExecutableProcess("test-process")
            .startEvent()
            .serviceTask("service-task-1")
            .zeebeJobType("test")
            .boundaryEvent("error-boundary-event")
            .error("bpmn-error")
            .endEvent("error-end")
            .moveToActivity("service-task-1")
            .endEvent("success-end")
            .done();

    final DeploymentEvent deploymentEvent =
        client
            .newDeployResourceCommand()
            .addProcessModel(processModel, "test-process.bpmn")
            .send()
            .join();
    return deploymentEvent.getProcesses().stream().findFirst().get().getProcessDefinitionKey();
  }
}
