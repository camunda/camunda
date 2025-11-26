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
import io.camunda.process.test.api.dsl.ImmutableTestCase;
import io.camunda.process.test.api.dsl.ImmutableTestScenario;
import io.camunda.process.test.api.dsl.TestCaseInstructionType;
import io.camunda.process.test.api.dsl.TestScenario;
import io.camunda.process.test.api.dsl.instructions.ImmutableCreateProcessInstanceInstruction;
import java.io.IOException;
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
        Arguments.of(
            "create process instance: minimal",
            ImmutableTestScenario.builder()
                .addTestCases(
                    ImmutableTestCase.builder()
                        .name("test case 1")
                        .addInstructions(
                            ImmutableCreateProcessInstanceInstruction.builder()
                                .type(TestCaseInstructionType.CREATE_PROCESS_INSTANCE)
                                .build())
                        .build())
                .build()),
        Arguments.of(
            "create process instance: full",
            ImmutableTestScenario.builder()
                .addTestCases(
                    ImmutableTestCase.builder()
                        .name("test case 1")
                        .addInstructions(
                            ImmutableCreateProcessInstanceInstruction.builder()
                                .type(TestCaseInstructionType.CREATE_PROCESS_INSTANCE)
                                .putVariables("orderId", 12345)
                                .build())
                        .build())
                .build()));
  }
}
