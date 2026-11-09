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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CamundaProcessTestManagedScriptsIT {

  @RegisterExtension
  private static final CamundaProcessTestExtension EXTENSION =
      new CamundaProcessTestExtension()
          .withManagedScriptsEnabled(true)
          // The first version of the Connectors with managed scripts.
          .withConnectorsDockerImageVersion("8.11-SNAPSHOT");

  private CamundaClient client;

  @BeforeEach
  void setUp() {
    // The first call of a language starts its executor.
    CamundaAssert.setAssertionTimeout(Duration.ofMinutes(1));
  }

  @AfterEach
  void tearDown() {
    CamundaAssert.setAssertionTimeout(CamundaAssert.DEFAULT_ASSERTION_TIMEOUT);
  }

  @Test
  void shouldRunPythonAndJavaScriptScripts() {
    // given: the scripts are deployed with the process
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("managed-scripts/managed-scripts-process.bpmn")
        // A resource is linked by its name: deploy the scripts without their classpath folder.
        .addResourceStream(script("sum.py"), "sum.py")
        .addResourceStream(script("greet.js"), "greet.js")
        .send()
        .join();

    final Map<String, Object> variables = new HashMap<>();
    variables.put("a", 2);
    variables.put("b", 3);
    variables.put("name", "Zee");

    // when
    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("managed-scripts-process")
            .latestVersion()
            .variables(variables)
            .send()
            .join();

    // then
    CamundaAssert.assertThatProcessInstance(processInstance)
        .isCompleted()
        .hasCompletedElements(byName("Sum in Python"), byName("Greet in JavaScript"))
        .hasVariable("sum", 5)
        .hasVariable("greeting", "Hello Zee, the sum is 5 (javascript_script)");
  }

  private static InputStream script(final String name) {
    return CamundaProcessTestManagedScriptsIT.class.getResourceAsStream("/managed-scripts/" + name);
  }
}
