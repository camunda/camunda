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
package io.camunda.process.test.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.camunda.process.test.api.dsl.ImmutableElementSelector;
import io.camunda.process.test.api.dsl.ImmutableProcessDefinitionSelector;
import io.camunda.process.test.api.dsl.ImmutableProcessInstanceSelector;
import io.camunda.process.test.api.dsl.ImmutableTestCase;
import io.camunda.process.test.api.dsl.ImmutableTestScenario;
import io.camunda.process.test.api.dsl.ImmutableUserTaskSelector;
import io.camunda.process.test.api.dsl.TestCaseInstruction;
import io.camunda.process.test.api.dsl.TestScenario;
import io.camunda.process.test.api.dsl.instructions.ImmutableAssertElementInstanceInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableAssertElementInstancesInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableAssertProcessInstanceInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableAssertUserTaskInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableAssertVariablesInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableCompleteUserTaskInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableCreateProcessInstanceInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableMockChildProcessInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableMockDmnDecisionInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableMockJobWorkerCompleteJobInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutablePublishMessageInstruction;
import io.camunda.process.test.api.dsl.instructions.assertElementInstance.ElementInstanceState;
import io.camunda.process.test.api.dsl.instructions.assertElementInstances.ElementInstancesState;
import io.camunda.process.test.api.dsl.instructions.assertProcessInstance.ProcessInstanceState;
import io.camunda.process.test.api.dsl.instructions.assertUserTask.UserTaskState;
import io.camunda.process.test.api.dsl.instructions.createProcessInstance.ImmutableCreateProcessInstanceStartInstruction;
import io.camunda.process.test.api.dsl.instructions.createProcessInstance.ImmutableCreateProcessInstanceTerminateRuntimeInstruction;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class PojoCompatibilityTest {

  private static final String DSL_SCHEMA = "/schema/test-scenario-dsl.schema.json";

  private static JsonSchema jsonSchema;

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
          .registerModule(new Jdk8Module());

  @BeforeAll
  static void setup() {
    final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4);
    jsonSchema = factory.getSchema(PojoCompatibilityTest.class.getResourceAsStream(DSL_SCHEMA));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("testScenarios")
  void shouldBeValidScenario(final String name, final TestScenario testScenario) {
    // given
    final JsonNode json = objectMapper.valueToTree(testScenario);

    // when
    final Set<ValidationMessage> errors = jsonSchema.validate(json);

    // then
    assertThat(errors)
        .describedAs("Scenario '%s' should have no validation errors", name)
        .isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("testScenarios")
  void shouldDeserializeScenario(final String name, final TestScenario testScenario)
      throws IOException {
    // given
    final String json = objectMapper.writeValueAsString(testScenario);

    // when
    final TestScenario deserializedObject = objectMapper.readValue(json, TestScenario.class);

    // then
    assertThat(deserializedObject)
        .describedAs("Scenario '%s' should deserialize correctly", name)
        .isEqualTo(testScenario);
  }

  static Stream<Arguments> testScenarios() {
    return Stream.of(
        Arguments.of("no test cases", ImmutableTestScenario.builder().build()),
        Arguments.of(
            "test case: minimal",
            ImmutableTestScenario.builder()
                .addTestCases(ImmutableTestCase.builder().name("test case 1").build())
                .build()),
        Arguments.of(
            "test case: full",
            ImmutableTestScenario.builder()
                .addTestCases(
                    ImmutableTestCase.builder()
                        .name("test case 1")
                        .description("My first test case")
                        .build())
                .build()),
        // ===== CREATE_PROCESS_INSTANCE =====
        Arguments.of(
            "create process instance: minimal",
            singleTestCase(
                ImmutableCreateProcessInstanceInstruction.builder()
                    .processDefinitionSelector(
                        ImmutableProcessDefinitionSelector.builder()
                            .processDefinitionId("my-process")
                            .build())
                    .build())),
        Arguments.of(
            "create process instance: full",
            singleTestCase(
                ImmutableCreateProcessInstanceInstruction.builder()
                    .processDefinitionSelector(
                        ImmutableProcessDefinitionSelector.builder()
                            .processDefinitionId("my-process")
                            .build())
                    .putVariables("orderId", 12345)
                    .addStartInstructions(
                        ImmutableCreateProcessInstanceStartInstruction.builder()
                            .elementId("task1")
                            .build())
                    .addRuntimeInstructions(
                        ImmutableCreateProcessInstanceTerminateRuntimeInstruction.builder()
                            .afterElementId("task2")
                            .build())
                    .build())),
        // ===== ASSERT_PROCESS_INSTANCE =====
        Arguments.of(
            "assert process instance: minimal",
            singleTestCase(
                ImmutableAssertProcessInstanceInstruction.builder()
                    .processInstanceSelector(
                        ImmutableProcessInstanceSelector.builder()
                            .processDefinitionId("my-process")
                            .build())
                    .build())),
        Arguments.of(
            "assert process instance: full",
            singleTestCase(
                ImmutableAssertProcessInstanceInstruction.builder()
                    .processInstanceSelector(
                        ImmutableProcessInstanceSelector.builder()
                            .processDefinitionId("my-process")
                            .build())
                    .state(ProcessInstanceState.IS_COMPLETED)
                    .hasActiveIncidents(false)
                    .build())),
        // ===== ASSERT_ELEMENT_INSTANCE =====
        Arguments.of(
            "assert element instance: minimal with elementId",
            singleTestCase(
                ImmutableAssertElementInstanceInstruction.builder()
                    .processInstanceSelector(
                        ImmutableProcessInstanceSelector.builder()
                            .processDefinitionId("my-process")
                            .build())
                    .elementSelector(ImmutableElementSelector.builder().elementId("task1").build())
                    .state(ElementInstanceState.IS_ACTIVE)
                    .build())),
        Arguments.of(
            "assert element instance: minimal with elementName",
            singleTestCase(
                ImmutableAssertElementInstanceInstruction.builder()
                    .processInstanceSelector(
                        ImmutableProcessInstanceSelector.builder()
                            .processDefinitionId("my-process")
                            .build())
                    .elementSelector(
                        ImmutableElementSelector.builder().elementName("Task A").build())
                    .state(ElementInstanceState.IS_COMPLETED)
                    .build())),
        Arguments.of(
            "assert element instance: full",
            singleTestCase(
                ImmutableAssertElementInstanceInstruction.builder()
                    .processInstanceSelector(
                        ImmutableProcessInstanceSelector.builder()
                            .processDefinitionId("my-process")
                            .build())
                    .elementSelector(ImmutableElementSelector.builder().elementId("task1").build())
                    .state(ElementInstanceState.IS_TERMINATED)
                    .amount(3)
                    .build())),
        // ===== ASSERT_USER_TASK =====
        Arguments.of(
            "assert user task: minimal",
            singleTestCase(
                ImmutableAssertUserTaskInstruction.builder()
                    .userTaskSelector(
                        ImmutableUserTaskSelector.builder().elementId("task1").build())
                    .build())),
        Arguments.of(
            "assert user task: full",
            singleTestCase(
                ImmutableAssertUserTaskInstruction.builder()
                    .userTaskSelector(
                        ImmutableUserTaskSelector.builder()
                            .processDefinitionId("my-process")
                            .elementId("task1")
                            .build())
                    .state(UserTaskState.IS_CREATED)
                    .assignee("me")
                    .candidateGroups(Arrays.asList("manager"))
                    .priority(100)
                    .elementId("task1")
                    .name("Review")
                    .dueDate("2025-10-31T10:00:00Z")
                    .followUpDate("2025-10-20T10:00:00Z")
                    .build())),
        // ===== ASSERT_ELEMENT_INSTANCES =====
        Arguments.of(
            "assert element instances: IS_ACTIVE",
            singleTestCase(
                ImmutableAssertElementInstancesInstruction.builder()
                    .processInstanceSelector(
                        ImmutableProcessInstanceSelector.builder()
                            .processDefinitionId("my-process")
                            .build())
                    .addElementSelectors(
                        ImmutableElementSelector.builder().elementId("task1").build())
                    .addElementSelectors(
                        ImmutableElementSelector.builder().elementName("Task B").build())
                    .state(ElementInstancesState.IS_ACTIVE)
                    .build())),
        Arguments.of(
            "assert element instances: IS_COMPLETED_IN_ORDER",
            singleTestCase(
                ImmutableAssertElementInstancesInstruction.builder()
                    .processInstanceSelector(
                        ImmutableProcessInstanceSelector.builder()
                            .processDefinitionId("my-process")
                            .build())
                    .addElementSelectors(
                        ImmutableElementSelector.builder().elementId("task1").build())
                    .addElementSelectors(
                        ImmutableElementSelector.builder().elementId("task2").build())
                    .state(ElementInstancesState.IS_COMPLETED_IN_ORDER)
                    .build())),
        // ===== COMPLETE_USER_TASK =====
        Arguments.of(
            "complete user task: minimal",
            singleTestCase(
                ImmutableCompleteUserTaskInstruction.builder()
                    .userTaskSelector(
                        ImmutableUserTaskSelector.builder().elementId("task1").build())
                    .build())),
        Arguments.of(
            "complete user task: with variables",
            singleTestCase(
                ImmutableCompleteUserTaskInstruction.builder()
                    .userTaskSelector(
                        ImmutableUserTaskSelector.builder().taskName("Approve Request").build())
                    .putVariables("approved", true)
                    .putVariables("comment", "Looks good")
                    .build())),
        Arguments.of(
            "complete user task: with example data",
            singleTestCase(
                ImmutableCompleteUserTaskInstruction.builder()
                    .userTaskSelector(
                        ImmutableUserTaskSelector.builder()
                            .processDefinitionId("my-process")
                            .elementId("task1")
                            .build())
                    .useExampleData(true)
                    .build())),
        // ===== PUBLISH_MESSAGE =====
        Arguments.of(
            "publish message: minimal",
            singleTestCase(ImmutablePublishMessageInstruction.builder().name("message1").build())),
        Arguments.of(
            "publish message: full",
            singleTestCase(
                ImmutablePublishMessageInstruction.builder()
                    .name("message1")
                    .correlationKey("order-12345")
                    .putVariables("orderId", 12345)
                    .timeToLive(60000L)
                    .messageId("msg-123")
                    .build())),
        // ===== MOCK_CHILD_PROCESS =====
        Arguments.of(
            "mock child process: minimal",
            singleTestCase(
                ImmutableMockChildProcessInstruction.builder()
                    .processDefinitionId("child-process")
                    .build())),
        Arguments.of(
            "mock child process: with variables",
            singleTestCase(
                ImmutableMockChildProcessInstruction.builder()
                    .processDefinitionId("payment-process")
                    .putVariables("amount", 100.0)
                    .putVariables("currency", "USD")
                    .build())),
        // ===== MOCK_JOB_WORKER_COMPLETE_JOB =====
        Arguments.of(
            "mock job worker complete job: minimal",
            singleTestCase(
                ImmutableMockJobWorkerCompleteJobInstruction.builder()
                    .jobType("send-email")
                    .build())),
        Arguments.of(
            "mock job worker complete job: with variables",
            singleTestCase(
                ImmutableMockJobWorkerCompleteJobInstruction.builder()
                    .jobType("send-email")
                    .putVariables("status", "sent")
                    .build())),
        Arguments.of(
            "mock job worker complete job: with example data",
            singleTestCase(
                ImmutableMockJobWorkerCompleteJobInstruction.builder()
                    .jobType("fetch-weather-data")
                    .useExampleData(true)
                    .build())),
        // ===== MOCK_DMN_DECISION =====
        Arguments.of(
            "mock dmn decision: minimal",
            singleTestCase(
                ImmutableMockDmnDecisionInstruction.builder()
                    .decisionDefinitionId("credit-check-decision")
                    .build())),
        Arguments.of(
            "mock dmn decision: with variables",
            singleTestCase(
                ImmutableMockDmnDecisionInstruction.builder()
                    .decisionDefinitionId("credit-check-decision")
                    .putVariables("approved", true)
                    .putVariables("score", 750)
                    .build())),
        // ===== ASSERT_VARIABLES =====
        Arguments.of(
            "assert variables: minimal",
            singleTestCase(
                ImmutableAssertVariablesInstruction.builder()
                    .processInstanceSelector(
                        ImmutableProcessInstanceSelector.builder()
                            .processDefinitionId("my-process")
                            .build())
                    .build())),
        Arguments.of(
            "assert variables: complete",
            singleTestCase(
                ImmutableAssertVariablesInstruction.builder()
                    .processInstanceSelector(
                        ImmutableProcessInstanceSelector.builder()
                            .processDefinitionId("my-process")
                            .build())
                    .elementSelector(ImmutableElementSelector.builder().elementId("task_A").build())
                    .addVariableNames("var1", "var2")
                    .putVariables("x", 3)
                    .putVariables("y", "okay")
                    .build()))
        // add new instructions here
        );
  }

  private static TestScenario singleTestCase(final TestCaseInstruction instruction) {
    return ImmutableTestScenario.builder()
        .addTestCases(ImmutableTestCase.builder().name("test").addInstructions(instruction).build())
        .build();
  }
}
