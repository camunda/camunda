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

import static io.camunda.process.test.api.CamundaAssert.assertThatDecision;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "camunda.client.worker.defaults.enabled=false", // disable job workers
    })
@CamundaSpringProcessTest
public class InvoiceDecisionTest {

  @Autowired private CamundaClient client;

  @CsvSource(
      delimiter = '|',
      useHeadersInDisplayName = true,
      textBlock =
          """
          Amount | Supplier ID | Auto-approve |
             30  |     1089    |      true    |
             80  |     0815    |      true    |
            220  |     0815    |     false    |
            220  |     1234    |      true    |
           1000  |     1234    |     false    |
           1000  |     0405    |      true    |
           7500  |     0405    |     false    |
          """)
  @DisplayName("Should auto-approve an invoice")
  @ParameterizedTest
  void shouldAutoApproveInvoice(
      final int invoiceAmount, final String invoiceSupplierId, final boolean autoApprove) {
    // given
    final Map<String, Object> invoiceSupplier = new HashMap<String, Object>();
    invoiceSupplier.put("id", invoiceSupplierId);
    invoiceSupplier.put("name", "Supplier Inc.");

    final Map<String, Object> invoice = new HashMap<String, Object>();
    invoice.put("id", "INV-1001");
    invoice.put("amount", invoiceAmount);
    invoice.put("currency", "EUR");
    invoice.put("supplier", invoiceSupplier);
    invoice.put("contactEmail", "accounting@supplier.com");

    // when
    final EvaluateDecisionResponse evaluateDecisionResponse =
        client
            .newEvaluateDecisionCommand()
            .decisionId("auto-approve-invoice")
            .variable("invoice", invoice)
            .send()
            .join();

    // then
    assertThatDecision(evaluateDecisionResponse).hasOutput(autoApprove);
  }
}
