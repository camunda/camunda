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

  private static final String DSL_SCHEMA = "/schema/test-scenario-dsl.schema.json";

  private static JsonSchema jsonSchema;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeAll
  static void setup() {
    final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(VersionFlag.V7);
    jsonSchema = factory.getSchema(SchemaValidationTest.class.getResourceAsStream(DSL_SCHEMA));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/empty-test-scenario.json",
        "/example-test-scenario.json",
        "/full-test-scenario.json"
      })
  void shouldValidateScenario(final String filePath) throws IOException {
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
        objectMapper.readTree(getClass().getResourceAsStream("/invalid-test-scenario.json"));

    // when
    final Set<ValidationMessage> errors = jsonSchema.validate(jsonNode);

    // then
    assertThat(errors)
        .isNotEmpty()
        .extracting(ValidationMessage::getMessage)
        .contains(
            "$.testCases[0].instructions[0]: must be valid to one and only one schema, but 0 are valid");
  }
}
