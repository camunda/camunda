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
package io.camunda;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.CamundaAssert.assertThatUserTask;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byElementId;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.Testcontainers;
import org.wiremock.spring.EnableWireMock;

@EnableWireMock
@SpringBootTest(
    properties = {
      "camunda.client.worker.defaults.enabled=false", // disable job workers
      "camunda.process-test.connectors-enabled=true",
      "camunda.process-test.connectors-secrets.INVOICE_REJECTION_URL=http://host.testcontainers.internal:${wiremock.server.port}"
    })
@CamundaSpringProcessTest
public class InvoiceApprovalIntegrationTest {

  @Value("${wiremock.server.port}")
  private int wireMockPort;

  @Autowired private CamundaClient client;

  @Autowired private CamundaProcessTestContext processTestContext;

  @Autowired private ObjectMapper objectMapper;

  private final String invoiceJson =
      """
      {
        "id": "INV-1001",
        "amount": 12000,
        "currency": "EUR",
        "supplier": {
          "id": "0815",
          "name": "Acme GmbH"
        },
        "contactEmail": "accounting@acme.com"
      }""";

  @BeforeEach
  void setup() {
    Testcontainers.exposeHostPorts(wireMockPort);
  }

  @Test
  public void testRejectionPath() throws Exception {
    final Map<String, Object> variables = new HashMap<>();
    variables.put("approver", "Zee");
    variables.put("invoice", objectMapper.readTree(invoiceJson));

    // Create a stub for the HTTP endpoint that is invoked by the Connector
    stubFor(post("/reject").willReturn(aResponse().withStatus(200).withBody("ok")));

    // Kick of the process instance
    final var processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("Process_InvoiceApproval")
            .latestVersion()
            .variables(variables)
            .send()
            .join();

    // assert the User Task and simulate a human decision
    assertThatUserTask(byElementId("UserTask_ApproveInvoice")).isCreated().hasAssignee("Zee");
    processTestContext.completeUserTask(
        byElementId("UserTask_ApproveInvoice"),
        Map.of( //
            "approved",
            false, //
            "rejectionReason",
            "it is a test case :-)"));

    // This should make the process instance execute till the end
    assertThat(processInstance)
        .hasCompletedElementsInOrder(
            byId("StartEvent_InvoiceReceived"),
            byId("UserTask_ApproveInvoice"),
            byId("Gateway_Approved"),
            byId("ServiceTask_SendRejection"),
            byId("EndEvent_InvoiceRejected"))
        .isCompleted();

    // Assert that the HTTP endpoint was actually invoked with the right parameters
    verify(
        postRequestedFor(urlEqualTo("/reject"))
            .withRequestBody(
                equalToJson(
                    """
                          {
                            "invoiceId": "INV-1001",
                            "rejectionReason": "it is a test case :-)"
                          }""")));
  }
}
