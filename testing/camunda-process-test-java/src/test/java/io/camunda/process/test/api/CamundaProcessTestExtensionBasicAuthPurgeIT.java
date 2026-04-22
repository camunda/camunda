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
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.process.test.impl.containers.ContainerFactory;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeDefaults;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.time.Duration;
import java.time.Instant;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy.Mode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that the cluster purge completes successfully when basic authorization is enabled on the
 * runtime.
 */
@Testcontainers
public class CamundaProcessTestExtensionBasicAuthPurgeIT {

  private static final String ADMIN_USERNAME = "admin";
  private static final String ADMIN_PASSWORD = "admin";
  private static final String ADMIN_NAME = "Admin";
  private static final String ADMIN_EMAIL = "admin@camunda.com";

  // 1: Start the Camunda container with basic authentication enabled via env variables.
  @Order(0)
  @Container
  private static final CamundaContainer REMOTE_CAMUNDA_CONTAINER =
      new ContainerFactory()
          .createCamundaContainer(
              CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_NAME,
              CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_VERSION)
          .withEnv("CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED", "true")
          .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI", "false")
          .withEnv("CAMUNDA_SECURITY_INITIALIZATION_USERS_0_NAME", ADMIN_NAME)
          .withEnv("CAMUNDA_SECURITY_INITIALIZATION_USERS_0_EMAIL", ADMIN_EMAIL)
          .withEnv("CAMUNDA_SECURITY_INITIALIZATION_USERS_0_USERNAME", ADMIN_USERNAME)
          .withEnv("CAMUNDA_SECURITY_INITIALIZATION_USERS_0_PASSWORD", ADMIN_PASSWORD)
          .withEnv("CAMUNDA_SECURITY_INITIALIZATION_DEFAULTROLES_ADMIN_USERS_0", ADMIN_USERNAME)
          .waitingFor(
              new WaitAllStrategy(Mode.WITH_OUTER_TIMEOUT)
                  .withStrategy(CamundaContainer.newDefaultBrokerReadyCheck())
                  .withStrategy(
                      CamundaContainer.newDefaultTopologyReadyCheck()
                          .withBasicCredentials(ADMIN_USERNAME, ADMIN_PASSWORD))
                  .withStartupTimeout(Duration.ofMinutes(1)));

  // 2: Bind the extension to the Camunda container, configuring basic auth credentials.
  @Order(1)
  @RegisterExtension
  private static final BindCamundaProcessTestExtension BIND_EXTENSION_TO_REMOTE =
      new BindCamundaProcessTestExtension();

  // 3: Start the extension and connect to the Camunda container as a remote runtime.
  @Order(2)
  @RegisterExtension
  private static final CamundaProcessTestExtension EXTENSION =
      new CamundaProcessTestExtension().withRuntimeMode(CamundaProcessTestRuntimeMode.REMOTE);

  // to be injected
  private CamundaClient client;

  @Test
  void shouldPurgeClusterWithinTimeout() throws InterruptedException {
    // given
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("process").startEvent().userTask().endEvent().done(),
            "process.bpmn")
        .send()
        .join();

    final ProcessInstanceEvent processInstance =
        client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();

    CamundaAssert.assertThatProcessInstance(processInstance).isActive();

    // when — purge using a new CamundaManagementClient (management API requires no auth)
    final Instant purgeStart = Instant.now();

    final CamundaManagementClient managementClient =
        CamundaManagementClient.createClient(REMOTE_CAMUNDA_CONTAINER.getMonitoringApiAddress());
    managementClient.purgeCluster();

    final Duration purgeElapsed = Duration.between(purgeStart, Instant.now());

    // then — data is gone
    Awaitility.await("Verify data deletion after purge")
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptionsInstanceOf(ClientException.class)
        .untilAsserted(
            () ->
                // there can be a delay until the authorization is initialized
                assertThat(client.newProcessInstanceSearchRequest().send().join().items())
                    .describedAs("The purge operation should delete all data.")
                    .isEmpty());

    // and — purge completed well within the 30-second limit
    assertThat(purgeElapsed)
        .describedAs("The purge operation should take less than 10 seconds to complete.")
        .isLessThan(Duration.ofSeconds(10));
  }

  private static final class BindCamundaProcessTestExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(final ExtensionContext context) {
      EXTENSION
          .withCamundaClientBuilderFactory(
              () ->
                  CamundaClient.newClientBuilder()
                      .restAddress(REMOTE_CAMUNDA_CONTAINER.getRestApiAddress())
                      .grpcAddress(REMOTE_CAMUNDA_CONTAINER.getGrpcApiAddress())
                      .credentialsProvider(
                          CredentialsProvider.newBasicAuthCredentialsProviderBuilder()
                              .username(ADMIN_USERNAME)
                              .password(ADMIN_PASSWORD)
                              .build()))
          .withRemoteCamundaMonitoringApiAddress(
              REMOTE_CAMUNDA_CONTAINER.getMonitoringApiAddress());
    }
  }
}
