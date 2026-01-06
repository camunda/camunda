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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.process.test.api.dsl.TestCase;
import io.camunda.process.test.api.dsl.TestScenario;
import io.camunda.process.test.impl.dsl.TestScenarioReader;
import org.junit.jupiter.api.Test;

public class TestScenarioReaderTest {

  private static final String EXAMPLE_TEST_SCENARIO = "/example-test-scenario.json";
  private static final String COMPLETE_JOB_TEST_SCENARIO = "/complete-job-test-scenario.json";
  private static final String INVALID_TEST_SCENARIO = "/invalid-test-scenario.json";

  @Test
  void shouldReadTestScenario() {
    // given
    final TestScenarioReader reader = new TestScenarioReader();

    // when
    final TestScenario testScenario =
        reader.read(getClass().getResourceAsStream(EXAMPLE_TEST_SCENARIO));

    // then
    assertThat(testScenario).isNotNull();
    assertThat(testScenario.getTestCases()).hasSize(1);

    final TestCase testCase = testScenario.getTestCases().get(0);
    assertThat(testCase.getName()).isEqualTo("Happy path order processing");
  }

  @Test
  void shouldReadCompleteJobTestScenario() {
    // given
    final TestScenarioReader reader = new TestScenarioReader();

    // when
    final TestScenario testScenario =
        reader.read(getClass().getResourceAsStream(COMPLETE_JOB_TEST_SCENARIO));

    // then
    assertThat(testScenario).isNotNull();
    assertThat(testScenario.getTestCases()).hasSize(3);

    final TestCase testCase1 = testScenario.getTestCases().get(0);
    assertThat(testCase1.getName()).isEqualTo("Complete job by job type");
    assertThat(testCase1.getInstructions()).hasSize(2);

    final TestCase testCase2 = testScenario.getTestCases().get(1);
    assertThat(testCase2.getName()).isEqualTo("Complete job with example data");
    assertThat(testCase2.getInstructions()).hasSize(1);

    final TestCase testCase3 = testScenario.getTestCases().get(2);
    assertThat(testCase3.getName()).isEqualTo("Complete job with combined selector");
    assertThat(testCase3.getInstructions()).hasSize(1);
  }

  @Test
  void shouldThrowExceptionForInvalidTestScenario() {
    // given
    final TestScenarioReader reader = new TestScenarioReader();

    // when / then
    assertThatThrownBy(() -> reader.read(getClass().getResourceAsStream(INVALID_TEST_SCENARIO)))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to read test scenario from stream")
        .rootCause()
        .hasMessageStartingWith("Could not resolve type id 'unknown type'");
  }
}
