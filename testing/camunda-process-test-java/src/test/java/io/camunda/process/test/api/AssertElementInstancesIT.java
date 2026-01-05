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
package io.camunda.process.test.api;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.dsl.TestCase;
import io.camunda.process.test.api.dsl.TestScenarioRunner;
import io.camunda.process.test.api.dsl.TestScenarioSource;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;

@CamundaProcessTest
public class AssertElementInstancesIT {

  private CamundaClient client;
  private TestScenarioRunner testScenarioRunner;

  @ParameterizedTest
  @TestScenarioSource(fileNames = "assert-element-instances-test-scenario.json")
  void shouldAssertElementInstances(final TestCase testCase, final String scenarioFile) {
    // given
    if (testCase.getName().equals("Assert active elements")) {
      final BpmnModelInstance process =
          Bpmn.createExecutableProcess("test-process")
              .startEvent("start")
              .parallelGateway("fork")
              .serviceTask("task_A")
              .zeebeJobType("task_A")
              .moveToNode("fork")
              .serviceTask("task_B")
              .zeebeJobType("task_B")
              .done();

      client
          .newDeployResourceCommand()
          .addProcessModel(process, "test-process.bpmn")
          .send()
          .join();
    } else if (testCase.getName().equals("Assert completed elements in order")) {
      final BpmnModelInstance process =
          Bpmn.createExecutableProcess("sequential-process")
              .startEvent("start")
              .serviceTask("task_A")
              .zeebeJobType("task_A")
              .serviceTask("task_B")
              .zeebeJobType("task_B")
              .endEvent("end")
              .done();

      client
          .newDeployResourceCommand()
          .addProcessModel(process, "sequential-process.bpmn")
          .send()
          .join();

      // Auto-complete jobs
      client
          .newStreamJobsCommand()
          .jobType("task_A")
          .consumer(
              job -> {
                client.newCompleteCommand(job).send().join();
              })
          .send()
          .join();

      client
          .newStreamJobsCommand()
          .jobType("task_B")
          .consumer(
              job -> {
                client.newCompleteCommand(job).send().join();
              })
          .send()
          .join();

      // Wait a bit for completion
      try {
        Thread.sleep(Duration.ofSeconds(2).toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    // when
    testScenarioRunner.run(testCase);

    // then - no exception thrown
  }
}
