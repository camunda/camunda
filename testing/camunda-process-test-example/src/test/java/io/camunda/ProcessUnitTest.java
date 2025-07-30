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

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "camunda.client.worker.defaults.enabled=false" // disable all job workers
    })
@CamundaSpringProcessTest
public class ProcessUnitTest {

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

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
    assertThatProcessInstance(processInstance).hasActiveElements(byName("Received tracking code"));

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
    assertThatProcessInstance(processInstance).hasActiveElements(byName("Received tracking code"));

    processTestContext.increaseTime(Duration.ofDays(2));

    // then
    assertThatProcessInstance(processInstance)
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
