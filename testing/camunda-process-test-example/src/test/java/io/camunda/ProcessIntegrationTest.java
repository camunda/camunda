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
import static io.camunda.process.test.api.assertions.ElementSelectors.byName;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.services.InventoryService;
import io.camunda.services.PaymentService;
import io.camunda.services.ShippingService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
@CamundaSpringProcessTest
public class ProcessIntegrationTest {

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

  // mock services from job workers
  @MockBean private PaymentService paymentService;
  @MockBean private InventoryService inventoryService;
  @MockBean private ShippingService shippingService;

  @Test
  void happyPath() {
    // given
    final String orderId = "order-1";
    final String shippingId = "shipping-1";

    when(shippingService.shipOrder(orderId)).thenReturn(shippingId);

    final var processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("order-process")
            .latestVersion()
            .variable("order_id", orderId)
            .send()
            .join();

    // when
    assertThatProcessInstance(processInstance)
        .hasActiveElements(byName("Received tracking code"))
        .hasVariable("shipping_id", shippingId);

    client
        .newPublishMessageCommand()
        .messageName("Received tracking code")
        .correlationKey(shippingId)
        .variable("tracking_code", "tracking-1")
        .send();

    // then
    assertThatProcessInstance(processInstance)
        .hasCompletedElements(
            byName("Collect money"),
            byName("Fetch items"),
            byName("Ship parcel"),
            byName("Received tracking code"))
        .isCompleted();

    verify(paymentService).processPayment(orderId);
    verify(inventoryService).fetchItems(orderId);
    verify(shippingService).shipOrder(orderId);
    verifyNoMoreInteractions(paymentService, inventoryService, shippingService);
  }

  @Test
  void requestTrackingCode() {
    // given
    final String orderId = "order-2";
    final String shippingId = "shipping-2";

    when(shippingService.shipOrder(orderId)).thenReturn(shippingId);

    final var processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("order-process")
            .latestVersion()
            .variable("order_id", orderId)
            .send()
            .join();

    // when
    assertThatProcessInstance(processInstance)
        .hasActiveElements(byName("Received tracking code"))
        .hasVariable("shipping_id", shippingId);

    processTestContext.increaseTime(Duration.ofDays(2));

    // then
    assertThatProcessInstance(processInstance)
        .hasCompletedElements(byName("Request tracking code"))
        .hasActiveElements(byName("Received tracking code"))
        .isActive();

    verify(paymentService).processPayment(orderId);
    verify(inventoryService).fetchItems(orderId);
    verify(shippingService).shipOrder(orderId);
    verify(shippingService).requestTrackingCode(shippingId);
    verifyNoMoreInteractions(paymentService, inventoryService, shippingService);
  }
}
