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

import static io.camunda.process.test.api.assertions.ElementSelectors.byName;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import java.io.InputStream;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Without managed scripts, a test mocks the scripts by their job type instead of running them. */
public class CamundaProcessTestManagedScriptsMockIT {

  private static final String MANAGED_SCRIPT_JOB_TYPE = "io.camunda:managed-script:1";

  @RegisterExtension
  private static final CamundaProcessTestExtension EXTENSION = new CamundaProcessTestExtension();

  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @Test
  void shouldMockTheScripts() {
    // given: one mock for every script task, which completes with the mapped variables
    processTestContext
        .mockJobWorker(MANAGED_SCRIPT_JOB_TYPE)
        .withHandler(
            (jobClient, job) ->
                jobClient
                    .newCompleteCommand(job)
                    .variables(
                        "python_script".equals(job.getElementId())
                            ? Collections.singletonMap("sum", 42)
                            : Collections.singletonMap("greeting", "mocked"))
                    .send()
                    .join());

    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("managed-scripts/managed-scripts-process.bpmn")
        // A resource is linked by its name: deploy the scripts without their classpath folder.
        .addResourceStream(script("sum.py"), "sum.py")
        .addResourceStream(script("greet.js"), "greet.js")
        .send()
        .join();

    // when
    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("managed-scripts-process")
            .latestVersion()
            .variables("{\"a\": 2, \"b\": 3, \"name\": \"Zee\"}")
            .send()
            .join();

    // then
    CamundaAssert.assertThatProcessInstance(processInstance)
        .isCompleted()
        .hasCompletedElements(byName("Sum in Python"), byName("Greet in JavaScript"))
        .hasVariable("sum", 42)
        .hasVariable("greeting", "mocked");
  }

  private static InputStream script(final String name) {
    return CamundaProcessTestManagedScriptsMockIT.class.getResourceAsStream(
        "/managed-scripts/" + name);
  }
}
