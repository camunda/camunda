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
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.process.test.impl.containers.CamundaContainer.MultiTenancyConfiguration;
import io.camunda.process.test.impl.containers.ContainerFactory;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeDefaults;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that {@link CamundaManagementClient#purgeCluster()} completes promptly when basic
 * authorization is enabled on the runtime. Without the fix, the purge completion check received
 * HTTP 401 and kept returning {@code false} until the 30-second timeout expired.
 */
// We use Testcontainers to start a "remote" Camunda container that is not managed by the Camunda
// process test extension.
@Testcontainers
public class CamundaProcessTestExtensionBasicAuthPurgeIT {

  private static final String PROCESS_ID = "process";
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask().endEvent().done();

  // 1: Start the Camunda container with basic authentication enabled.
  @Order(0)
  @Container
  private static final CamundaContainer REMOTE_CAMUNDA_CONTAINER =
      new ContainerFactory()
          .createCamundaContainer(
              CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_NAME,
              CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_VERSION)
          .withMultiTenancy();

  // 2: Bind the extension to the Camunda container, configuring basic auth credentials.
  @Order(1)
  @RegisterExtension
  private static final BindCamundaProcessTestExtension BIND_EXTENSION_TO_REMOTE =
      new BindCamundaProcessTestExtension();

  // 3: Start the extension and connect to the Camunda container.
  @Order(2)
  @RegisterExtension
  private static final CamundaProcessTestExtension EXTENSION =
      new CamundaProcessTestExtension().withRuntimeMode(CamundaProcessTestRuntimeMode.REMOTE);

  // to be injected
  private CamundaClient client;

  @Test
  void shouldPurgeClusterWithinTimeout() {
    // given
    client.newDeployResourceCommand().addProcessModel(PROCESS, "process.bpmn").send().join();

    final ProcessInstanceEvent processInstance =
        client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().send().join();

    Awaitility.await("Wait until process instance is exported to secondary storage")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final List<ProcessInstance> instances =
                  client.newProcessInstanceSearchRequest().send().join().items();
              assertThat(instances)
                  .extracting(ProcessInstance::getProcessInstanceKey)
                  .contains(processInstance.getProcessInstanceKey());
            });

    // when — purge using a new CamundaManagementClient (with the authenticated CamundaClient)
    final Instant purgeStart = Instant.now();
    final CamundaManagementClient managementClient =
        CamundaManagementClient.createClient(
            REMOTE_CAMUNDA_CONTAINER.getMonitoringApiAddress(),
            CamundaClient.newClientBuilder()
                .restAddress(REMOTE_CAMUNDA_CONTAINER.getRestApiAddress())
                .grpcAddress(REMOTE_CAMUNDA_CONTAINER.getGrpcApiAddress())
                .credentialsProvider(
                    CredentialsProvider.newBasicAuthCredentialsProviderBuilder()
                        .username(MultiTenancyConfiguration.MULTITENANCY_USER_USERNAME)
                        .password(MultiTenancyConfiguration.MULTITENANCY_USER_PASSWORD)
                        .build())
                .build());
    managementClient.purgeCluster();
    final Duration purgeElapsed = Duration.between(purgeStart, Instant.now());

    // then — data is gone
    assertThat(client.newProcessInstanceSearchRequest().send().join().items()).isEmpty();

    // and — purge completed well within the 30-second limit
    assertThat(purgeElapsed).isLessThan(Duration.ofSeconds(10));
  }

  private static final class BindCamundaProcessTestExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(final ExtensionContext context) {
      EXTENSION
          .withRemoteCamundaClientBuilderFactory(
              () ->
                  CamundaClient.newClientBuilder()
                      .restAddress(REMOTE_CAMUNDA_CONTAINER.getRestApiAddress())
                      .grpcAddress(REMOTE_CAMUNDA_CONTAINER.getGrpcApiAddress())
                      .credentialsProvider(
                          CredentialsProvider.newBasicAuthCredentialsProviderBuilder()
                              .username(MultiTenancyConfiguration.MULTITENANCY_USER_USERNAME)
                              .password(MultiTenancyConfiguration.MULTITENANCY_USER_PASSWORD)
                              .build()))
          .withRemoteCamundaMonitoringApiAddress(
              REMOTE_CAMUNDA_CONTAINER.getMonitoringApiAddress());
    }
  }
}
