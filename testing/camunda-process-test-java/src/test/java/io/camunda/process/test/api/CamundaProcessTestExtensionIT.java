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

import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.ElementSelectors.byName;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.DecisionRequirements;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.time.Instant;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
public class CamundaProcessTestExtensionIT {

  // to be injected
  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

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
        .hasActiveElements(byName("task"))
        .hasVariable("status", "active");
  }

  @Test
  void shouldQueryProcessInstances() {
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
  void shouldAssertUserTask() {
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
  class DeploymentAnnotationTests {

    @TestDeployment(
        resources = {
          "camundaProcessTestExtensionIT/hello-world.bpmn",
          "camundaProcessTestExtensionIT/greeting.dmn"
        })
    @Test
    void shouldDeployResources() {
      // then
      Awaitility.await("Wait until deployment resources are available (eventually)")
          .untilAsserted(
              () -> {
                assertThat(client.newProcessDefinitionSearchRequest().send().join().items())
                    .extracting(ProcessDefinition::getResourceName)
                    .describedAs("Expect the BPMN process to be deployed")
                    .contains("camundaProcessTestExtensionIT/hello-world.bpmn");

                assertThat(client.newDecisionRequirementsSearchRequest().send().join().items())
                    .extracting(DecisionRequirements::getResourceName)
                    .describedAs("Expect the DMN decision to be deployed")
                    .contains("camundaProcessTestExtensionIT/greeting.dmn");
              });
    }
  }
}
