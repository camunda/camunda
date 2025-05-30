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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.spec.CamundaProcessSpecResource;
import io.camunda.process.test.api.spec.CamundaProcessSpecRunner;
import io.camunda.process.test.api.spec.CamundaProcessSpecSource;
import io.camunda.process.test.api.spec.CamundaProcessSpecTestCase;
import io.camunda.services.InventoryService;
import io.camunda.services.PaymentService;
import io.camunda.services.ShippingService;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
@CamundaSpringProcessTest
public class ProcessSpecTest {

  @Autowired private CamundaProcessSpecRunner processSpecRunner;

  // mock services from job workers
  @MockBean private PaymentService paymentService;
  @MockBean private InventoryService inventoryService;
  @MockBean private ShippingService shippingService;

  @ParameterizedTest
  @CamundaProcessSpecSource(specFile = "/specs/order-process.spec")
  void happyPath(
      final CamundaProcessSpecTestCase testCase, final List<CamundaProcessSpecResource> resources) {
    // given - (optional) set up mocks, job workers, etc.
    final String orderId = "order-1";
    final String shippingId = "shipping-1";

    when(shippingService.shipOrder(orderId)).thenReturn(shippingId);

    // when - run and verify the test case
    processSpecRunner.runTestCase(testCase, resources);

    // then - (optional) verify mock invocations, external resources, etc.
    verify(paymentService).processPayment(orderId);
    verify(inventoryService).fetchItems(orderId);
    verify(shippingService).shipOrder(orderId);
    verifyNoMoreInteractions(paymentService, inventoryService, shippingService);
  }

  @ParameterizedTest
  @CamundaProcessSpecSource(specFile = "/specs/order-process-2.spec")
  void requestTrackingCode(
      final CamundaProcessSpecTestCase testCase, final List<CamundaProcessSpecResource> resources) {
    // given - (optional) set up mocks, job workers, etc.
    final String orderId = "order-2";
    final String shippingId = "shipping-2";

    when(shippingService.shipOrder(orderId)).thenReturn(shippingId);

    // when - run and verify the test case
    processSpecRunner.runTestCase(testCase, resources);

    // then - (optional) verify mock invocations, external resources, etc.
    verify(paymentService).processPayment(orderId);
    verify(inventoryService).fetchItems(orderId);
    verify(shippingService).shipOrder(orderId);
    verify(shippingService).requestTrackingCode(shippingId);
    verifyNoMoreInteractions(paymentService, inventoryService, shippingService);
  }
}
