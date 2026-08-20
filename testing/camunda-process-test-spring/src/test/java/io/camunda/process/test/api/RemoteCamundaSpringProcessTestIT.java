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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// We use Testcontainers to start a "remote" Camunda container that is not managed by the Camunda
// process test execution listener.
@Testcontainers
@SpringBootTest(
    classes = {RemoteCamundaSpringProcessTestIT.class},
    properties = {"io.camunda.process.test.runtime-mode=remote"})
// 3: Start the execution listener and connect to the Camunda container
@CamundaSpringProcessTest
public class RemoteCamundaSpringProcessTestIT {

  // 1: Start the Camunda container
  @Container
  private static final CamundaContainer REMOTE_CAMUNDA_CONTAINER =
      new ContainerFactory()
          .createCamundaContainer(
              CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_NAME,
              CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_VERSION);

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

  // 2: Bind the execution listener to the Camunda container
  @DynamicPropertySource
  static void bindToCamundaContainer(final DynamicPropertyRegistry registry) {
    registry.add(
        "io.camunda.process.test.remote.client.restAddress",
        REMOTE_CAMUNDA_CONTAINER::getRestApiAddress);
    registry.add(
        "io.camunda.process.test.remote.client.grpcAddress",
        REMOTE_CAMUNDA_CONTAINER::getGrpcApiAddress);
    registry.add(
        "io.camunda.process.test.remote.camunda-monitoring-api-address",
        REMOTE_CAMUNDA_CONTAINER::getMonitoringApiAddress);
  }

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
}
