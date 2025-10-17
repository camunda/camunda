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
import static io.camunda.process.test.api.assertions.ElementSelectors.byName;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
public class ProcessNestedUnitTest {

  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @BeforeEach
  public void setUp() {
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("bpmn/order-process.bpmn")
        .send()
        .join();
  }

  @Nested
  @DisplayName("Happy path")
  class HappyPathTests {
    @Test
    void happyPath() {
      // given
      final String shippingId = "shipping-1";

      processTestContext.mockJobWorker("collect-money").thenComplete();
      processTestContext.mockJobWorker("fetch-items").thenComplete();
      processTestContext
          .mockJobWorker("ship-parcel")
          .thenComplete(Map.of("shipping_id", shippingId));

      final var processInstance =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId("order-process")
              .latestVersion()
              .variable("order_id", "order-1")
              .send()
              .join();

      // when
      assertThat(processInstance).hasActiveElements(byName("Received tracking code"));

      client
          .newPublishMessageCommand()
          .messageName("Received tracking code")
          .correlationKey(shippingId)
          .variable("tracking_code", "tracking-1")
          .send();

      // then
      assertThat(processInstance)
          .hasCompletedElements(
              byName("Collect money"),
              byName("Fetch items"),
              byName("Ship parcel"),
              byName("Received tracking code"))
          .isCompleted();
    }
  }

  @Nested
  class ExceptionalTests {
    @Test
    void requestTrackingCode() {
      // given
      final String shippingId = "shipping-2";

      processTestContext.mockJobWorker("collect-money").thenComplete();
      processTestContext.mockJobWorker("fetch-items").thenComplete();
      processTestContext
          .mockJobWorker("ship-parcel")
          .thenComplete(Map.of("shipping_id", shippingId));
      processTestContext.mockJobWorker("request-tracking-code").thenComplete();

      final var processInstance =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId("order-process")
              .latestVersion()
              .variable("order_id", "order-2")
              .send()
              .join();

      // when
      assertThat(processInstance).hasActiveElements(byName("Received tracking code"));

      processTestContext.increaseTime(Duration.ofDays(2));

      // then
      assertThat(processInstance)
          .hasCompletedElements(byName("Request tracking code"))
          .hasActiveElements(byName("Received tracking code"))
          .isActive();
    }
  }
}
