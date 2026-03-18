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
import org.junit.jupiter.api.RepeatedTest;

@CamundaProcessTest
public class ConditionalBehaviorApiIT {

  // injected by the extension
  private CamundaProcessTestContext processTestContext;
  private CamundaClient client;

  // ensure repeated tests get fresh behaviors
  @RepeatedTest(value = 2)
  void shouldCompleteProcessWithConditionalBehaviors() {
    // Deploy the process model
    client.newDeployResourceCommand().addProcessModel(MODEL, PROCESS_ID + ".bpmn").send().join();

    final Map<String, Object> unhappy = Collections.singletonMap("happy", false);
    final Map<String, Object> happy = Collections.singletonMap("happy", true);
    final Map<String, Object> exportVars = Collections.singletonMap("exportSuccess", true);

    // Setup conditional behaviors before starting the process

    // When user task "State_Happiness" is created, complete it:
    //   1st time with happy=false (loops back)
    //   2nd time with happy=true (proceeds to Export_Happiness)
    processTestContext
        .when(
            () ->
                assertThat(ProcessInstanceSelectors.byProcessId(PROCESS_ID))
                    .hasActiveElement(USER_TASK_ID, 1))
        .then(() -> processTestContext.completeUserTask(USER_TASK_ID, unhappy))
        .then(() -> processTestContext.completeUserTask(USER_TASK_ID, happy));

    // When Export_Happiness is active, complete the job
    processTestContext
        .when(
            () ->
                assertThatProcessInstance(ProcessInstanceSelectors.byProcessId(PROCESS_ID))
                    .hasActiveElements(SERVICE_TASK_ID))
        .then(() -> processTestContext.completeJob(JOB_TYPE, exportVars));

    // Start the process
    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().execute();

    // Assert process completed with expected variables
    assertThatProcessInstance(processInstanceEvent)
        .isCompleted()
        .hasVariable("happy", true)
        .hasVariable("exportSuccess", true);
  }
}
