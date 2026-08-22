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
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class SchemaValidationTest {

  private static final String JSON_SCHEMA_PATH = "/schema/cpt-test-cases/schema.json";

  private static Schema jsonSchema;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeAll
  static void setup() {
    final SchemaRegistry schemaRegistry = SchemaRegistry.withDialect(Dialects.getDraft7());
    jsonSchema =
        schemaRegistry.getSchema(SchemaValidationTest.class.getResourceAsStream(JSON_SCHEMA_PATH));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"/empty-test-cases.json", "/example-test-cases.json", "/full-test-cases.json"})
  void shouldValidateTestCases(final String filePath) throws IOException {
    // given
    final JsonNode jsonNode = objectMapper.readTree(getClass().getResourceAsStream(filePath));
    final String jsonString = objectMapper.writeValueAsString(jsonNode);

    // when
    final List<Error> errors = jsonSchema.validate(jsonString, InputFormat.JSON);

    // then
    assertThat(errors).isEmpty();
  }

  @Test
  void shouldReportValidationErrors() throws IOException {
    // given
    final JsonNode jsonNode =
        objectMapper.readTree(getClass().getResourceAsStream("/invalid-test-cases.json"));

    final String jsonString = objectMapper.writeValueAsString(jsonNode);

    // when
    final List<Error> errors = jsonSchema.validate(jsonString, InputFormat.JSON);

    // then
    assertThat(errors)
        .isNotEmpty()
        .extracting(error -> error.getInstanceLocation().toString(), Error::getMessage)
        .contains(
            tuple(
                "/testCases/0/instructions/0",
                "must be valid to one and only one schema, but 0 are valid"));
  }

  @Test
  void shouldValidateAssertIncidentInstruction() {
    // given
    final String json =
        "{\"testCases\":[{\"name\":\"assert incident\",\"instructions\":[{"
            + "\"type\":\"ASSERT_INCIDENT\","
            + "\"incidentSelector\":{\"elementId\":\"payment-task\"},"
            + "\"state\":\"IS_ACTIVE\","
            + "\"errorType\":\"JOB_NO_RETRIES\","
            + "\"errorMessage\":\"Payment worker failed\","
            + "\"elementId\":\"payment-task\""
            + "}]}]}";

    // when
    final List<Error> errors = jsonSchema.validate(json, InputFormat.JSON);

    // then
    assertThat(errors).isEmpty();
  }

  @Test
  void shouldRejectAssertIncidentWithoutSelector() {
    // given
    final String json =
        "{\"testCases\":[{\"name\":\"assert incident\",\"instructions\":[{"
            + "\"type\":\"ASSERT_INCIDENT\","
            + "\"state\":\"IS_ACTIVE\""
            + "}]}]}";

    // when
    final List<Error> errors = jsonSchema.validate(json, InputFormat.JSON);

    // then
    assertThat(errors).isNotEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"state\":\"INVALID\"", "\"errorType\":\"INVALID\""})
  void shouldRejectAssertIncidentWithInvalidEnum(final String invalidProperty) {
    // given
    final String json =
        "{\"testCases\":[{\"name\":\"assert incident\",\"instructions\":[{"
            + "\"type\":\"ASSERT_INCIDENT\","
            + "\"incidentSelector\":{\"elementId\":\"payment-task\"},"
            + invalidProperty
            + "}]}]}";

    // when
    final List<Error> errors = jsonSchema.validate(json, InputFormat.JSON);

    // then
    assertThat(errors).isNotEmpty();
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

    // when
    final List<Error> errors = jsonSchema.validate(json, InputFormat.JSON);

    // then
    assertThat(errors)
        .extracting(error -> error.getInstanceLocation().toString(), Error::getMessage)
        .contains(
            tuple("/testCases/0/instructions/0/actions", "must have at least 1 items but found 0"));
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

    // when
    final List<Error> errors = jsonSchema.validate(json, InputFormat.JSON);

    // then
    assertThat(errors)
        .extracting(error -> error.getInstanceLocation().toString(), Error::getMessage)
        .contains(
            tuple(
                "/testCases/0/instructions/0/conditions",
                "must have at least 1 items but found 0"));
  }

  @Test
  void shouldRejectConditionalBehaviorWithConditionalBehaviorConditions() throws IOException {
    // given
    final String json =
        "{\"testCases\":[{\"name\":\"t\",\"instructions\":[{"
            + "\"type\":\"CONDITIONAL_BEHAVIOR\","
            + "\"conditions\":[{\"type\":\"CONDITIONAL_BEHAVIOR\","
            + "\"conditions\":[{\"type\":\"ASSERT_USER_TASK\",\"userTaskSelector\":{\"elementId\":\"task1\"}}],"
            + "\"actions\":[{\"type\":\"COMPLETE_USER_TASK\",\"userTaskSelector\":{\"elementId\":\"task1\"}}]}],"
            + "\"actions\":[{\"type\":\"COMPLETE_USER_TASK\","
            + "\"userTaskSelector\":{\"elementId\":\"task1\"}}]"
            + "}]}]}";

    // when
    final List<Error> errors = jsonSchema.validate(json, InputFormat.JSON);

    // then
    assertThat(errors)
        .extracting(error -> error.getInstanceLocation().toString(), Error::getMessage)
        .contains(
            tuple(
                "/testCases/0/instructions/0/conditions/0",
                "must not be valid to the schema {\"$ref\":\"#/definitions/ConditionalBehaviorInstruction\"}"));
  }

  @Test
  void shouldRejectConditionalBehaviorWithConditionalBehaviorAction() throws IOException {
    // given
    final String json =
        "{\"testCases\":[{\"name\":\"t\",\"instructions\":[{"
            + "\"type\":\"CONDITIONAL_BEHAVIOR\","
            + "\"conditions\":[{\"type\":\"ASSERT_USER_TASK\",\"userTaskSelector\":{\"elementId\":\"task1\"}}],"
            + "\"actions\":[{\"type\":\"CONDITIONAL_BEHAVIOR\","
            + "\"conditions\":[{\"type\":\"ASSERT_USER_TASK\",\"userTaskSelector\":{\"elementId\":\"task1\"}}],"
            + "\"actions\":[{\"type\":\"COMPLETE_USER_TASK\",\"userTaskSelector\":{\"elementId\":\"task1\"}}]}]"
            + "}]}]}";

    // when
    final List<Error> errors = jsonSchema.validate(json, InputFormat.JSON);

    // then
    assertThat(errors)
        .extracting(error -> error.getInstanceLocation().toString(), Error::getMessage)
        .contains(
            tuple(
                "/testCases/0/instructions/0/actions/0",
                "must not be valid to the schema {\"$ref\":\"#/definitions/ConditionalBehaviorInstruction\"}"));
  }
}
