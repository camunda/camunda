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

import static io.camunda.process.test.api.CamundaAssert.assertThatDecision;
import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.CamundaAssert.assertThatUserTask;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.ElementSelectors.byName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.process.test.api.assertions.DecisionSelectors;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder.JobWorkerMock;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
public class CamundaProcessTestExtensionIT {

  // to be injected
  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  private long deployProcessModel(final BpmnModelInstance processModel) {
    return client
        .newDeployResourceCommand()
        .addProcessModel(processModel, "testProcess.bpmn")
        .send()
        .join()
        .getProcesses()
        .get(0)
        .getProcessDefinitionKey();
  }

  @Nested
  class ProcessInstanceTests {

    @Test
    void shouldCreateProcessInstance() {
      // given
      final BpmnModelInstance process =
          Bpmn.createExecutableProcess("process")
              .startEvent()
              .name("start")
              .zeebeOutputExpression("\"active\"", "status")
              .userTask()
              .name("task")
              .endEvent()
              .name("end")
              .zeebeOutputExpression("\"ok\"", "result")
              .done();

      client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

      // when
      final ProcessInstanceEvent processInstance =
          client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();

      // then
      CamundaAssert.assertThatProcessInstance(processInstance)
          .isActive()
          .hasActiveElements(byName("task"));
    }

    @Test
    void shouldAssertUserTask() {
      // given
      final long processDefinitionKey = deployProcessModel(processModelWithUserTask());
      client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

      // then
      assertThatUserTask(UserTaskSelectors.byElementId("user-task-1")).isCreated();
    }

    @Test
    void shouldAssertDecision() {
      // given
      client
          .newDeployResourceCommand()
          .addResourceFromClasspath("dmn/decision-table-unique.dmn")
          .send()
          .join();

      final long processDefinitionKey = deployProcessModel(processModelWithBusinessRuleTask());

      client
          .newCreateInstanceCommand()
          .processDefinitionKey(processDefinitionKey)
          .variable("lightsaber_color", "blue")
          .send()
          .join();

      // then
      final Map<String, Object> expectedOutput = new HashMap<>();
      expectedOutput.put("jedi_or_sith", "jedi");
      expectedOutput.put("force_user", "Mace");

      assertThatDecision(DecisionSelectors.byName("Jedi or Sith"))
          .isEvaluated()
          .hasOutput(expectedOutput);
    }

    private BpmnModelInstance processModelWithUserTask() {
      return Bpmn.createExecutableProcess("test-process-with-user-task")
          .startEvent("start-1")
          .userTask("user-task-1")
          .name("user-task")
          .zeebeUserTask()
          .boundaryEvent("error-boundary-event")
          .error("bpmn-error")
          .endEvent("error-end")
          .moveToActivity("user-task-1")
          .endEvent("success-end")
          .done();
    }

    private BpmnModelInstance processModelWithBusinessRuleTask() {
      return Bpmn.createExecutableProcess("test-process")
          .startEvent("start-1")
          .businessRuleTask("business-rule-1")
          .zeebeCalledDecisionId("jedi_or_sith")
          .zeebeResultVariable("jedi_or_sith")
          .userTask("user-task-1")
          .endEvent("success-end")
          .done();
    }
  }

  @Nested
  class CustomAssertionTests {

    @Test
    void shouldQueryAndAssertProcessInstance() {
      // given
      final BpmnModelInstance process =
          Bpmn.createExecutableProcess("process")
              .startEvent()
              .name("start")
              .userTask()
              .name("task")
              .endEvent()
              .name("end")
              .done();

      client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

      // when
      final ProcessInstanceEvent processInstance =
          client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();

      // then
      Awaitility.await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () ->
                  assertThat(client.newProcessInstanceSearchRequest().send().join().items())
                      .hasSize(1)
                      .extracting(ProcessInstance::getProcessInstanceKey)
                      .contains(processInstance.getProcessInstanceKey()));
    }

    @Test
    void shouldQueryAndAssertUserTask() {
      // given
      final BpmnModelInstance process =
          Bpmn.createExecutableProcess("process")
              .startEvent()
              .name("start")
              .userTask(
                  "task",
                  userTask -> userTask.zeebeUserTask().zeebeAssignee("me").zeebeTaskPriority("60"))
              .name("task")
              .endEvent()
              .name("end")
              .done();

      client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

      // when
      final ProcessInstanceEvent processInstance =
          client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();

      // then
      CamundaAssert.assertThatProcessInstance(processInstance)
          .isActive()
          .hasActiveElements(byName("task"));

      Awaitility.await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () ->
                  client
                      .newUserTaskSearchRequest()
                      .filter(
                          filter ->
                              filter
                                  .processInstanceKey(processInstance.getProcessInstanceKey())
                                  .state(UserTaskState.CREATED))
                      .send()
                      .join()
                      .items(),
              userTasks -> {
                assertThat(userTasks).isNotEmpty();

                final UserTask userTask = userTasks.get(0);
                assertThat(userTask)
                    .returns("task", UserTask::getName)
                    .returns("me", UserTask::getAssignee)
                    .returns(60, UserTask::getPriority);

                // when: complete the user task
                client.newCompleteUserTaskCommand(userTask.getUserTaskKey()).send().join();
              });

      // then: verify that the user task and the process instance are completed
      CamundaAssert.assertThatProcessInstance(processInstance)
          .hasCompletedElements(byName("task"))
          .isCompleted();
    }
  }

  @Nested
  class TimerEventTests {

    private final Duration timerDuration = Duration.ofHours(1);

    @BeforeEach
    void deployProcess() {
      final BpmnModelInstance process =
          Bpmn.createExecutableProcess("process")
              .startEvent("start")
              .intermediateCatchEvent("timer", e -> e.timerWithDuration(timerDuration.toString()))
              .endEvent("end")
              .done();

      client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();
    }

    @Test
    void shouldIncreaseTimeAndTriggerTimerEvent() {
      // given
      final ProcessInstanceEvent processInstance =
          client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();

      // when
      CamundaAssert.assertThatProcessInstance(processInstance).hasActiveElements(byId("timer"));

      processTestContext.increaseTime(timerDuration);

      // then
      CamundaAssert.assertThatProcessInstance(processInstance)
          .isCompleted()
          .hasCompletedElements(byId("timer"));
    }

    @Test
    void shouldSetTimeAndTriggerTimerEvent() {
      // given
      final ProcessInstanceEvent processInstance =
          client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();

      // when
      CamundaAssert.assertThatProcessInstance(processInstance).hasActiveElements(byId("timer"));

      final Instant currentTime = processTestContext.getCurrentTime();
      processTestContext.setTime(currentTime.plus(timerDuration));

      // then
      CamundaAssert.assertThatProcessInstance(processInstance)
          .isCompleted()
          .hasCompletedElements(byId("timer"));
    }
  }

  @Nested
  class VariableTests {

    @Test
    void shouldAssertTruncatedVariable() {
      // given
      final BpmnModelInstance process =
          Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

      client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

      final Map<String, Object> variables = new HashMap<>();
      variables.put("small", "smallValue");
      variables.put("large", createLargeStringValue());

      // when
      final ProcessInstanceEvent processInstance =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId("process")
              .latestVersion()
              .variables(variables)
              .send()
              .join();

      // then
      CamundaAssert.assertThatProcessInstance(processInstance).hasVariables(variables);

      assertThat(
              client.newVariableSearchRequest().filter(f -> f.name("large")).execute().singleItem())
          .describedAs("Ensure that the variable is large enough to be truncated")
          .satisfies(variable -> assertThat(variable.isTruncated()).isTrue());
    }

    private String createLargeStringValue() {
      final int sizeBytes = 100 * 1024; // 100 KB
      final char[] chars = new char[sizeBytes];
      java.util.Arrays.fill(chars, 'x');
      return new String(chars);
    }
  }

  @Nested
  class MessageEventTests {

    @Test
    public void shouldAssertMessageSubscription() {
      // given
      final BpmnModelInstance process =
          Bpmn.createExecutableProcess("MoonExplorationProcess")
              .startEvent("start")
              .intermediateCatchEvent(
                  "message",
                  e ->
                      e.message(
                          m -> m.name("AstronautReady").zeebeCorrelationKeyExpression("astronaut")))
              .endEvent("end")
              .done();

      client
          .newDeployResourceCommand()
          .addProcessModel(process, "MoonExplorationProcess.bpmn")
          .send()
          .join();

      // when
      final ProcessInstanceEvent processInstance =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId("MoonExplorationProcess")
              .latestVersion()
              .variable("astronaut", "Zee")
              .send()
              .join();

      CamundaAssert.assertThatProcessInstance(processInstance)
          .isWaitingForMessage("AstronautReady")
          .isWaitingForMessage("AstronautReady", "Zee")
          .isNotWaitingForMessage("AstronautReady", "Cam")
          .isNotWaitingForMessage("ProximityAlarm");

      client
          .newCorrelateMessageCommand()
          .messageName("AstronautReady")
          .correlationKey("Zee")
          .send()
          .join();

      // then
      CamundaAssert.assertThatProcessInstance(processInstance)
          .hasCorrelatedMessage("AstronautReady")
          .hasCorrelatedMessage("AstronautReady", "Zee");
    }
  }

  @Nested
  class JobCompletionTests {

    @Test
    void shouldCompleteJobWithVariables() {
      // given
      final long processDefinitionKey = deployProcessModel(processModelWithServiceTask());
      final ProcessInstanceEvent processInstanceEvent =
          client
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .send()
              .join();
      final Map<String, Object> variables = new HashMap<>();
      variables.put("abc", 123);

      // when
      processTestContext.completeJob("test", variables);

      // then
      assertThatProcessInstance(processInstanceEvent).isCompleted();
      assertThatProcessInstance(processInstanceEvent).hasCompletedElements("success-end");
      assertThatProcessInstance(processInstanceEvent).hasVariables(variables);
    }

    @Test
    void shouldCompleteMultipleJobsBySameJobType() {
      // given: a multi-instance service task with a collection of two items
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
          client
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .send()
              .join();

      // when: complete both jobs
      processTestContext.completeJob(jobType);
      processTestContext.completeJob(jobType);

      // then: both jobs are completed (3 elements = 2 service tasks + multi-instance body)
      assertThatProcessInstance(processInstanceEvent)
          .isCompleted()
          .hasCompletedElement("service-task-1", 3);
    }

    @Test
    void shouldThrowBpmnErrorFromJobWithVariables() {
      // given
      final long processDefinitionKey = deployProcessModel(processModelWithServiceTask());
      final ProcessInstanceEvent processInstanceEvent =
          client
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .send()
              .join();
      final Map<String, Object> variables = new HashMap<>();
      variables.put("abc", 123);

      // when
      processTestContext.throwBpmnErrorFromJob("test", "bpmn-error", variables);

      // then
      assertThatProcessInstance(processInstanceEvent).isCompleted();
      assertThatProcessInstance(processInstanceEvent).hasCompletedElements("error-end");

      final Map<String, Object> expectedVariables = new HashMap<>();
      expectedVariables.put("error_code", 123);
      assertThatProcessInstance(processInstanceEvent).hasVariables(expectedVariables);
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
  }

  @Nested
  class UserTaskCompletionTests {

    @Test
    void shouldCompleteUserTask() {
      // given
      final long processDefinitionKey = deployProcessModel(processModelWithUserTask());
      final ProcessInstanceEvent processInstanceEvent =
          client
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .send()
              .join();
      final Map<String, Object> variables = new HashMap<>();
      variables.put("abc", 123);

      // when
      processTestContext.completeUserTask("user-task-1", variables);

      // then
      assertThatProcessInstance(processInstanceEvent).isCompleted();
      assertThatProcessInstance(processInstanceEvent).hasCompletedElements("success-end");
      assertThatProcessInstance(processInstanceEvent).hasVariables(variables);
    }

    @Test
    void shouldCompleteMultipleUserTasksBySameElementId() {
      // given: a multi-instance user task with a collection of two items
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
          client
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .send()
              .join();

      // when: complete both user tasks
      processTestContext.completeUserTask(UserTaskSelectors.byElementId(elementId));
      processTestContext.completeUserTask(UserTaskSelectors.byElementId(elementId));

      // then: both user tasks are completed (3 elements = 2 user tasks + multi-instance body)
      assertThatProcessInstance(processInstanceEvent)
          .isCompleted()
          .hasCompletedElement(elementId, 3);
    }

    private BpmnModelInstance processModelWithUserTask() {
      return Bpmn.createExecutableProcess("test-process-with-user-task")
          .startEvent("start-1")
          .userTask("user-task-1")
          .name("user-task")
          .zeebeUserTask()
          .boundaryEvent("error-boundary-event")
          .error("bpmn-error")
          .endEvent("error-end")
          .moveToActivity("user-task-1")
          .endEvent("success-end")
          .done();
    }
  }

  @Nested
  class JobWorkerMockTests {

    @Test
    void shouldThrowBpmnErrorWithVariables() {
      // given
      final Map<String, Object> variables = new HashMap<>();
      variables.put("abc", 123);

      final JobWorkerMock mockedJobWorker =
          processTestContext.mockJobWorker("test").thenThrowBpmnError("bpmn-error", variables);
      final long processDefinitionKey = deployProcessModel(processModelWithServiceTask());

      // when
      final ProcessInstanceEvent processInstanceEvent =
          client
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .send()
              .join();

      // then
      assertThatProcessInstance(processInstanceEvent).hasCompletedElements("error-end");

      final Map<String, Object> expectedVariables = new HashMap<>();
      expectedVariables.put("error_code", 123);
      assertThatProcessInstance(processInstanceEvent).hasVariables(expectedVariables);

      assertThat(mockedJobWorker.getInvocations()).isEqualTo(1);
      assertThat(mockedJobWorker.getActivatedJobs().get(0).getElementId())
          .isEqualTo("service-task-1");
    }

    @Test
    void shouldMockJobWorkerWithVariables() {
      // given
      final Map<String, Object> variables = new HashMap<>();
      variables.put("abc", 123);
      final JobWorkerMock mockedJobWorker =
          processTestContext.mockJobWorker("test").thenComplete(variables);

      final long processDefinitionKey =
          deployProcessModel(processModelWithServiceTaskAndVariables());

      // when
      final ProcessInstanceEvent processInstanceEvent =
          client
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .send()
              .join();

      // then
      assertThatProcessInstance(processInstanceEvent).isCompleted();
      assertThatProcessInstance(processInstanceEvent).hasCompletedElements("success-end");
      assertThatProcessInstance(processInstanceEvent).hasVariables(variables);

      assertThat(mockedJobWorker.getInvocations()).isEqualTo(1);
      assertThat(mockedJobWorker.getActivatedJobs().get(0).getVariablesAsMap())
          .contains(entry("error_code", "404"));
    }

    @Test
    void shouldMockJobWorkerWithJobHandlerSuccess() {
      // given
      final JobWorkerMock mockedJobWorker =
          processTestContext
              .mockJobWorker("test")
              .withHandler((jobClient, job) -> jobClient.newCompleteCommand(job).send().join());

      final long processDefinitionKey = deployProcessModel(processModelWithServiceTask());

      // when
      final ProcessInstanceEvent processInstanceEvent =
          client
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .send()
              .join();

      // then
      assertThatProcessInstance(processInstanceEvent).isCompleted();
      assertThatProcessInstance(processInstanceEvent).hasCompletedElements("success-end");

      assertThat(mockedJobWorker.getInvocations()).isEqualTo(1);
      assertThat(mockedJobWorker.getActivatedJobs().get(0).getVariablesAsMap()).isEmpty();
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

    private BpmnModelInstance processModelWithServiceTaskAndVariables() {
      return Bpmn.createExecutableProcess("test-process")
          .startEvent()
          .serviceTask("service-task-1")
          .zeebeInputExpression("\"404\"", "error_code")
          .zeebeJobType("test")
          .boundaryEvent("error-boundary-event")
          .error("bpmn-error")
          .zeebeOutputExpression("abc", "error_code")
          .endEvent("error-end")
          .moveToActivity("service-task-1")
          .endEvent("success-end")
          .done();
    }
  }

  @Nested
  class MockChildProcessTests {

    @Test
    void shouldMockChildProcessWithVariables() {
      // given
      deployProcessModel(childProcessModel());
      final long processDefinitionKey = deployProcessModel(processModelWithChildProcess());
      final Map<String, Object> variables = new HashMap<>();
      variables.put("abc", 123);
      final Map<String, Object> mapValue = new HashMap<>();
      mapValue.put("def", 4554534);
      variables.put("mapKey", mapValue);

      // when
      processTestContext.mockChildProcess("child-process-1", variables);
      final ProcessInstanceEvent processInstanceEvent =
          client
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .send()
              .join();

      // then
      assertThatProcessInstance(processInstanceEvent).isCompleted();
      assertThatProcessInstance(processInstanceEvent).hasCompletedElements("success-end");
      assertThatProcessInstance(processInstanceEvent).hasVariables(variables);
    }

    private BpmnModelInstance processModelWithChildProcess() {
      return Bpmn.createExecutableProcess("test-process")
          .startEvent("start-1")
          .callActivity("call-child-process")
          .zeebeProcessId("child-process-1")
          .endEvent("success-end")
          .done();
    }

    private BpmnModelInstance childProcessModel() {
      return Bpmn.createExecutableProcess("child-process-1")
          .startEvent("start-child")
          .serviceTask("child-service-task")
          .zeebeJobType("child-job")
          .endEvent("child-end")
          .done();
    }
  }
}
