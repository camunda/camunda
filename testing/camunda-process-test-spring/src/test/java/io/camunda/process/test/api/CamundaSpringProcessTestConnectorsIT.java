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
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static io.camunda.process.test.api.assertions.ElementSelectors.byName;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import java.io.IOException;
import java.time.Duration;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.Testcontainers;

@SpringBootTest(
    classes = {CamundaSpringProcessTestConnectorsIT.class},
    properties = {
      "io.camunda.process.test.connectors-enabled=true",
      "io.camunda.process.test.connectors-secrets.CONNECTORS_URL=http://connectors:8080/actuator/health/readiness"
    })
@CamundaSpringProcessTest
public class CamundaSpringProcessTestConnectorsIT {

  private static final WireMockServer WIREMOCK =
      new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

  @DynamicPropertySource
  static void registerWireMockProperties(final DynamicPropertyRegistry registry) {
    WIREMOCK.start();
    final int wireMockPort = WIREMOCK.port();
    Testcontainers.exposeHostPorts(wireMockPort);
    registry.add(
        "io.camunda.process.test.connectors-secrets.BASE_URL",
        () -> "http://host.testcontainers.internal:" + wireMockPort);
  }

  @AfterAll
  static void tearDown() {
    WIREMOCK.stop();
  }

  // The ID is part of the connector configuration in the BPMN element
  private static final String INBOUND_CONNECTOR_ID = "941c5492-ab2b-4305-aa18-ac86991ff4ca";

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

  @Test
  void shouldInvokeInAndOutboundConnectors() throws IOException {
    // given
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("connector-process.bpmn")
        .send()
        .join();

    // when
    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("connector-process")
            .latestVersion()
            .variable("key", "key-1")
            .send()
            .join();

    // then: outbound connector is invoked
    CamundaAssert.assertThatProcessInstance(processInstance)
        .isActive()
        .hasCompletedElements(byName("Get connectors readiness status"))
        .hasVariable("health", "UP");

    // when: invoke the inbound connector
    final String inboundAddress =
        processTestContext.getConnectorsAddress() + "/inbound/" + INBOUND_CONNECTOR_ID;
    final HttpPost request = new HttpPost(inboundAddress);
    final String requestBody = "{\"key\":\"key-1\"}";
    request.setEntity(HttpEntities.create(requestBody, ContentType.APPLICATION_JSON));

    try (final CloseableHttpClient httpClient = HttpClients.createDefault()) {
      Awaitility.await()
          .atMost(Duration.ofSeconds(10))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(
              () -> {
                final Integer responseCode = httpClient.execute(request, HttpResponse::getCode);
                assertThat(responseCode)
                    .describedAs("Expect invoking the inbound connector successfully")
                    .isEqualTo(200);
              });
    }

    // then
    CamundaAssert.assertThatProcessInstance(processInstance)
        .isCompleted()
        .hasCompletedElements(byName("Wait for HTTP POST request"));
  }

  @Test
  void shouldInvokeOutboundConnectors() {
    // given
    WIREMOCK.stubFor(
        get(urlPathMatching("/test"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withStatus(200)
                    .withBody("{\"status\":\"okay\"}")));

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
        .isCompleted()
        .hasCompletedElements(byName("Mocked Outbound Connector"))
        .hasVariable("status", "okay");

    WIREMOCK.verify(getRequestedFor(urlEqualTo("/test")));
  }
}
