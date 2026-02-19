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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.process.test.api.testCases.TestCase;
import io.camunda.process.test.api.testCases.TestCases;
import io.camunda.process.test.impl.testCases.TestCasesReader;
import org.junit.jupiter.api.Test;

public class TestScenarioReaderTest {

  private static final String EXAMPLE_TEST_TEST_CASES = "/example-test-cases.json";
  private static final String INVALID_TEST_CASES = "/invalid-test-cases.json";

  @Test
  void shouldReadTestCases() {
    // given
    final TestCasesReader reader = new TestCasesReader();

    // when
    final TestCases testCases =
        reader.read(getClass().getResourceAsStream(EXAMPLE_TEST_TEST_CASES));

    // then
    assertThat(testCases).isNotNull();
    assertThat(testCases.getTestCases()).hasSize(1);

    final TestCase testCase = testCases.getTestCases().get(0);
    assertThat(testCase.getName()).isEqualTo("Happy path order processing");
  }

  @Test
  void shouldThrowExceptionForInvalidTestCases() {
    // given
    final TestCasesReader reader = new TestCasesReader();

    // when / then
    assertThatThrownBy(() -> reader.read(getClass().getResourceAsStream(INVALID_TEST_CASES)))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Failed to read test cases from stream")
        .rootCause()
        .hasMessageStartingWith("Could not resolve type id 'unknown type'");
  }
}
