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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.process.test.api.dsl.TestCase;
import io.camunda.process.test.api.dsl.TestScenario;
import io.camunda.process.test.api.dsl.instructions.CreateProcessInstanceInstruction;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

public class DeserializationTest {

  private static final String EMPTY_TEST_SCENARIO = "/empty-test-scenario.json";
  private static final String EXAMPLE_TEST_SCENARIO = "/example-test-scenario.json";

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .registerModules(new Jdk8Module(), new JavaTimeModule());

  @Test
  void shouldParseEmptyTestScenario() throws IOException {
    // given
    final InputStream json = getClass().getResourceAsStream(EMPTY_TEST_SCENARIO);

    // when
    final TestScenario testScenario = objectMapper.readValue(json, TestScenario.class);

    // then
    assertThat(testScenario).isNotNull();
    assertThat(testScenario.getTestCases()).hasSize(1);

    final TestCase testCase = testScenario.getTestCases().get(0);
    assertThat(testCase.getName()).isEqualTo("Test Case 1");
    assertThat(testCase.getDescription())
        .hasValue("A human-readable description of what this test case is verifying.");
    assertThat(testCase.getInstructions()).isEmpty();
  }

  @Test
  void shouldParseExampleTestScenario() throws IOException {
    // given
    final InputStream json = getClass().getResourceAsStream(EXAMPLE_TEST_SCENARIO);

    // when
    final TestScenario testScenario = objectMapper.readValue(json, TestScenario.class);

    // then
    assertThat(testScenario).isNotNull();
    assertThat(testScenario.getTestCases()).hasSize(1);

    final TestCase testCase = testScenario.getTestCases().get(0);
    assertThat(testCase.getName()).isEqualTo("Happy path order processing");
    assertThat(testCase.getDescription())
        .hasValue(
            "The order should be processed successfully from creation to shipping with tracking code received.");
    assertThat(testCase.getInstructions())
        .hasSize(1)
        .satisfies(
            testCaseInstructions ->
                assertThat(testCaseInstructions.get(0))
                    .isInstanceOf(CreateProcessInstanceInstruction.class));
  }
}
