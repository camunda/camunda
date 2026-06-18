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
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.DecisionRequirements;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.process.test.api.assertions.DecisionSelectors;
import io.camunda.process.test.api.assertions.ElementSelectors;
import io.camunda.process.test.api.assertions.IncidentSelectors;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import io.camunda.process.test.api.assertions.VariableSelectors;
import io.camunda.process.test.api.coverage.model.CoverageReport;
import io.camunda.process.test.api.coverage.model.CoverageRunReport;
import io.camunda.process.test.api.coverage.model.ProcessCoverage;
import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.api.judge.MultimodalChatModelAdapter;
import io.camunda.process.test.api.judge.ResolvedDocument;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder.JobWorkerMock;
import io.camunda.process.test.api.testCases.TestCase;
import io.camunda.process.test.api.testCases.TestCaseRunner;
import io.camunda.process.test.api.testCases.TestCaseSource;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.coverage.CoverageCollector;
import io.camunda.process.test.impl.coverage.CoverageTestDataCollector;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;

@CamundaProcessTest
public class CamundaProcessTestExtensionIT {

  // to be injected
  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;
  private TestCaseRunner testCaseRunner;

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
    void shouldAssertProcessInstance() {
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
  class CoverageTests {

    @Test
    void shouldCoverProcess(final TestInfo testInfo) {
      // given
      final CoverageCollector coverageCollector = CoverageCollector.newBuilder().build();
      final BpmnModelInstance simpleGatewayProcess = simpleGatewayProcess();

      // when: run 1 - takes default flow (no gatewayAnswer variable)
      client
          .newDeployResourceCommand()
          .addProcessModel(simpleGatewayProcess, "test-with-simple-gateway.bpmn")
          .send()
          .join();
      final ProcessInstanceEvent processInstance1 =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId("test-with-simple-gateway")
              .latestVersion()
              .send()
              .join();
      CamundaAssert.assertThat(processInstance1).isCompleted();

      // when: run 2 - takes yes flow
      final ProcessInstanceEvent processInstance2 =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId("test-with-simple-gateway")
              .latestVersion()
              .variable("gatewayAnswer", "yes")
              .send()
              .join();
      CamundaAssert.assertThat(processInstance2).isCompleted();

      final String testName = testInfo.getDisplayName();
      final CoverageRunReport coverageRunReport =
          collectCoverageRunReport(coverageCollector, testName);

      // then
      assertThat(coverageRunReport.getProcessCoverages()).hasSize(2);
      assertThat(coverageRunReport.getProcessCoverages())
          .extracting(ProcessCoverage::getCompletedElements)
          .containsExactly(
              Arrays.asList("StartEvent", "GatewayEvent", "NoEvent", "EndEvent"),
              Arrays.asList("StartEvent", "GatewayEvent", "YesEvent", "EndEvent"));
      assertThat(coverageRunReport.getProcessCoverages())
          .extracting(ProcessCoverage::getTakenSequenceFlows)
          .containsExactly(
              Arrays.asList("FlowGateway", "FlowGatewayDefault", "FlowNoEnd"),
              Arrays.asList("FlowGateway", "FlowGatewayYes", "FlowYesEnd"));
      assertThat(coverageRunReport.getProcessCoverages())
          .extracting(ProcessCoverage::getCoverage)
          .containsExactly(0.7, 0.7);
      assertThat(coverageRunReport.getProcessCoverages())
          .extracting(ProcessCoverage::getProcessDefinitionId)
          .containsExactly("test-with-simple-gateway", "test-with-simple-gateway");
    }

    @Test
    void shouldCoverDecision(final TestInfo testInfo) {
      // given
      final CoverageCollector coverageCollector = CoverageCollector.newBuilder().build();

      // when
      client
          .newDeployResourceCommand()
          .addResourceFromClasspath("dmn/simple-decision-table.dmn")
          .send()
          .join();

      final EvaluateDecisionResponse decisionResponse =
          client
              .newEvaluateDecisionCommand()
              .decisionId("simple_decision")
              .variable("yes_or_no", "yes")
              .send()
              .join();

      assertThatDecision(decisionResponse).isEvaluated();

      final String testName = testInfo.getDisplayName();
      final CoverageRunReport coverageRunReport =
          collectCoverageRunReport(coverageCollector, testName);

      // then
      assertThat(coverageRunReport.getDecisionCoverages()).hasSize(1);
      assertThat(coverageRunReport.getDecisionCoverages())
          .first()
          .satisfies(
              dc -> {
                assertThat(dc.getDecisionDefinitionId()).isEqualTo("simple_decision");
                assertThat(dc.getMatchedRuleIds()).containsExactly("DecisionRule_0ikukos");
                assertThat(dc.getMatchedRuleIndices()).containsExactly(1);
                assertThat(dc.getCoverage()).isEqualTo(0.5);
              });
    }

    private BpmnModelInstance simpleGatewayProcess() {
      return Bpmn.createExecutableProcess("test-with-simple-gateway")
          .startEvent("StartEvent")
          .sequenceFlowId("FlowGateway")
          .exclusiveGateway("GatewayEvent")
          .sequenceFlowId("FlowGatewayYes")
          .conditionExpression("= gatewayAnswer = \"yes\"")
          .task("YesEvent")
          .sequenceFlowId("FlowYesEnd")
          .endEvent("EndEvent")
          .moveToLastExclusiveGateway()
          .defaultFlow()
          .sequenceFlowId("FlowGatewayDefault")
          .task("NoEvent")
          .sequenceFlowId("FlowNoEnd")
          .connectTo("EndEvent")
          .done();
    }

    private CoverageRunReport collectCoverageRunReport(
        final CoverageCollector coverageCollector, final String testName) {
      final CoverageReport coverageReport =
          coverageCollector.collectTestRunCoverage(
              CamundaProcessTestExtensionIT.class,
              testName,
              null,
              CoverageTestDataCollector.collectData(new CamundaDataSource(client)));
      return coverageReport.getSuites().stream()
          .flatMap(report -> report.getRuns().stream())
          .filter(run -> run.getName().equals(testName))
          .findFirst()
          .orElseThrow(() -> new AssertionError("No run report found for " + testName));
    }
  }

  @Nested
  class JsonTests {

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
      CamundaAssert.assertThatProcessInstance(ProcessInstanceSelectors.byProcessId("process"))
          .isCreated();
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

    @Test
    void shouldMatchVariableWithNameAndValueContainsSelector() {
      // given
      final long processDefinitionKey = deployProcessModel(processModelWithVariables());
      final Map<String, Object> variables = new HashMap<>();
      variables.put("order_id", "order-123");
      variables.put("other", "value");
      final ProcessInstanceEvent processInstanceEvent =
          client
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .variables(variables)
              .send()
              .join();

      // then
      assertThatProcessInstance(processInstanceEvent)
          .hasVariable(VariableSelectors.byName("order_id"), "order-123")
          .hasVariable(VariableSelectors.byValueContains("order"), "order-123")
          .hasVariable(
              VariableSelectors.byName("order_id").and(VariableSelectors.byValueContains("order")),
              "order-123");
    }

    @Test
    void shouldUpdateVariables() {
      // given
      deployProcessModel(
          Bpmn.createExecutableProcess("process")
              .startEvent()
              .userTask("task")
              .zeebeUserTask()
              .endEvent()
              .done());

      final long processInstanceKey =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId("process")
              .latestVersion()
              .variable("a", "init")
              .send()
              .join()
              .getProcessInstanceKey();

      // when
      final Map<String, Object> updatedVariables = new HashMap<>();
      updatedVariables.put("global", "updated");
      updatedVariables.put("b", "new");

      processTestContext.updateVariables(
          ProcessInstanceSelectors.byKey(processInstanceKey), updatedVariables);

      // then
      assertThatProcessInstance(ProcessInstanceSelectors.byKey(processInstanceKey))
          .hasVariables(updatedVariables);
    }

    @Test
    void shouldUpdateLocalVariables() {
      // given
      deployProcessModel(
          Bpmn.createExecutableProcess("process")
              .startEvent()
              .userTask("task")
              .zeebeUserTask()
              .zeebeInputExpression("init", "local")
              .endEvent()
              .done());

      final long processInstanceKey =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId("process")
              .latestVersion()
              .variable("global", "init")
              .send()
              .join()
              .getProcessInstanceKey();

      // when
      final Map<String, Object> updatedVariables = new HashMap<>();
      updatedVariables.put("global", "updated");
      updatedVariables.put("local", "updated");
      updatedVariables.put("other", "new");

      processTestContext.updateLocalVariables(
          ProcessInstanceSelectors.byKey(processInstanceKey),
          ElementSelectors.byId("task"),
          updatedVariables);

      // then
      assertThatProcessInstance(ProcessInstanceSelectors.byKey(processInstanceKey))
          .hasVariable("global", "updated")
          .hasVariable("other", "new")
          .hasLocalVariable(ElementSelectors.byId("task"), "local", "updated");
    }

    @Test
    void shouldAssertVariableSatisfiesExpression() {
      // given
      final Map<String, Object> helmet = new HashMap<>();
      helmet.put("name", "Helmet");
      helmet.put("quantity", 1);
      final Map<String, Object> flag = new HashMap<>();
      flag.put("name", "Flag");
      flag.put("quantity", 1);
      final Map<String, Object> oxygenTank = new HashMap<>();
      oxygenTank.put("name", "Oxygen tank");
      oxygenTank.put("quantity", 3);
      final Map<String, Object> order = new HashMap<>();
      order.put("status", "approved");
      order.put("items", Arrays.asList(helmet, flag, oxygenTank));

      deployProcessModel(
          Bpmn.createExecutableProcess("process")
              .startEvent()
              .userTask("review")
              .zeebeUserTask()
              .endEvent()
              .done());

      final ProcessInstanceEvent processInstance =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId("process")
              .latestVersion()
              .variable("order", order)
              .send()
              .join();

      // then
      assertThatProcessInstance(processInstance)
          .hasVariableSatisfiesExpression(
              VariableSelectors.byName("order"),
              "order.status = \"approved\" and count(order.items) = 3 and "
                  + "order.items[name = \"Helmet\"][1].quantity = 1");
    }

    @Test
    void shouldAssertLocalVariableSatisfiesExpression() {
      // given
      deployProcessModel(
          Bpmn.createExecutableProcess("process")
              .startEvent()
              .userTask("review")
              .zeebeUserTask()
              .zeebeInputExpression("order.status", "localStatus")
              .endEvent()
              .done());

      final ProcessInstanceEvent processInstance =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId("process")
              .latestVersion()
              .variable("order", Collections.singletonMap("status", "approved"))
              .send()
              .join();

      // then
      assertThatProcessInstance(processInstance)
          .hasLocalVariableSatisfiesExpression(
              ElementSelectors.byId("review"),
              VariableSelectors.byName("localStatus"),
              "localStatus = \"approved\"");
    }

    private BpmnModelInstance processModelWithVariables() {
      return Bpmn.createExecutableProcess("test-process-variables")
          .startEvent()
          .endEvent("success-end")
          .done();
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

  @Nested
  class JobCompletionTests {

    @Test
    void shouldCompleteJobWithVariableMapper() {
      // given
      final long processDefinitionKey = deployProcessModel(processModelWithServiceTask());
      final ProcessInstanceEvent processInstanceEvent =
          client
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .variables(Collections.singletonMap("id", 1))
              .send()
              .join();

      // when
      processTestContext.completeJob(
          "test",
          inputVars -> {
            final int id = ((Number) inputVars.get("id")).intValue();
            return Collections.singletonMap("user", id == 1 ? "Alice" : "Bob");
          });

      // then
      assertThatProcessInstance(processInstanceEvent).isCompleted();
      assertThatProcessInstance(processInstanceEvent).hasVariable("user", "Alice");
    }

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
    void shouldCompleteJobWithExampleData() {
      // given
      final long processDefinitionKey = deployProcessModel(processModelWithExampleData());

      // when
      final ProcessInstanceEvent processInstanceEvent =
          client
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .send()
              .join();

      processTestContext.completeJobWithExampleData("email");

      // then
      final Map<String, Object> expectedVariables = new HashMap<>();
      expectedVariables.put("send_status", 200);

      assertThatProcessInstance(processInstanceEvent).isCompleted();
      assertThatProcessInstance(processInstanceEvent).hasVariables(expectedVariables);
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
  }

  @Nested
  class UserTaskCompletionTests {

    @Test
    void shouldCompleteUserTaskWithVariableMapper() {
      // given
      final long processDefinitionKey = deployProcessModel(processModelWithUserTask());
      final ProcessInstanceEvent processInstanceEvent =
          client
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .variables(Collections.singletonMap("id", 2))
              .send()
              .join();

      // when
      processTestContext.completeUserTask(
          "user-task-1",
          inputVars -> {
            final int id = ((Number) inputVars.get("id")).intValue();
            return Collections.singletonMap("user", id == 1 ? "Alice" : "Bob");
          });

      // then
      assertThatProcessInstance(processInstanceEvent).isCompleted();
      assertThatProcessInstance(processInstanceEvent).hasVariable("user", "Bob");
    }

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
    void shouldCompleteUserTaskWithExampleData() {
      // given
      final long processDefinitionKey =
          deployProcessModel(processModelWithUserTaskAndExampleData());
      final ProcessInstanceEvent processInstanceEvent =
          client
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .send()
              .join();
      final Map<String, Object> variables = new HashMap<>();
      variables.put("email_output", "Lorem ipsum");

      // when
      processTestContext.completeUserTaskWithExampleData("user_task_write_email");

      // then
      assertThatProcessInstance(processInstanceEvent).isCompleted();
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

    private BpmnModelInstance processModelWithUserTaskAndExampleData() {
      return Bpmn.createExecutableProcess("process")
          .startEvent()
          .userTask(
              "user_task_write_email",
              ut ->
                  ut.zeebeUserTask()
                      .zeebeProperty(
                          "camundaModeler:exampleOutputJson",
                          "{\"email_output\": \"Lorem ipsum\"}"))
          .endEvent()
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

    @Test
    void shouldMockJobWorkerWithExampleData() {
      // given
      processTestContext.mockJobWorker("email").thenCompleteWithExampleData();

      final long processDefinitionKey = deployProcessModel(processModelWithExampleData());

      // when
      final ProcessInstanceEvent processInstanceEvent =
          client
              .newCreateInstanceCommand()
              .processDefinitionKey(processDefinitionKey)
              .send()
              .join();

      // then
      final Map<String, Object> expectedVariables = new HashMap<>();
      expectedVariables.put("send_status", 200);

      assertThatProcessInstance(processInstanceEvent).isCompleted();
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

    @Test
    void shouldMockChildProcessWithVariableSupplier() {
      // given: a parent process with a multi-instance call activity
      final String childProcessId = "child-process";
      final String parentVariableName = "item";
      final String childVariableName = "result";

      deployProcessModel(
          Bpmn.createExecutableProcess("parent-process")
              .startEvent()
              .callActivity(
                  "call-activity",
                  c ->
                      c.zeebeProcessId(childProcessId)
                          .zeebeOutputExpression(childVariableName, childVariableName)
                          .multiInstance()
                          .zeebeInputCollectionExpression("[1,2]")
                          .zeebeInputElement(parentVariableName)
                          .zeebeOutputCollection("results")
                          .zeebeOutputElementExpression(childVariableName))
              .endEvent()
              .done());

      // the child process returns a dynamic variable based on the parent variable
      processTestContext.mockChildProcess(
          childProcessId,
          parentVariables ->
              Collections.singletonMap(
                  childVariableName, 2 * (int) parentVariables.get(parentVariableName)));

      // when
      final ProcessInstanceEvent processInstanceEvent =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId("parent-process")
              .latestVersion()
              .send()
              .join();

      // then: verify the child process mock using the multi-instance output collection variable
      assertThatProcessInstance(processInstanceEvent).hasVariable("results", Arrays.asList(2, 4));
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

  @Nested
  class IncidentTests {

    @Test
    void shouldResolveIncident() {
      // given
      deployProcessModel(
          Bpmn.createExecutableProcess("process")
              .startEvent()
              .exclusiveGateway("gateway")
              .conditionExpression("priority > 10")
              .endEvent("high")
              .moveToLastExclusiveGateway()
              .defaultFlow()
              .endEvent("low")
              .done());

      final long processInstanceKey =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId("process")
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey();

      assertThatProcessInstance(ProcessInstanceSelectors.byKey(processInstanceKey))
          .hasActiveIncidents();

      // when
      client.newSetVariablesCommand(processInstanceKey).variable("priority", 7).send().join();

      processTestContext.resolveIncident(IncidentSelectors.byElementId("gateway"));

      // then
      assertThatProcessInstance(ProcessInstanceSelectors.byKey(processInstanceKey))
          .hasNoActiveIncidents()
          .isCompleted();
    }

    @Test
    void shouldResolveIncidentAndUpdateJobRetries() {
      // given
      final String jobType = "task";

      deployProcessModel(
          Bpmn.createExecutableProcess("process")
              .startEvent()
              .serviceTask(jobType, t -> t.zeebeJobType(jobType).zeebeJobRetries("1"))
              .done());

      final long processInstanceKey =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId("process")
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey();

      Awaitility.await("fail the job to create an incident")
          .untilAsserted(
              () ->
                  client
                      .newActivateJobsCommand()
                      .jobType(jobType)
                      .maxJobsToActivate(1)
                      .send()
                      .join()
                      .getJobs()
                      .stream()
                      .findFirst(),
              job -> {
                assertThat(job).isPresent();

                client.newFailCommand(job.get().getKey()).retries(0).send().join();
              });

      assertThatProcessInstance(ProcessInstanceSelectors.byKey(processInstanceKey))
          .hasActiveIncidents();

      // when
      processTestContext.resolveIncident(IncidentSelectors.byElementId(jobType));

      processTestContext.completeJob(jobType);

      // then
      assertThatProcessInstance(ProcessInstanceSelectors.byKey(processInstanceKey))
          .hasNoActiveIncidents()
          .isCompleted();
    }
  }

  @Nested
  class ConditionalBehaviorTests {

    private static final String PROCESS_ID = "user-happiness-check";
    private static final String USER_TASK_ID = "State_Happiness";
    private static final String SERVICE_TASK_ID = "Export_Happiness";
    private static final String JOB_TYPE = "io.camunda:http-json:1";

    @BeforeEach
    void setupBehaviors() {
      // Deploy the process model
      client
          .newDeployResourceCommand()
          .addProcessModel(conditionalBehaviorProcess(), PROCESS_ID + ".bpmn")
          .send()
          .join();

      processTestContext
          .when(
              () ->
                  CamundaAssert.assertThat(ProcessInstanceSelectors.byProcessId(PROCESS_ID))
                      .hasActiveElement(USER_TASK_ID, 1))
          .then(
              () ->
                  processTestContext.completeUserTask(
                      USER_TASK_ID, Collections.singletonMap("happy", false)))
          .then(
              () ->
                  processTestContext.completeUserTask(
                      USER_TASK_ID, Collections.singletonMap("happy", true)));

      processTestContext
          .when(
              () ->
                  assertThatProcessInstance(ProcessInstanceSelectors.byProcessId(PROCESS_ID))
                      .hasActiveElements(SERVICE_TASK_ID))
          .then(
              () ->
                  processTestContext.completeJob(
                      JOB_TYPE, Collections.singletonMap("exportSuccess", true)));
    }

    @Test
    void shouldCompleteProcessFirstRun() {
      final ProcessInstanceEvent processInstanceEvent =
          client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().execute();

      // increase assertion timeout because the immediate loop-back occasionally activates
      // the reset gate timeout of 5 seconds
      assertThatProcessInstance(processInstanceEvent)
          .withAssertionTimeout(Duration.ofSeconds(30))
          .isCompleted();

      assertThatProcessInstance(processInstanceEvent)
          .hasVariable("happy", true)
          .hasVariable("exportSuccess", true);
    }

    @Test
    void shouldCompleteProcessSecondRun() {
      // Same behavior — proves @BeforeEach re-registers fresh behaviors for each test,
      // including a fresh action chain (happy=false first, then happy=true)
      final ProcessInstanceEvent processInstanceEvent =
          client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().execute();

      // increase assertion timeout because the immediate loop-back occasionally activates
      // the reset gate timeout of 5 seconds
      assertThatProcessInstance(processInstanceEvent)
          .withAssertionTimeout(Duration.ofSeconds(30))
          .isCompleted();

      assertThatProcessInstance(processInstanceEvent)
          .hasVariable("happy", true)
          .hasVariable("exportSuccess", true);
    }

    private BpmnModelInstance conditionalBehaviorProcess() {
      return Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .userTask(USER_TASK_ID)
          .zeebeUserTask()
          .exclusiveGateway("User_Happy_Gateway")
          .conditionExpression("=happy")
          .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType(JOB_TYPE).zeebeJobRetries("3"))
          .endEvent()
          .moveToLastExclusiveGateway()
          .defaultFlow()
          .connectTo(USER_TASK_ID)
          .done();
    }
  }

  @Nested
  static class JudgeDocumentResolutionTests {

    private static final String PROCESS_ID = "judge-document";
    private static final String ATTACHMENT_VARIABLE = "attachment";
    private static final String FAKE_RESPONSE = "{\"score\": 1.0, \"reasoning\": \"ok\"}";
    private static final String CONTENT_TYPE = "text/plain";
    private static final String NOTE_FILE_NAME = "note.txt";
    private static final String NOTE_TEXT = "remember the milk";
    private static final String MEMO_FILE_NAME = "memo.txt";
    private static final String MEMO_TEXT = "buy bread";

    private static final CapturingMultimodalChatModelAdapter STUB =
        new CapturingMultimodalChatModelAdapter();

    // injected by @CamundaProcessTest on the enclosing class's extension
    private CamundaClient client;

    @BeforeEach
    void setUp() {
      STUB.reset();
      CamundaAssert.setJudgeConfig(JudgeConfig.of(STUB).withAttachDocuments(true));
    }

    @Test
    void shouldResolveAndAttachUploadedDocumentToJudgeCall() {
      // given
      final DocumentReferenceResponse reference = uploadDocument(client, NOTE_FILE_NAME, NOTE_TEXT);
      final ProcessInstanceEvent instance = startProcessWithAttachment(client, reference);

      // when
      assertThatProcessInstance(instance)
          .isCompleted()
          .hasVariableSatisfiesJudge(ATTACHMENT_VARIABLE, "should be a short note");

      // then
      assertMultimodalPathTaken();
      assertAttachedDocumentsMatch(Arrays.asList(reference), Arrays.asList(NOTE_TEXT.getBytes()));
      assertPromptCarriesResolvedDocumentSection(reference);
    }

    @Test
    void shouldResolveMultipleDistinctDocumentsAndDeduplicate() {
      // given — two distinct uploads; the first is referenced twice across nested paths,
      // the second is referenced once
      final DocumentReferenceResponse noteReference =
          uploadDocument(client, NOTE_FILE_NAME, NOTE_TEXT);
      final DocumentReferenceResponse memoReference =
          uploadDocument(client, MEMO_FILE_NAME, MEMO_TEXT);

      final Map<String, Object> nestedMessage = new HashMap<>();
      nestedMessage.put("role", "user");
      nestedMessage.put("attachments", Arrays.asList(noteReference, memoReference));
      final Map<String, Object> attachmentWithDuplicates = new HashMap<>();
      attachmentWithDuplicates.put("tool_result", noteReference);
      attachmentWithDuplicates.put("history", Arrays.asList(nestedMessage));

      final ProcessInstanceEvent instance =
          startProcessWithAttachment(client, attachmentWithDuplicates);

      // when
      assertThatProcessInstance(instance)
          .isCompleted()
          .hasVariableSatisfiesJudge(ATTACHMENT_VARIABLE, "should describe two short notes");

      // then — duplicate references collapse but distinct documents are preserved in order
      assertAttachedDocumentsMatch(
          Arrays.asList(noteReference, memoReference),
          Arrays.asList(NOTE_TEXT.getBytes(), MEMO_TEXT.getBytes()));
    }

    @Test
    void shouldUseTextOnlyPathWhenDocumentAttachmentDisabled() {
      // given — judge config explicitly disables document attachment for this test
      CamundaAssert.setJudgeConfig(JudgeConfig.of(STUB).withAttachDocuments(false));

      final DocumentReferenceResponse reference = uploadDocument(client, NOTE_FILE_NAME, NOTE_TEXT);
      final ProcessInstanceEvent instance = startProcessWithAttachment(client, reference);

      // when
      assertThatProcessInstance(instance)
          .isCompleted()
          .hasVariableSatisfiesJudge(ATTACHMENT_VARIABLE, "should be a short note");

      // then — text-only generate(String) is used; no documents are resolved or attached
      assertTextOnlyPathTaken();
      assertThat(STUB.documents.get()).isNull();
      assertThat(STUB.prompt.get()).doesNotContain("<resolved_documents>");
    }

    private static void assertTextOnlyPathTaken() {
      assertThat(STUB.textOnlyCalls.get()).isEqualTo(1);
      assertThat(STUB.multimodalCalls.get()).isZero();
    }

    private static DocumentReferenceResponse uploadDocument(
        final CamundaClient client, final String fileName, final String text) {
      return client
          .newCreateDocumentCommand()
          .content(text.getBytes())
          .fileName(fileName)
          .contentType(CONTENT_TYPE)
          .send()
          .join();
    }

    private static ProcessInstanceEvent startProcessWithAttachment(
        final CamundaClient client, final Object attachmentValue) {
      deployJudgeDocumentProcess(client);
      return client
          .newCreateInstanceCommand()
          .bpmnProcessId(PROCESS_ID)
          .latestVersion()
          .variables(Collections.singletonMap(ATTACHMENT_VARIABLE, attachmentValue))
          .send()
          .join();
    }

    private static void deployJudgeDocumentProcess(final CamundaClient client) {
      client
          .newDeployResourceCommand()
          .addProcessModel(
              Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done(),
              PROCESS_ID + ".bpmn")
          .send()
          .join();
    }

    private static void assertMultimodalPathTaken() {
      assertThat(STUB.multimodalCalls.get()).isEqualTo(1);
      assertThat(STUB.textOnlyCalls.get()).isZero();
    }

    private static void assertAttachedDocumentsMatch(
        final List<DocumentReferenceResponse> expectedReferences,
        final List<byte[]> expectedContents) {
      final List<ResolvedDocument> captured = STUB.documents.get();
      assertThat(captured).hasSameSizeAs(expectedReferences);
      for (int i = 0; i < expectedReferences.size(); i++) {
        final DocumentReferenceResponse expected = expectedReferences.get(i);
        final ResolvedDocument attached = captured.get(i);
        assertThat(attached.getDocumentId()).isEqualTo(expected.getDocumentId());
        assertThat(attached.getContentType()).isEqualTo(CONTENT_TYPE);
        assertThat(attached.getContent()).isEqualTo(expectedContents.get(i));
      }
    }

    private static void assertPromptCarriesResolvedDocumentSection(
        final DocumentReferenceResponse expected) {
      assertThat(STUB.prompt.get())
          .contains("<resolved_documents>")
          .contains(expected.getDocumentId());
    }

    private static final class CapturingMultimodalChatModelAdapter
        implements MultimodalChatModelAdapter {

      private final AtomicReference<String> prompt = new AtomicReference<>();
      private final AtomicReference<List<ResolvedDocument>> documents = new AtomicReference<>();
      private final AtomicInteger textOnlyCalls = new AtomicInteger();
      private final AtomicInteger multimodalCalls = new AtomicInteger();

      @Override
      public String generate(final String prompt) {
        this.prompt.set(prompt);
        textOnlyCalls.incrementAndGet();
        return FAKE_RESPONSE;
      }

      @Override
      public String generate(final String prompt, final List<ResolvedDocument> documents) {
        this.prompt.set(prompt);
        this.documents.set(documents);
        multimodalCalls.incrementAndGet();
        return FAKE_RESPONSE;
      }

      void reset() {
        prompt.set(null);
        documents.set(null);
        textOnlyCalls.set(0);
        multimodalCalls.set(0);
      }
    }
  }
}
