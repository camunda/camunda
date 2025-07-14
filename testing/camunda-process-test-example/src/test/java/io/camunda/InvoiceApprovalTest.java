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
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.services.ArchiveService;

@SpringBootTest(
    properties = {
      "camunda.client.worker.defaults.enabled=false", // disable job workers
      "camunda.client.worker.override.archive-invoice.enabled=true" // use this worker to test the scope with the glue code
    })
@CamundaSpringProcessTest
public class InvoiceApprovalTest {

  @Autowired
  private CamundaClient client;

  @Autowired
  private CamundaProcessTestContext processTestContext;
  
  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private ArchiveService archiveService;

  @Test
  void happyPath() throws JsonMappingException, JsonProcessingException {
    final String invoiceJson = """
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

    final HashMap<String, Object> variables = new HashMap<String, Object>();
    variables.put("approver", "Zee");
    variables.put("invoice", objectMapper.readTree(invoiceJson));

    // This is NOT working:
    //////////////////////////
    // Let's define a mock in case there is no service interface in between - this is close to what you do with Connectors
    final AtomicBoolean addInvoiceJobWorkerCalled = new AtomicBoolean(false);
    processTestContext.mockJobWorker("add-invoice-to-accounting").withHandler((jobClient, job) -> {
      addInvoiceJobWorkerCalled.set(true);
      // check input mapping
      assertEquals("INV-1001", job.getVariablesAsMap().get("invoiceId"));
      assertEquals(invoiceJson, job.getVariablesAsMap().get("invoice"));
      jobClient.newCompleteCommand(job)
        //.variables(null) //  We could now also simulate setting some response values
        .send().join();
    });

    // This is working:
    //////////////////////////
    processTestContext.mockJobWorker("add-invoice-to-accounting").thenComplete();


    // and now kick of the process instance
    final var processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("Process_InvoiceApproval")
            .latestVersion()
            .variables(variables)
            .send()
            .join();

    assertThat(processInstance).hasActiveElements("UserTask_ApproveInvoice");
    assertThat(byElementId("UserTask_ApproveInvoice"))
      .isCreated()
      .hasAssignee("Zee");

    processTestContext.completeUserTask(
        byElementId("UserTask_ApproveInvoice"),
        Map.of("approved", true));

    assertThat(processInstance)
        .hasCompletedElementsInOrder(
            byId("StartEvent_InvoiceReceived"),
            byId("UserTask_ApproveInvoice"),
            byId("ServiceTask_ArchiveInvoice"),
            byId("ServiceTask_AddInvoiceAccounting"),
            byId("EndEvent_InvoiceApproved"))
        .isCompleted();

    Mockito.verify(archiveService).archiveInvoice("INV-1001", objectMapper.readTree(invoiceJson));
    assertTrue("add-invoice-to-accounting job worker was called", addInvoiceJobWorkerCalled.get());
  }
}
