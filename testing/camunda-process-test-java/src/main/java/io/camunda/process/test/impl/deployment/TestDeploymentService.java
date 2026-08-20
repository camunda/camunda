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
package io.camunda.process.test.impl.deployment;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.DeployResourceCommandStep1.DeployResourceCommandStep2;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.process.test.api.TestDeployment;
import java.lang.reflect.Method;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service responsible for handling @TestDeployment annotations. Shared between JUnit Extension and
 * Spring Boot test execution listener.
 */
public class TestDeploymentService {

  private static final Logger LOG = LoggerFactory.getLogger(TestDeploymentService.class);

  /**
   * Deploys resources defined by @TestDeployment annotation on method or class.
   *
   * @param testMethod the test method
   * @param testClass the test class
   * @param client the Camunda client to use for deployment
   */
  public void deployTestResources(
      final Method testMethod, final Class<?> testClass, final CamundaClient client) {

    final TestDeployment deployment = findTestDeployment(testMethod, testClass);
    if (deployment != null) {
      performDeployment(deployment, client);
    }
  }

  private TestDeployment findTestDeployment(final Method testMethod, final Class<?> testClass) {
    // Method-level annotation takes precedence
    TestDeployment deployment = null;
    if (testMethod != null) {
      deployment = testMethod.getAnnotation(TestDeployment.class);
    }
    if (deployment == null && testClass != null) {
      deployment = testClass.getAnnotation(TestDeployment.class);
    }
    return deployment;
  }

  private void performDeployment(final TestDeployment deployment, final CamundaClient client) {

    final String[] resources = deployment.resources();
    if (deployment.resources().length == 0) {
      LOG.warn("No resources defined in @TestDeployment. Skipping.");
      return;
    }

    LOG.debug(
        "Deploying resources from @TestDeployment: [{}]", String.join(",", deployment.resources()));

    try {
      DeployResourceCommandStep2 deployCommand =
          client.newDeployResourceCommand().addResourceFromClasspath(resources[0]);
      for (int i = 1; i < resources.length; i++) {
        deployCommand = deployCommand.addResourceFromClasspath(resources[i]);
      }
      final DeploymentEvent deploymentEvent = deployCommand.send().join();

      LOG.info("Deployed resources from @TestDeployment: {}", collectDefinitions(deploymentEvent));
    } catch (final Exception e) {
      throw new RuntimeException("Failed to deploy resources from @TestDeployment", e);
    }
  }

  private static String collectDefinitions(final DeploymentEvent deploymentEvent) {
    return Stream.concat(
            deploymentEvent.getDecisionRequirements().stream()
                .map(
                    wf ->
                        String.format(
                            "<%s:%d>", wf.getDmnDecisionRequirementsId(), wf.getVersion())),
            deploymentEvent.getProcesses().stream()
                .map(wf -> String.format("<%s:%d>", wf.getBpmnProcessId(), wf.getVersion())))
        .collect(Collectors.joining(","));
  }
}
