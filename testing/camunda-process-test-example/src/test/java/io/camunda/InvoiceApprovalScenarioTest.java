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
package io.camunda;

import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.dsl.TestCase;
import io.camunda.process.test.api.dsl.TestScenarioRunner;
import io.camunda.process.test.api.dsl.TestScenarioSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * This test class demonstrates how to run process test scenarios using Camunda Process Test.
 * InvoiceApprovalTest is the equivalent test class that uses CPT's Java API.
 */
@SpringBootTest(properties = {"camunda.client.worker.defaults.enabled=false"})
@CamundaSpringProcessTest
public class InvoiceApprovalScenarioTest {

  @Autowired private TestScenarioRunner testScenarioRunner;

  @ParameterizedTest
  @TestScenarioSource
  void shouldPass(final TestCase testCase, final String scenarioFile) {
    // given: the process definitions are deployed via Spring Boot application

    // when/then: run and verify the test case
    testScenarioRunner.run(testCase);
  }
}
