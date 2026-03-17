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

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {SpringConditionalScenarioApiIT.class})
@CamundaSpringProcessTest
public class SpringConditionalScenarioApiIT {

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

  @Test
  void shouldCompleteProcessWithConditionalScenarios() {
    // Deploy
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("conditionalScenarioApi/scenario-api-test.bpmn")
        .addResourceFromClasspath("conditionalScenarioApi/satisfaction.form")
        .execute();

    // Setup conditional scenarios
    processTestContext
        .when(
            () ->
                assertThat(ProcessInstanceSelectors.byProcessId("user-happiness-check"))
                    .hasActiveElement("State_Happiness", 1))
        .then(() -> processTestContext.completeUserTask("State_Happiness", Map.of("happy", false)))
        .then(() -> processTestContext.completeUserTask("State_Happiness", Map.of("happy", true)));

    processTestContext
        .when(
            () ->
                assertThatProcessInstance(
                        ProcessInstanceSelectors.byProcessId("user-happiness-check"))
                    .hasActiveElements("Export_Happiness"))
        .then(
            () ->
                processTestContext.completeJob(
                    "io.camunda:http-json:1", Map.of("exportSuccess", true)));

    // Start the process
    final ProcessInstanceEvent processInstanceEvent =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("user-happiness-check")
            .latestVersion()
            .execute();

    // Assert
    assertThatProcessInstance(processInstanceEvent)
        .isCompleted()
        .hasVariable("happy", true)
        .hasVariable("exportSuccess", true);
  }
}
