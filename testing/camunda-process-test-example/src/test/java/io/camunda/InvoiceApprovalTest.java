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

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.CamundaAssert.assertThatUserTask;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byElementId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.services.ArchiveService;
import io.camunda.services.WiredLegacyException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {
      "camunda.client.worker.defaults.enabled=false", // disable job workers
      // but enable the normal glue code that just delegates to the ArchiveService
      "camunda.client.worker.override.archive-invoice.enabled=true",
    })
@CamundaSpringProcessTest
public class InvoiceApprovalTest {

  @Autowired private CamundaClient client;

  @Autowired private CamundaProcessTestContext processTestContext;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private ArchiveService archiveService;

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

  @DisplayName("Happy path through the process")
  @Test
  public void happyPath() throws Exception {
    final HashMap<String, Object> variables = new HashMap<String, Object>();
    variables.put("approver", "Zee");
    variables.put("invoice", objectMapper.readTree(invoiceJson));

    // Let's define a mock in case there is no service interface in between - this is close to what
    // you do with Connectors
    final AtomicBoolean addInvoiceJobWorkerCalled = new AtomicBoolean(false);
    processTestContext
        .mockJobWorker("add-invoice-to-accounting")
        .withHandler(
            (jobClient, job) -> {
              addInvoiceJobWorkerCalled.set(true);
              // check input mapping
              org.assertj.core.api.Assertions.assertThat(job.getVariablesAsMap().get("invoiceId"))
                  .isEqualTo("INV-1001");
              jobClient
                  .newCompleteCommand(job)
                  // .variables(null) //  We could now also simulate setting some response values
                  .send()
                  .join();
            });

    // and now kick off the process instance
    final var processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("Process_InvoiceApproval")
            .latestVersion()
            .variables(variables)
            .send()
            .join();

    // assert the User Task and simulate a human decision
    assertThatProcessInstance(processInstance).hasActiveElements("UserTask_ApproveInvoice");
    assertThatUserTask(byElementId("UserTask_ApproveInvoice")).isCreated().hasAssignee("Zee");
    processTestContext.completeUserTask(
        byElementId("UserTask_ApproveInvoice"), Map.of("approved", true));

    // This should make the process instance execute till the end
    assertThatProcessInstance(processInstance)
        .hasCompletedElementsInOrder(
            byId("StartEvent_InvoiceReceived"),
            byId("UserTask_ApproveInvoice"),
            byId("ServiceTask_ArchiveInvoice"),
            byId("ServiceTask_AddInvoiceAccounting"),
            byId("EndEvent_InvoiceApproved"))
        .isCompleted();

    // verify that side effects have happened
    Mockito.verify(archiveService).archiveInvoice("INV-1001", objectMapper.readTree(invoiceJson));
    org.assertj.core.api.Assertions.assertThat(addInvoiceJobWorkerCalled.get())
        .as("add-invoice-to-accounting job worker called")
        .isTrue();
  }

  @DisplayName("Path when invoice was rejected")
  @Test
  public void testRejectionPath() throws Exception {
    final HashMap<String, Object> variables = new HashMap<String, Object>();
    variables.put("approver", "Zee");
    variables.put("invoice", objectMapper.readTree(invoiceJson));

    // We skip HTTP for the simple unit test - mock the http connector
    processTestContext.mockJobWorker("io.camunda:http-json:1").thenComplete();

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
    assertThatProcessInstance(processInstance)
        .hasCompletedElementsInOrder(
            byId("StartEvent_InvoiceReceived"),
            byId("UserTask_ApproveInvoice"),
            byId("Gateway_Approved"),
            byId("ServiceTask_SendRejection"),
            byId("EndEvent_InvoiceRejected"))
        .isCompleted();
  }

  @DisplayName("Path when there is a timeout on the approval")
  @Test
  public void testApprovalTimeout() throws Exception {
    final HashMap<String, Object> variables = new HashMap<String, Object>();
    variables.put("approver", "Zee");
    variables.put("invoice", objectMapper.readTree(invoiceJson));

    processTestContext.mockJobWorker("add-invoice-to-accounting").thenComplete();

    final var processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("Process_InvoiceApproval")
            .latestVersion()
            .variables(variables)
            .send()
            .join();

    // assert the User Task and simulate the timeout
    assertThatProcessInstance(processInstance).hasActiveElements("UserTask_ApproveInvoice");
    processTestContext.increaseTime(Duration.ofDays(5));

    // This should make the process instance auto approve and run till the end
    assertThatProcessInstance(processInstance)
        .isCompleted()
        .hasCompletedElementsInOrder(
            byId("StartEvent_InvoiceReceived"),
            byId("ServiceTask_ArchiveInvoice"),
            byId("ServiceTask_AddInvoiceAccounting"),
            byId("EndEvent_InvoiceApproved"))
        .hasTerminatedElements(byId("UserTask_ApproveInvoice"));
  }

  @DisplayName("Path when the archive system raises an error")
  @Test
  public void testArchiveSystemError() throws Exception {
    final HashMap<String, Object> variables = new HashMap<String, Object>();
    variables.put("approver", "Zee");
    variables.put("invoice", objectMapper.readTree(invoiceJson));

    doThrow(new WiredLegacyException()).when(archiveService).archiveInvoice(anyString(), any());
    processTestContext.mockJobWorker("add-invoice-to-accounting").thenComplete();

    final var processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("Process_InvoiceApproval")
            .latestVersion()
            .variables(variables)
            .send()
            .join();

    // approve the request
    assertThatUserTask(byElementId("UserTask_ApproveInvoice")).isCreated();
    processTestContext.completeUserTask(
        byElementId("UserTask_ApproveInvoice"), Map.of("approved", true));

    //  this should lead to the exception being thrown and the process to end up in the user task to
    // handle the problem
    assertThatUserTask(byElementId("UserTask_ManuallyArchiveInvoice"))
        .isCreated()
        .hasCandidateGroup(
            "archive-team"); // probably not worth to test as it limits flexibility in model changes
    processTestContext.completeUserTask(byElementId("UserTask_ManuallyArchiveInvoice"));

    verify(archiveService).archiveInvoice(anyString(), any(JsonNode.class));

    assertThatProcessInstance(processInstance)
        .isCompleted()
        .hasCompletedElementsInOrder(
            byId("StartEvent_InvoiceReceived"),
            byId("UserTask_ApproveInvoice"),
            byId("UserTask_ManuallyArchiveInvoice"),
            byId("ServiceTask_AddInvoiceAccounting"),
            byId("EndEvent_InvoiceApproved"))
        .hasTerminatedElements(byId("ServiceTask_ArchiveInvoice"));
  }
}
