/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.example;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.assertions.ElementSelectors.byName;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.time.Duration;
import org.example.services.InventoryService;
import org.example.services.PaymentService;
import org.example.services.ShippingService;
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
