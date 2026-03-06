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

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.CamundaAssert.assertThatUserTask;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that conditional scenarios registered in {@code @BeforeAll} work correctly across
 * multiple test methods in a Spring test context with a shared test instance.
 */
@SpringBootTest(classes = {SpringConditionalScenarioBeforeAllIT.class})
@CamundaSpringProcessTest
@TestInstance(Lifecycle.PER_CLASS)
public class SpringConditionalScenarioBeforeAllIT {

  private static final Map<String, Object> UNHAPPY = Collections.singletonMap("happy", false);
  private static final Map<String, Object> HAPPY = Collections.singletonMap("happy", true);
  private static final Map<String, Object> EXPORT_VARS =
      Collections.singletonMap("exportSuccess", true);

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

  @BeforeAll
  void setupScenarios() {
    processTestContext
        .when(
            () -> assertThatUserTask(UserTaskSelectors.byElementId("State_Happiness")).isCreated())
        .then(() -> processTestContext.completeUserTask("State_Happiness", UNHAPPY))
        .then(() -> processTestContext.completeUserTask("State_Happiness", HAPPY))
        .when(
            () ->
                assertThatProcessInstance(
                        ProcessInstanceSelectors.byProcessId("user-happiness-check"))
                    .hasActiveElements("Export_Happiness"))
        .then(() -> processTestContext.completeJob("io.camunda:http-json:1", EXPORT_VARS));
  }

  @BeforeEach
  void deployResources() {
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("conditionalScenarioApi/scenario-api-test.bpmn")
        .addResourceFromClasspath("conditionalScenarioApi/satisfaction.form")
        .send()
        .join();
  }

  @Test
  void shouldCompleteProcessFirstRun() {
    final ProcessInstanceEvent processInstanceEvent =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("user-happiness-check")
            .latestVersion()
            .send()
            .join();

    assertThatProcessInstance(processInstanceEvent)
        .isCompleted()
        .hasVariable("happy", true)
        .hasVariable("exportSuccess", true);
  }

  @Test
  void shouldCompleteProcessSecondRun() {
    final ProcessInstanceEvent processInstanceEvent =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("user-happiness-check")
            .latestVersion()
            .send()
            .join();

    assertThatProcessInstance(processInstanceEvent)
        .isCompleted()
        .hasVariable("happy", true)
        .hasVariable("exportSuccess", true);
  }
}
