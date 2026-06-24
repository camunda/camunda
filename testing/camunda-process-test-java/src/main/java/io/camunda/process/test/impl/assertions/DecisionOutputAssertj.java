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

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.process.test.impl.assertions.util.CamundaAssertJsonMapper;
import org.assertj.core.api.AbstractAssert;

public class DecisionOutputAssertj extends AbstractAssert<DecisionOutputAssertj, String> {

  private final CamundaAssertJsonMapper jsonMapper;

  public DecisionOutputAssertj(
      final CamundaAssertJsonMapper jsonMapper, final String failureMessagePrefix) {

    super(failureMessagePrefix, DecisionOutputAssertj.class);

    this.jsonMapper = jsonMapper;
  }

  public void hasOutput(final String decisionOutput, final Object expectedOutput) {
    final JsonNode decisionOutputJson = jsonMapper.readJson(decisionOutput);
    final JsonNode expectedOutputJson = jsonMapper.toJsonNode(expectedOutput);

    assertThat(decisionOutputJson)
        .withFailMessage(
            "%s to have output '%s', but was '%s'", actual, expectedOutputJson, decisionOutputJson)
        .isEqualTo(expectedOutputJson);
  }
}
