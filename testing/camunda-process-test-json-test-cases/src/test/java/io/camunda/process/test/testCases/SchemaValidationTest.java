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
package io.camunda.process.test.testCases;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class SchemaValidationTest {

  private static final String JSON_SCHEMA_PATH = "/schema/cpt-test-cases/schema.json";

  private static JsonSchema jsonSchema;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeAll
  static void setup() {
    final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(VersionFlag.V7);
    jsonSchema =
        factory.getSchema(SchemaValidationTest.class.getResourceAsStream(JSON_SCHEMA_PATH));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"/empty-test-cases.json", "/example-test-cases.json", "/full-test-cases.json"})
  void shouldValidateTestCases(final String filePath) throws IOException {
    // given
    final JsonNode jsonNode = objectMapper.readTree(getClass().getResourceAsStream(filePath));

    // when
    final Set<ValidationMessage> errors = jsonSchema.validate(jsonNode);

    // then
    assertThat(errors).isEmpty();
  }

  @Test
  void shouldReportValidationErrors() throws IOException {
    // given
    final JsonNode jsonNode =
        objectMapper.readTree(getClass().getResourceAsStream("/invalid-test-cases.json"));

    // when
    final Set<ValidationMessage> errors = jsonSchema.validate(jsonNode);

    // then
    assertThat(errors)
        .isNotEmpty()
        .extracting(ValidationMessage::getMessage)
        .contains(
            "$.testCases[0].instructions[0]: must be valid to one and only one schema, but 0 are valid");
  }

  @Test
  void shouldRejectConditionalBehaviorWithEmptyActions() throws IOException {
    // given
    final String json =
        "{\"testCases\":[{\"name\":\"t\",\"instructions\":[{"
            + "\"type\":\"CONDITIONAL_BEHAVIOR\","
            + "\"conditions\":[{\"type\":\"ASSERT_USER_TASK\","
            + "\"userTaskSelector\":{\"elementId\":\"task1\"}}],"
            + "\"actions\":[]"
            + "}]}]}";
    final JsonNode jsonNode = objectMapper.readTree(json);

    // when
    final Set<ValidationMessage> errors = jsonSchema.validate(jsonNode);

    // then
    assertThat(errors)
        .extracting(ValidationMessage::getMessage)
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("$.testCases[0].instructions[0].actions")
                    .containsAnyOf("must have at least 1 items", "minItems"));
  }

  @Test
  void shouldRejectConditionalBehaviorWithEmptyConditions() throws IOException {
    // given
    final String json =
        "{\"testCases\":[{\"name\":\"t\",\"instructions\":[{"
            + "\"type\":\"CONDITIONAL_BEHAVIOR\","
            + "\"conditions\":[],"
            + "\"actions\":[{\"type\":\"COMPLETE_USER_TASK\","
            + "\"userTaskSelector\":{\"elementId\":\"task1\"}}]"
            + "}]}]}";
    final JsonNode jsonNode = objectMapper.readTree(json);

    // when
    final Set<ValidationMessage> errors = jsonSchema.validate(jsonNode);

    // then
    assertThat(errors)
        .extracting(ValidationMessage::getMessage)
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("$.testCases[0].instructions[0].conditions")
                    .containsAnyOf("must have at least 1 items", "minItems"));
  }

  @Test
  void shouldRejectConditionalBehaviorWithNonAssertCondition() throws IOException {
    // given - a CREATE_PROCESS_INSTANCE used as a condition is not allowed
    final String json =
        "{\"testCases\":[{\"name\":\"t\",\"instructions\":[{"
            + "\"type\":\"CONDITIONAL_BEHAVIOR\","
            + "\"conditions\":[{\"type\":\"CREATE_PROCESS_INSTANCE\","
            + "\"processDefinitionSelector\":{\"processDefinitionId\":\"p\"}}],"
            + "\"actions\":[{\"type\":\"COMPLETE_USER_TASK\","
            + "\"userTaskSelector\":{\"elementId\":\"task1\"}}]"
            + "}]}]}";
    final JsonNode jsonNode = objectMapper.readTree(json);

    // when
    final Set<ValidationMessage> errors = jsonSchema.validate(jsonNode);

    // then
    assertThat(errors)
        .extracting(ValidationMessage::getMessage)
        .contains(
            "$.testCases[0].instructions[0].conditions[0]: must be valid to one and only one schema, but 0 are valid");
  }

  @Test
  void shouldRejectConditionalBehaviorWithDisallowedAction() throws IOException {
    // given - a CREATE_PROCESS_INSTANCE used as an action is not allowed
    final String json =
        "{\"testCases\":[{\"name\":\"t\",\"instructions\":[{"
            + "\"type\":\"CONDITIONAL_BEHAVIOR\","
            + "\"conditions\":[{\"type\":\"ASSERT_USER_TASK\","
            + "\"userTaskSelector\":{\"elementId\":\"task1\"}}],"
            + "\"actions\":[{\"type\":\"CREATE_PROCESS_INSTANCE\","
            + "\"processDefinitionSelector\":{\"processDefinitionId\":\"p\"}}]"
            + "}]}]}";
    final JsonNode jsonNode = objectMapper.readTree(json);

    // when
    final Set<ValidationMessage> errors = jsonSchema.validate(jsonNode);

    // then
    assertThat(errors)
        .extracting(ValidationMessage::getMessage)
        .contains(
            "$.testCases[0].instructions[0].actions[0]: must be valid to one and only one schema, but 0 are valid");
  }
}
