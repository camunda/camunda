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
package io.camunda.process.test.impl.extensions;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.impl.extensions.ConditionalBehaviorTestProcess.*;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that conditional behaviors registered in {@code @BeforeEach} work correctly across
 * multiple test methods. Each test method gets fresh behaviors.
 */
@CamundaProcessTest
public class ConditionalBehaviorBeforeEachIT {

  private static final Map<String, Object> UNHAPPY = Collections.singletonMap("happy", false);
  private static final Map<String, Object> HAPPY = Collections.singletonMap("happy", true);
  private static final Map<String, Object> EXPORT_VARS =
      Collections.singletonMap("exportSuccess", true);

  private CamundaProcessTestContext processTestContext;
  private CamundaClient client;

  @BeforeEach
  void setupBehaviors() {
    // Deploy the process model
    client.newDeployResourceCommand().addProcessModel(MODEL, PROCESS_ID + ".bpmn").send().join();

    processTestContext
        .when(
            () ->
                assertThat(ProcessInstanceSelectors.byProcessId(PROCESS_ID))
                    .hasActiveElement(USER_TASK_ID, 1))
        .then(() -> processTestContext.completeUserTask(USER_TASK_ID, UNHAPPY))
        .then(() -> processTestContext.completeUserTask(USER_TASK_ID, HAPPY));

    processTestContext
        .when(
            () ->
                assertThatProcessInstance(ProcessInstanceSelectors.byProcessId(PROCESS_ID))
                    .hasActiveElements(SERVICE_TASK_ID))
        .then(() -> processTestContext.completeJob(JOB_TYPE, EXPORT_VARS));
  }

  @Test
  void shouldCompleteProcessFirstRun() {
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().execute();

    assertThatProcessInstance(processInstanceEvent)
        .isCompleted()
        .hasVariable("happy", true)
        .hasVariable("exportSuccess", true);
  }

  @Test
  void shouldCompleteProcessSecondRun() {
    // Same behavior — proves @BeforeEach re-registers fresh behaviors for each test,
    // including a fresh action chain (happy=false first, then happy=true)
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().execute();

    assertThatProcessInstance(processInstanceEvent)
        .isCompleted()
        .hasVariable("happy", true)
        .hasVariable("exportSuccess", true);
  }
}
