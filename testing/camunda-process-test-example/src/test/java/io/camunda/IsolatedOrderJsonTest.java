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
import io.camunda.process.test.api.testCases.TestCase;
import io.camunda.process.test.api.testCases.TestCaseRunner;
import io.camunda.process.test.api.testCases.TestCaseSource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Runs the test cases that reserve an instance's jobs and stub its call activities. Job workers are
 * left enabled, because one case hands a job back to {@link io.camunda.workers.SendInvoiceWorker}.
 *
 * <p>Disabled because both switches need an engine that has them: the default runtime pulls {@code
 * camunda/camunda:SNAPSHOT}, which is built from {@code main}. See the module README for the two
 * ways to point this test at an engine built from the branch.
 */
@Disabled("Needs an engine with job reservation and call activity stubbing — see the README")
@SpringBootTest
@CamundaSpringProcessTest
public class IsolatedOrderJsonTest {

  @Autowired private TestCaseRunner testCaseRunner;

  @ParameterizedTest
  @TestCaseSource(directory = "/test-cases-isolated")
  void shouldPass(final TestCase testCase, final String filename) {
    // given: the process definitions are deployed via the Spring Boot application

    // when/then: run and verify the test case
    testCaseRunner.run(testCase);
  }
}
