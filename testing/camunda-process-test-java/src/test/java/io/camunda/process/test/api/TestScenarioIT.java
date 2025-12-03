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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.dsl.TestCase;
import io.camunda.process.test.api.dsl.TestScenarioSource;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.params.ParameterizedTest;

@CamundaProcessTest
public class TestScenarioIT {

  // to be injected
  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @ParameterizedTest
  @TestScenarioSource
  void shouldPass(final TestCase testCase) {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process").startEvent("start").name("end").done();

    client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

    // when

    // then
    assertThat(testCase).isNotNull();
  }
}
