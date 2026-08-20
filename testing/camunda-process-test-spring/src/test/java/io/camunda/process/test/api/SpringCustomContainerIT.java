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
import io.camunda.process.test.api.runtime.CamundaProcessTestContainerProvider;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * This test verifies that we can add a custom container to the Camunda process test runtime. The
 * custom container is used to mock the HTTP endpoint of the outbound connector in the process. We
 * assert the invocation of the custom container by checking the process instance variables and the
 * completion of the connector task.
 */
@SpringBootTest(
    classes = {SpringCustomContainerIT.class, SpringCustomContainerIT.TestConfig.class},
    properties = {
      "camunda.process-test.connectors-enabled=true",
      "camunda.process-test.connectors-secrets.BASE_URL=http://wiremock:8080"
    })
@CamundaSpringProcessTest
public class SpringCustomContainerIT {

  @Autowired private CamundaClient client;

  @Test
  void shouldInvokeCustomContainer() {
    // given
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("connector-outbound-process.bpmn")
        .send()
        .join();

    // when
    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("outbound-connector-process")
            .latestVersion()
            .send()
            .join();

    // then
    CamundaAssert.assertThatProcessInstance(processInstance)
        .hasCompletedElements(byName("Mocked Outbound Connector"))
        .hasVariable("status", "okay")
        .isCompleted();
  }

  @Configuration
  static class TestConfig {

    @Bean
    public CamundaProcessTestContainerProvider wireMockProvider() {
      return containerContext -> new WireMockContainer();
    }
  }

  private static final class WireMockContainer extends GenericContainer<WireMockContainer> {
    public WireMockContainer() {
      super("wiremock/wiremock:3.13.0");
      withNetworkAliases("wiremock")
          .withExposedPorts(8080)
          .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("tc.wiremock"), true))
          .waitingFor(
              Wait.forHttp("/__admin/mappings").forPort(8080).withMethod("GET").forStatusCode(200))
          .withCopyFileToContainer(
              // Copy the WireMock mapping file to the container
              MountableFile.forClasspathResource("/customContainerIT/wiremock-mapping.json"),
              "/home/wiremock/mappings/wiremock-mapping.json");
    }
  }
}
