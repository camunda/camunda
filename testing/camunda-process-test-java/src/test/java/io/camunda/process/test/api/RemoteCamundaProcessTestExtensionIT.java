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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.process.test.impl.containers.ContainerFactory;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeDefaults;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// We use Testcontainers to start a "remote" Camunda container that is not managed by the Camunda
// process test extension.
@Testcontainers
public class RemoteCamundaProcessTestExtensionIT {

  // 1: Start the Camunda container
  @Order(0)
  @Container
  private static final CamundaContainer REMOTE_CAMUNDA_CONTAINER =
      new ContainerFactory()
          .createCamundaContainer(
              CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_NAME,
              CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_VERSION);

  // 2: Bind the extension to the Camunda container
  @Order(1)
  @RegisterExtension
  private static final BindCamundaProcessTestExtension BIND_EXTENSION_TO_REMOTE =
      new BindCamundaProcessTestExtension();

  // 3: Start the extension and connect to the Camunda container
  @Order(2)
  @RegisterExtension
  private static final CamundaProcessTestExtension EXTENSION =
      new CamundaProcessTestExtension().withRuntimeMode(CamundaProcessTestRuntimeMode.REMOTE);

  // to be injected
  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @Test
  void shouldCreateProcessInstance() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task", t -> t.name("Task").zeebeJobType("task"))
            .endEvent("end")
            .done();

    client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

    processTestContext.mockJobWorker("task").thenComplete();

    // when
    final ProcessInstanceEvent processInstance =
        client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();

    // then
    CamundaAssert.assertThatProcessInstance(processInstance)
        .isCompleted()
        .hasCompletedElementsInOrder("start", "task", "end");
  }

  private static final class BindCamundaProcessTestExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(final ExtensionContext context) {
      EXTENSION
          .withRemoteCamundaClientBuilderFactory(
              () ->
                  CamundaClient.newClientBuilder()
                      .usePlaintext()
                      .restAddress(REMOTE_CAMUNDA_CONTAINER.getRestApiAddress())
                      .grpcAddress(REMOTE_CAMUNDA_CONTAINER.getGrpcApiAddress()))
          .withRemoteCamundaMonitoringApiAddress(
              REMOTE_CAMUNDA_CONTAINER.getMonitoringApiAddress());
    }
  }
}
