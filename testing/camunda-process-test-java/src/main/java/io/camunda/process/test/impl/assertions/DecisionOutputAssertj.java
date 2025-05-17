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
package io.camunda.process.test.impl.assertions;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.assertj.core.api.AbstractAssert;

public class DecisionOutputAssertj extends AbstractAssert<DecisionOutputAssertj, String> {

  private final ObjectMapper jsonMapper = new ObjectMapper();

  public DecisionOutputAssertj(final String failureMessagePrefix) {
    super(failureMessagePrefix, DecisionOutputAssertj.class);
  }

  public void hasOutput(final String decisionOutput, final Object expectedOutput) {
    final JsonNode decisionOutputJson = readJson(decisionOutput);
    final JsonNode expectedOutputJson = toJson(expectedOutput);

    assertThat(decisionOutputJson)
        .withFailMessage(
            "%s to have output '%s', but was '%s'", actual, expectedOutputJson, decisionOutputJson)
        .isEqualTo(expectedOutputJson);
  }

  private JsonNode readJson(final String value) {
    if (value == null) {
      return NullNode.getInstance();
    }

    try {
      return jsonMapper.readValue(value, JsonNode.class);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(String.format("Failed to read JSON: '%s'", value), e);
    }
  }

  private JsonNode toJson(final Object value) {
    try {
      return jsonMapper.convertValue(value, JsonNode.class);
    } catch (final IllegalArgumentException e) {
      throw new RuntimeException(
          String.format("Failed to transform value to JSON: '%s'", value), e);
    }
  }
}
