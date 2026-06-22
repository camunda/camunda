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
import static io.camunda.process.test.api.ConditionalBehaviorTestProcess.*;
import static io.camunda.process.test.api.assertions.ElementSelectors.byName;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.Deployment;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import io.camunda.process.test.api.testCases.TestCase;
import io.camunda.process.test.api.testCases.TestCaseRunner;
import io.camunda.process.test.api.testCases.TestCaseSource;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

@SpringBootTest(classes = {CamundaSpringProcessTestListenerIT.TestApplication.class})
@CamundaSpringProcessTest
public class CamundaSpringProcessTestListenerIT {

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;
  @Autowired private TestCaseRunner testCaseRunner;

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
    assertThatProcessInstance(processInstance)
        .isActive()
        .hasActiveElements(byName("task"))
        .hasVariable("status", "active");
  }

  @Test
  void shouldTriggerTimerEvent() {
    // given
    final Duration timerDuration = Duration.ofHours(1);
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .name("start")
            .userTask("A")
            .name("A")
            .endEvent()
            .moveToActivity("A")
            .boundaryEvent()
            .timerWithDuration(timerDuration.toString())
            .userTask()
            .name("B")
            .endEvent()
            .done();

    client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

    final ProcessInstanceEvent processInstance =
        client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();

    // when
    assertThatProcessInstance(processInstance).hasActiveElements(byName("A"));
    final Instant timeBefore = processTestContext.getCurrentTime();
    processTestContext.increaseTime(timerDuration);
    final Instant timeAfter = processTestContext.getCurrentTime();

    // then
    assertThatProcessInstance(processInstance)
        .hasTerminatedElements(byName("A"))
        .hasActiveElements(byName("B"));
    assertThat(Duration.between(timeBefore, timeAfter))
        .isCloseTo(timerDuration, Duration.ofSeconds(10));
  }

  @Test
  void shouldCompleteProcessWithConditionalBehaviors() {
    // given
    registerConditionalBehaviors();

    // when
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().execute();

    // then
    assertThatProcessInstance(processInstanceEvent)
        .isCompleted()
        .hasVariable("happy", true)
        .hasVariable("exportSuccess", true);
  }

  @Test
  void shouldCompleteProcessFirstRun() {
    // given
    registerConditionalBehaviors();

    // when
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().execute();

    // then
    assertThatProcessInstance(processInstanceEvent)
        .isCompleted()
        .hasVariable("happy", true)
        .hasVariable("exportSuccess", true);
  }

  @Test
  void shouldCompleteProcessSecondRun() {
    // given
    registerConditionalBehaviors();

    // when
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().execute();

    // then
    assertThatProcessInstance(processInstanceEvent)
        .isCompleted()
        .hasVariable("happy", true)
        .hasVariable("exportSuccess", true);
  }

  @ParameterizedTest
  @TestCaseSource
  void shouldPass(final TestCase testCase, final String filename) {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .name("Start")
            .endEvent("end")
            .name("End")
            .done();

    client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

    // when
    testCaseRunner.run(testCase);

    // then
    assertThatProcessInstance(ProcessInstanceSelectors.byProcessId("process")).isCreated();
  }

  @TestDeployment(resources = {"connector-outbound-process.bpmn"})
  @Test
  void shouldDeployProcessDefinitions() {
    // then
    Awaitility.await("until process definitions are available (eventually)")
        .untilAsserted(
            () ->
                assertThat(client.newProcessDefinitionSearchRequest().send().join().items())
                    .extracting(ProcessDefinition::getProcessDefinitionId)
                    .describedAs("Expect the process specified in @TestDeployment to be deployed")
                    .contains("outbound-connector-process")
                    .describedAs("Expect the process specified in @Deployment to be deployed")
                    .contains("connector-process")
                    .hasSize(2));
  }

  private void registerConditionalBehaviors() {
    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(30));
    client.newDeployResourceCommand().addProcessModel(MODEL, PROCESS_ID + ".bpmn").send().join();

    processTestContext
        .when(
            () ->
                CamundaAssert.assertThat(ProcessInstanceSelectors.byProcessId(PROCESS_ID))
                    .hasActiveElement(USER_TASK_ID, 1))
        .then(() -> processTestContext.completeUserTask(USER_TASK_ID, Map.of("happy", false)))
        .then(() -> processTestContext.completeUserTask(USER_TASK_ID, Map.of("happy", true)));

    processTestContext
        .when(
            () ->
                assertThatProcessInstance(ProcessInstanceSelectors.byProcessId(PROCESS_ID))
                    .hasActiveElements(SERVICE_TASK_ID))
        .then(() -> processTestContext.completeJob(JOB_TYPE, Map.of("exportSuccess", true)));
  }

  @Deployment(resources = {"connector-process.bpmn"})
  @Configuration
  static class TestApplication {}
}
