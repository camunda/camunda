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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static io.camunda.process.test.api.assertions.ElementSelectors.byName;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.Testcontainers;

@WireMockTest(httpPort = 9999)
@SpringBootTest(
    classes = {CamundaProcessTestOutboundConnectorsMockIT.class},
    properties = {
      "io.camunda.process.test.connectors-enabled=true",
      "io.camunda.process.test.connectors-secrets.CONNECTORS_URL=http://connectors:8080/actuator/health/readiness"
    })
@CamundaSpringProcessTest
public class CamundaProcessTestOutboundConnectorsMockIT {

  // to be injected
  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

  @BeforeAll
  static void setup(final WireMockRuntimeInfo wireMockRuntimeInfo) {
    Testcontainers.exposeHostPorts(wireMockRuntimeInfo.getHttpPort());
  }

  @Test
  void shouldInvokeOutboundConnectors() {
    // given
    stubFor(
        get(urlPathMatching("/test"))
            .willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));

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
            .variable("key", "key-1")
            .send()
            .join();

    // then
    CamundaAssert.assertThatProcessInstance(processInstance)
        .isCompleted()
        .hasCompletedElements(byName("Mocked Outbound Connector"));
  }
}
