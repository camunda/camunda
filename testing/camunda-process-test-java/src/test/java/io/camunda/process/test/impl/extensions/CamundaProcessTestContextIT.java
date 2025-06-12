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

import static io.camunda.process.test.api.CamundaAssert.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.DecisionSelectors;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import io.camunda.process.test.impl.assertions.util.AssertionJsonMapper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.DecisionTable;
import org.camunda.bpm.model.dmn.instance.Definitions;
import org.camunda.bpm.model.dmn.instance.Input;
import org.camunda.bpm.model.dmn.instance.InputEntry;
import org.camunda.bpm.model.dmn.instance.InputExpression;
import org.camunda.bpm.model.dmn.instance.Output;
import org.camunda.bpm.model.dmn.instance.OutputEntry;
import org.camunda.bpm.model.dmn.instance.Rule;
import org.camunda.bpm.model.dmn.instance.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@CamundaProcessTest
public class CamundaProcessTestContextIT {

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
    assertThat(processInstanceEvent).hasCompletedElements("error-end");
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
    assertThat(processInstanceEvent).hasCompletedElements("error-end");

    final Map<String, Object> expectedVariables = new HashMap<>();
    expectedVariables.put("error_code", 123);
    assertThat(processInstanceEvent).hasVariables(expectedVariables);
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
    assertThat(processInstanceEvent).isCompleted();
    assertThat(processInstanceEvent).hasCompletedElements("success-end");
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
    assertThat(processInstanceEvent).isCompleted();
    assertThat(processInstanceEvent).hasCompletedElements("success-end");
    assertThat(processInstanceEvent).hasVariables(variables);
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
    assertThat(processInstanceEvent).isCompleted();
    assertThat(processInstanceEvent).hasCompletedElements("error-end");
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
    assertThat(processInstanceEvent).isCompleted();
    assertThat(processInstanceEvent).hasCompletedElements("success-end");
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
    assertThat(processInstanceEvent).isCompleted();
    assertThat(processInstanceEvent).hasCompletedElements("success-end");
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
    assertThat(processInstanceEvent).isCompleted();
    assertThat(processInstanceEvent).hasCompletedElements("success-end");
    assertThat(processInstanceEvent).hasVariables(variables);
  }

  @Test
  void shouldGiveClearErrorMessageWhenJobIsMissing() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithServiceTask());
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // Then
    Assertions.assertThatThrownBy(() -> processTestContext.completeJob("not-found"))
        .hasMessage(
            "Expected to complete a job with the type 'not-found' " + "but no job is available.");
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
    assertThat(processInstanceEvent).isCompleted();
    assertThat(processInstanceEvent).hasCompletedElements("error-end");
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
    assertThat(processInstanceEvent).isCompleted();
    assertThat(processInstanceEvent).hasCompletedElements("error-end");

    final Map<String, Object> expectedVariables = new HashMap<>();
    expectedVariables.put("error_code", 123);
    assertThat(processInstanceEvent).hasVariables(expectedVariables);
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
    assertThat(processInstanceEvent).isCompleted();
    assertThat(processInstanceEvent).hasCompletedElements("success-end");
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
    assertThat(processInstanceEvent).isCompleted();
    assertThat(processInstanceEvent).hasCompletedElements("success-end");
    assertThat(processInstanceEvent).hasVariables(variables);
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
    assertThat(processInstanceEvent).isCompleted();
    assertThat(processInstanceEvent).hasCompletedElements("success-end");
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
    assertThat(processInstanceEvent).isCompleted();
    assertThat(processInstanceEvent).hasCompletedElements("success-end");
    assertThat(processInstanceEvent).hasVariables(variables);
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
    assertThat(processInstanceEvent).isCompleted();
    assertThat(processInstanceEvent).hasCompletedElements("success-end");
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
    assertThat(processInstanceEvent).isCompleted();
    assertThat(processInstanceEvent).hasCompletedElements("success-end");
    assertThat(processInstanceEvent).hasVariables(variables);
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
    assertThat(processInstanceEvent).isCompleted();
    assertThat(processInstanceEvent).hasCompletedElements("success-end");
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
    assertThat(processInstanceEvent).isCompleted();
    assertThat(processInstanceEvent).hasCompletedElements("success-end");
    assertThat(processInstanceEvent).hasVariables(variables);
  }

  @Test
  void shouldGiveHelpfulErrorMessageWhenCompleteUserTaskFails() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithUserTask());
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // When
    Assertions.assertThatThrownBy(
            () ->
                processTestContext.completeUserTask(UserTaskSelectors.byElementId("unknown-task")))
        .hasMessage("Expected to complete user task [unknown-task] but no job is available.");
  }

  @Test
  void shouldMockChildProcess() {
    // Given
    final long subprocessDefinitionKey = deployProcessModel(childProcessModel());
    final long processDefinitionKey = deployProcessModel(processModelWithChildProcess());

    // When
    processTestContext.mockChildProcess("child-process-1");
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // Then
    assertThat(processInstanceEvent).isCompleted();
    assertThat(processInstanceEvent).hasCompletedElements("success-end");
  }

  @Test
  void shouldMockChildProcessWithVariables() {
    // Given
    final long subprocessDefinitionKey = deployProcessModel(childProcessModel());
    final long processDefinitionKey = deployProcessModel(processModelWithChildProcess());
    final Map<String, Object> variables = new HashMap<>();
    variables.put("abc", 123);
    final Map<String, Object> mapValue = new HashMap<>();
    mapValue.put("def", 4554534);
    variables.put("mapKey", mapValue);

    // When
    processTestContext.mockChildProcess("child-process-1", variables);
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // Then
    assertThat(processInstanceEvent).isCompleted();
    assertThat(processInstanceEvent).hasCompletedElements("success-end");
    assertThat(processInstanceEvent).hasVariables(variables);
  }

  @Test
  void shouldFindUserTaskByElementId() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithUserTask());

    client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // Then
    assertThat(UserTaskSelectors.byElementId("user-task-1")).isCreated();
  }

  @Test
  void shouldFindUserTaskByElementIdAndProcessInstanceKey() {
    // Given
    final long firstInstanceKey = deployProcessModel(processModelWithUserTask());
    final long secondInstanceKey = deployProcessModel(processModelWithUserTask());
    final long thirdInstanceKey = deployProcessModel(processModelWithUserTask());

    client.newCreateInstanceCommand().processDefinitionKey(firstInstanceKey).send().join();
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(secondInstanceKey).send().join();
    client.newCreateInstanceCommand().processDefinitionKey(thirdInstanceKey).send().join();

    // Then
    assertThat(
            UserTaskSelectors.byElementId(
                "user-task-1", processInstanceEvent.getProcessInstanceKey()))
        .hasProcessInstanceKey(processInstanceEvent.getProcessInstanceKey());
  }

  @Test
  void shouldFindUserTaskByTaskName() {
    // Given
    final long processDefinitionKey = deployProcessModel(processModelWithUserTask());

    client.newCreateInstanceCommand().processDefinitionKey(processDefinitionKey).send().join();

    // Then
    assertThat(UserTaskSelectors.byTaskName("user-task")).isCreated();
  }

  @Test
  void shouldFindUserTaskByTaskNameAndProcessInstanceKey() {
    // Given
    final long firstInstanceKey = deployProcessModel(processModelWithUserTask());
    final long secondInstanceKey = deployProcessModel(processModelWithUserTask());
    final long thirdInstanceKey = deployProcessModel(processModelWithUserTask());

    client.newCreateInstanceCommand().processDefinitionKey(firstInstanceKey).send().join();
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().processDefinitionKey(secondInstanceKey).send().join();
    client.newCreateInstanceCommand().processDefinitionKey(thirdInstanceKey).send().join();

    // Then
    assertThat(
            UserTaskSelectors.byTaskName("user-task", processInstanceEvent.getProcessInstanceKey()))
        .hasProcessInstanceKey(processInstanceEvent.getProcessInstanceKey());
  }

  @Test
  void shouldFindBusinessRuleTaskByName() {
    // Given
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

    // Then
    final Map<String, Object> expectedOutput = new HashMap<>();
    expectedOutput.put("jedi_or_sith", "jedi");
    expectedOutput.put("force_user", "Mace");

    assertThat(DecisionSelectors.byName("Jedi or Sith")).isEvaluated().hasOutput(expectedOutput);
  }

  @Test
  void shouldFindBusinessRuleTaskById() {
    // Given
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

    // Then
    final Map<String, Object> expectedResult = new HashMap<>();
    expectedResult.put("jedi_or_sith", "jedi");
    expectedResult.put("force_user", "Mace");

    assertThat(DecisionSelectors.byId("jedi_or_sith"))
        .isEvaluated()
        .hasOutput(expectedResult)
        .hasMatchedRules(1);
  }

  @Test
  void shouldFindBusinessRuleTaskByProcessInstanceKey() {
    // Given
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("dmn/decision-table-unique.dmn")
        .send()
        .join();

    final long processDefinitionKey = deployProcessModel(processModelWithBusinessRuleTask());

    final ProcessInstanceEvent processInstanceEvent =
        client
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .variable("lightsaber_color", "blue")
            .send()
            .join();

    // Then
    final Map<String, Object> expectedResult = new HashMap<>();
    expectedResult.put("jedi_or_sith", "jedi");
    expectedResult.put("force_user", "Mace");

    assertThat(DecisionSelectors.byProcessInstanceKey(processInstanceEvent.getProcessInstanceKey()))
        .isEvaluated()
        .hasOutput(expectedResult)
        .hasMatchedRules(1);
  }

  @Test
  void shouldMatchBusinessRuleWithStringResult() {
    // Given
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("dmn/simple-decision-table.dmn")
        .send()
        .join();

    final long processDefinitionKey =
        deployProcessModel(processModelWithBusinessRuleTask("simple_decision", "result"));

    final ProcessInstanceEvent processInstanceEvent =
        client
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .variable("yes_or_no", "yes")
            .send()
            .join();

    // Then
    assertThat(DecisionSelectors.byProcessInstanceKey(processInstanceEvent.getProcessInstanceKey()))
        .isEvaluated()
        .hasOutput(":)")
        .hasMatchedRules(1);
  }

  @Test
  void shouldMatchBusinessRuleWithListResult() {
    // Given
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("dmn/decision-table-collect.dmn")
        .send()
        .join();

    final long processDefinitionKey = deployProcessModel(processModelWithBusinessRuleTask());

    final ProcessInstanceEvent processInstanceEvent =
        client
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .variable("lightsaber_color", "green")
            .send()
            .join();

    // Then
    final Map<String, Object> firstMatch = new HashMap<>();
    firstMatch.put("jedi_or_sith", "jedi");
    firstMatch.put("force_user", "Anakin");
    final Map<String, Object> secondMatch = new HashMap<>();
    secondMatch.put("jedi_or_sith", "sith");
    secondMatch.put("force_user", "Mace");

    assertThat(DecisionSelectors.byProcessInstanceKey(processInstanceEvent.getProcessInstanceKey()))
        .isEvaluated()
        .hasOutput(Arrays.asList(firstMatch, secondMatch))
        .hasMatchedRules(2, 3);
  }

  @Test
  void shouldMatchBusinessRuleWithListResultOfDifferentTypes() {
    // Given
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("dmn/simple-decision-table-collect.dmn")
        .send()
        .join();

    final long processDefinitionKey =
        deployProcessModel(processModelWithBusinessRuleTask("simple_decision", "result"));

    final ProcessInstanceEvent processInstanceEvent =
        client
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .variable("yes_or_no", "yes")
            .send()
            .join();

    // Then
    final List<Object> expectedOutput = new ArrayList<>();
    expectedOutput.add(":)");
    expectedOutput.add(1);

    assertThat(DecisionSelectors.byProcessInstanceKey(processInstanceEvent.getProcessInstanceKey()))
        .isEvaluated()
        .hasOutput(expectedOutput)
        .hasMatchedRules(1, 3);
  }

  @Test
  void shouldEvaluateSimpleDecision() {
    // Given
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("dmn/simple-decision-table.dmn")
        .send()
        .join();

    final Map<String, Object> variables = new HashMap<>();
    variables.put("yes_or_no", "yes");

    // when
    final EvaluateDecisionResponse response =
        evaluateDecisionByDecisionId("simple_decision", variables);

    // Then
    assertThat(response).isEvaluated().hasOutput(":)").hasMatchedRules(1);
  }

  @Test
  void shouldEvaluateComplexDecision() {
    // Given
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("dmn/complex-decision-table.dmn")
        .send()
        .join();

    final Map<String, Object> variables = new HashMap<>();
    variables.put("color", "green");
    variables.put("language", "german");

    // when
    final EvaluateDecisionResponse response =
        evaluateDecisionByDecisionId("decision_overall_happiness", variables);

    // Then
    assertThat(response).isEvaluated().hasOutput("pretty happy").hasMatchedRules(3);

    assertThat(DecisionSelectors.byId("decision_color_happiness"))
        .isEvaluated()
        .hasOutput(90)
        .hasMatchedRules(2);

    assertThat(DecisionSelectors.byName("Language Happiness"))
        .isEvaluated()
        .hasOutput(10)
        .hasMatchedRules(1);
  }

  @Test
  void unableToEvaluateDecision() {
    // Given
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("dmn/faulty-decision-table.dmn")
        .send()
        .join();

    final Map<String, Object> variables = new HashMap<>();
    variables.put("color", "green");
    variables.put("language", "german");

    // when
    final EvaluateDecisionResponse response =
        evaluateDecisionByDecisionId("decision_overall_happiness", variables);

    // Then
    Assertions.assertThatThrownBy(() -> assertThat(response).isEvaluated())
        .hasMessage(
            "No DecisionInstance [Determine Overall Happiness "
                + "(decisionId: decision_overall_happiness)] "
                + "found.");

    Assertions.assertThatThrownBy(
            () -> assertThat(DecisionSelectors.byId("decision_overall_happiness")).isEvaluated())
        .hasMessage("No DecisionInstance [decision_overall_happiness] found.");
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

  private long deployDmnModel(final DmnModelInstance dmnModel) {
    final DeploymentEvent deploymentEvent =
        client
            .newDeployResourceCommand()
            .addResourceStream(
                new ByteArrayInputStream(Dmn.convertToString(dmnModel).getBytes()),
                "test-decision.dmn")
            .send()
            .join();
    return deploymentEvent.getDecisions().stream().findFirst().get().getDecisionKey();
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

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\"test_value\"",
        "[]",
        "[1, 2, 3]",
        "[\"a\", \"b\", \"c\"]",
        "{}",
        "{\"key\": \"value\"}",
        "{\"key\": 1, \"foo\": \"bar\", \"list\": [1, 2, 3, \"a\", \"b\", \"c\"]}"
      })
  void shouldMockBusinessRule(final String decisionOutputJson) {

    // Given
    final BpmnModelInstance instance = processModelWithBusinessRule();
    final long processDefinitionKey = deployProcessModel(instance);

    final String decisionId = "decision-id-1";

    final DmnModelInstance dmnModel = dmnModelWithBusinessRule(decisionId);
    deployDmnModel(dmnModel);

    final Map<String, Object> inputVariables = new HashMap<>();
    inputVariables.put("experience", 11);
    inputVariables.put("type", "luxury");

    final Object decisionOutput = AssertionJsonMapper.readJson(decisionOutputJson);

    // When
    processTestContext.mockDmnDecision(decisionId, decisionOutput);

    final ProcessInstanceEvent processInstanceEvent =
        client
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .variables(inputVariables)
            .send()
            .join();

    // Then
    assertThat(processInstanceEvent).isCompleted();
    final Map<String, Object> expectedVariables = new HashMap<>();
    expectedVariables.put("result", decisionOutput);
    assertThat(processInstanceEvent).hasVariables(expectedVariables);
  }

  private BpmnModelInstance processModelWithBusinessRule() {
    return Bpmn.createExecutableProcess("test-process-business-rule")
        .startEvent("start-1")
        .businessRuleTask(
            "br-task",
            builder -> builder.zeebeCalledDecisionId("decision-id-1").zeebeResultVariable("result"))
        .endEvent("success-end")
        .done();
  }

  private DmnModelInstance dmnModelWithBusinessRule(final String decisionId) {
    // Create an empty DMN model
    final DmnModelInstance modelInstance = Dmn.createEmptyModel();

    // Create and configure the definitions element
    final Definitions definitions = modelInstance.newInstance(Definitions.class);
    definitions.setName("DRD");
    definitions.setNamespace("http://camunda.org/schema/1.0/dmn");
    modelInstance.setDefinitions(definitions);

    // Create the decision element
    final Decision decision = modelInstance.newInstance(Decision.class);
    decision.setId(decisionId);
    decision.setName("Decision 1");
    definitions.addChildElement(decision);

    // Create the decision table
    final DecisionTable decisionTable = modelInstance.newInstance(DecisionTable.class);
    decision.addChildElement(decisionTable);

    // Add input clauses
    final Input inputExperience = modelInstance.newInstance(Input.class);
    final InputExpression inputExpressionExperience =
        modelInstance.newInstance(InputExpression.class);
    final Text textExperience = modelInstance.newInstance(Text.class);
    textExperience.setTextContent("experience");
    inputExpressionExperience.setText(textExperience);
    inputExperience.setInputExpression(inputExpressionExperience);
    decisionTable.addChildElement(inputExperience);

    final Input inputType = modelInstance.newInstance(Input.class);
    final InputExpression inputExpressionType = modelInstance.newInstance(InputExpression.class);
    final Text textElementType = modelInstance.newInstance(Text.class);
    textElementType.setTextContent("type");
    inputExpressionType.setText(textElementType);
    inputType.setInputExpression(inputExpressionType);
    decisionTable.addChildElement(inputType);

    // Add output clauses
    final Output outputCode = modelInstance.newInstance(Output.class);
    outputCode.setName("code");
    decisionTable.addChildElement(outputCode);

    final Output outputDescription = modelInstance.newInstance(Output.class);
    outputDescription.setName("description");
    decisionTable.addChildElement(outputDescription);

    // Add rules
    final Rule rule1 = modelInstance.newInstance(Rule.class);
    final InputEntry rule1InputExperience = modelInstance.newInstance(InputEntry.class);
    final Text rule1TextExperience = modelInstance.newInstance(Text.class);
    rule1TextExperience.setTextContent(">10");
    rule1InputExperience.setText(rule1TextExperience);
    rule1.addChildElement(rule1InputExperience);
    final InputEntry rule1InputCode = modelInstance.newInstance(InputEntry.class);
    final Text rule1TextType = modelInstance.newInstance(Text.class);
    rule1TextType.setTextContent("\"luxury\"");
    rule1InputCode.setText(rule1TextType);
    rule1.addChildElement(rule1InputCode);
    final OutputEntry rule1OutputCode = modelInstance.newInstance(OutputEntry.class);
    final Text rule1TextCode = modelInstance.newInstance(Text.class);
    rule1TextCode.setTextContent("\"green\"");
    rule1OutputCode.setText(rule1TextCode);
    rule1.addChildElement(rule1OutputCode);
    final OutputEntry rule1OutputDescription = modelInstance.newInstance(OutputEntry.class);
    final Text rule1TextDescription = modelInstance.newInstance(Text.class);
    rule1TextDescription.setTextContent("\"Green\"");
    rule1OutputDescription.setText(rule1TextDescription);
    rule1.addChildElement(rule1OutputDescription);
    decisionTable.addChildElement(rule1);

    final Rule rule2 = modelInstance.newInstance(Rule.class);
    final InputEntry rule2InputExperience = modelInstance.newInstance(InputEntry.class);
    final Text rule2TextExperience = modelInstance.newInstance(Text.class);
    rule2TextExperience.setTextContent("<=10");
    rule2InputExperience.setText(rule2TextExperience);
    rule2.addChildElement(rule2InputExperience);
    final InputEntry rule2InputType = modelInstance.newInstance(InputEntry.class);
    final Text rule2TextType = modelInstance.newInstance(Text.class);
    rule2TextType.setTextContent("\"standard\"");
    rule2InputType.setText(rule2TextType);
    rule2.addChildElement(rule2InputType);
    final OutputEntry rule2OutputCode = modelInstance.newInstance(OutputEntry.class);
    final Text rule2TextCode = modelInstance.newInstance(Text.class);
    rule2TextCode.setTextContent("\"red\"");
    rule2OutputCode.setText(rule2TextCode);
    rule2.addChildElement(rule2OutputCode);
    final OutputEntry rule2OutputEntryDescription = modelInstance.newInstance(OutputEntry.class);
    final Text rule2TextDescription = modelInstance.newInstance(Text.class);
    rule2TextDescription.setTextContent("\"Red\"");
    rule2OutputEntryDescription.setText(rule2TextDescription);
    rule2.addChildElement(rule2OutputEntryDescription);
    decisionTable.addChildElement(rule2);
    return modelInstance;
  }

  private BpmnModelInstance processModelWithBusinessRuleTask() {
    return processModelWithBusinessRuleTask("jedi_or_sith", "jedi_or_sith");
  }

  private BpmnModelInstance processModelWithBusinessRuleTask(
      final String decisionId, final String resultVariable) {
    return Bpmn.createExecutableProcess("test-process")
        .startEvent("start-1")
        .businessRuleTask("business-rule-1")
        .zeebeCalledDecisionId(decisionId)
        .zeebeResultVariable(resultVariable)
        .userTask("user-task-1")
        .endEvent("success-end")
        .done();
  }

  private EvaluateDecisionResponse evaluateDecisionByDecisionId(
      final String decisionId, final Map<String, Object> variables) {
    return client
        .newEvaluateDecisionCommand()
        .decisionId(decisionId)
        .variables(variables)
        .send()
        .join();
  }
}
