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
import io.camunda.process.test.api.assertions.JobSelectors;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
public class CamundaProcessTestCompletionApiIT {

  // to be injected
  private CamundaProcessTestContext processTestContext;
  private CamundaClient client;

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
  void shouldCompleteJobWithPartialExampleData() {
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
  void shouldCompleteMultipleJobsBySameJobType() {
    // Given: a multi-instance service task with a collection of two items
    final String jobType = "test-job";

    final long processDefinitionKey =
        deployProcessModel(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service-task-1")
                .zeebeJobType(jobType)
                .multiInstance()
                .zeebeInputCollectionExpression("[1,2]")
                .done());
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // When: complete both jobs
    processTestContext.completeJob(jobType);
    processTestContext.completeJob(jobType);

    // Then: both jobs are completed (3 elements = 2 service tasks + multi-instance body)
    assertThatProcessInstance(processInstanceEvent)
        .isCompleted()
        .hasCompletedElement("service-task-1", 3);
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
  void shouldCompleteMultipleUserTasksBySameElementId() {
    // Given: a multi-instance user task with a collection of two items
    final String elementId = "user-task-1";

    final long processDefinitionKey =
        deployProcessModel(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .userTask(elementId)
                .zeebeUserTask()
                .multiInstance()
                .zeebeInputCollectionExpression("[1,2]")
                .done());
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // When: complete both user tasks
    processTestContext.completeUserTask(UserTaskSelectors.byElementId(elementId));
    processTestContext.completeUserTask(UserTaskSelectors.byElementId(elementId));

    // Then: both user tasks are completed (3 elements = 2 user tasks + multi-instance body)
    assertThatProcessInstance(processInstanceEvent).isCompleted().hasCompletedElement(elementId, 3);
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

  @Test
  void shouldCompleteJobOfAdHocSubProcess() {
    // given
    deployProcessModel(
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .adHocSubProcess(
                "ad-hoc-sub-process",
                adHocSubProcess -> {
                  adHocSubProcess.zeebeJobType("ad-hoc-sub-process");

                  adHocSubProcess.task("A");
                  adHocSubProcess.task("B");
                })
            .done());

    final long processInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    // when
    processTestContext.completeJobOfAdHocSubProcess(
        JobSelectors.byJobType("ad-hoc-sub-process"),
        Collections.singletonMap("var", "new"),
        result -> result.activateElement("A"));

    processTestContext.completeJobOfAdHocSubProcess(
        JobSelectors.byJobType("ad-hoc-sub-process"),
        Collections.singletonMap("var", "updated"),
        result -> result.completionConditionFulfilled(true));

    // then
    assertThatProcessInstance(ProcessInstanceSelectors.byKey(processInstanceKey))
        .hasCompletedElements("ad-hoc-sub-process", "A")
        .hasVariable("var", "updated")
        .isCompleted();
  }
}
