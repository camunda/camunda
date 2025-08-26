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

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byElementId;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.MediaType;
import org.mockserver.verify.VerificationTimes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    properties = {
      "camunda.client.worker.defaults.enabled=false", // disable job workers
      "io.camunda.process.test.connectors-enabled=true"
    })
@CamundaSpringProcessTest
@Testcontainers
public class InvoiceApprovalIntegrationTest {

  public static final DockerImageName MOCKSERVER_IMAGE =
      DockerImageName.parse("mockserver/mockserver")
          .withTag("mockserver-" + MockServerClient.class.getPackage().getImplementationVersion());

  static MockServerContainer mockServer = new MockServerContainer(MOCKSERVER_IMAGE);

  static {
    mockServer
        .start(); // ensures it's ready before property injection in overrideSecrets() - @Container
    // comes too late
  }

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

  @DynamicPropertySource
  static void overrideSecrets(DynamicPropertyRegistry registry) {
    final String baseUrl = "http://" + mockServer.getHost() + ":" + mockServer.getServerPort();
    registry.add("io.camunda.process.test.connectors-secrets.INVOICE_REJECTION_URL", () -> baseUrl);
  }

  @Test
  public void testRejectionPath() throws Exception {
    final HashMap<String, Object> variables = new HashMap<String, Object>();
    variables.put("approver", "Zee");
    variables.put("invoice", objectMapper.readTree(invoiceJson));

    final MockServerClient mockServerClient =
        new MockServerClient(mockServer.getHost(), mockServer.getServerPort());

    // Create a stub for the HTTP endpoint that is invoked by the Connector
    mockServerClient
        .when(request().withMethod("POST").withPath("/reject"))
        .respond(response().withStatusCode(200).withBody("ok"));

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
    assertThat(byElementId("UserTask_ApproveInvoice")).isCreated().hasAssignee("Zee");
    processTestContext.completeUserTask(
        byElementId("UserTask_ApproveInvoice"),
        Map.of( //
            "approved",
            false, //
            "rejectionReason",
            "it is a test case :-)"));

    Thread.sleep(1000);
    SearchResponse<Incident> searchResponse =
        client
            .newIncidentsByProcessInstanceSearchRequest(processInstance.getProcessInstanceKey())
            .execute();
    for (Incident impl : searchResponse.items()) {
      System.out.println("Incident:");
      System.out.println("  incidentKey: " + impl.getIncidentKey());
      System.out.println("  processDefinitionKey: " + impl.getProcessDefinitionKey());
      System.out.println("  processDefinitionId: " + impl.getProcessDefinitionId());
      System.out.println("  processInstanceKey: " + impl.getProcessInstanceKey());
      System.out.println("  errorType: " + impl.getErrorType());
      System.out.println("  errorMessage: " + impl.getErrorMessage());
      System.out.println("  elementId: " + impl.getElementId());
      System.out.println("  elementInstanceKey: " + impl.getElementInstanceKey());
      System.out.println("  creationTime: " + impl.getCreationTime());
      System.out.println("  state: " + impl.getState());
      System.out.println("  jobKey: " + impl.getJobKey());
      System.out.println("  tenantId: " + impl.getTenantId());
      System.out.println();
      // client.newIncidentGetRequest(impl.getIncidentKey()).execute()
    }

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
    mockServerClient.verify(
        request()
            .withMethod("POST")
            .withBody(
                json(
                    """
            {
              "invoiceId": "INV-1001",
              "rejectionReason": "it is a test case :-)"
            }
          """,
                    MediaType.APPLICATION_JSON))
            .withPath("/reject"),
        VerificationTimes.once());
  }
}
