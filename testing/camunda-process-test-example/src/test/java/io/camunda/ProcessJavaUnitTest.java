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
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
public class ProcessJavaUnitTest {

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

  @Test
  void happyPath() {
    // given
    final String shippingId = "shipping-1";

    completeJobs("collect-money", Collections.emptyMap());
    completeJobs("fetch-items", Collections.emptyMap());
    completeJobs("ship-parcel", Map.of("shipping_id", shippingId));

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

  @Test
  void requestTrackingCode() {
    // given
    final String shippingId = "shipping-2";

    completeJobs("collect-money", Collections.emptyMap());
    completeJobs("fetch-items", Collections.emptyMap());
    completeJobs("ship-parcel", Map.of("shipping_id", shippingId));
    completeJobs("request-tracking-code", Collections.emptyMap());

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

  private void completeJobs(final String jobType, final Map<String, Object> variables) {
    client
        .newWorker()
        .jobType(jobType)
        .handler((jobClient, job) -> jobClient.newCompleteCommand(job).variables(variables).send())
        .open();
  }
}
