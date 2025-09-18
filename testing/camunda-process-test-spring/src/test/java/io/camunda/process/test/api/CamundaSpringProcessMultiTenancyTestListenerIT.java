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
package io.camunda.process.test.api;

import static io.camunda.process.test.api.assertions.ElementSelectors.byName;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/*
 * This test is a combination of the ConnectorsIT and the ExtensionIT to ensure
 * that the new multi-tenancy configuration works with and without the connectors container.
 */
@SpringBootTest(
    classes = {CamundaSpringProcessMultiTenancyTestListenerIT.class},
    properties = {
      "io.camunda.process.test.multi-tenancy-enabled=true",
      "io.camunda.process.test.connectors-enabled=true",
      "io.camunda.process.test.connectors-secrets.CONNECTORS_URL=http://connectors:8080/actuator/health/readiness"
    })
@CamundaSpringProcessTest
public class CamundaSpringProcessMultiTenancyTestListenerIT {

  // The ID is part of the connector configuration in the BPMN element
  private static final String INBOUND_CONNECTOR_ID = "941c5492-ab2b-4305-aa18-ac86991ff4ca";

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

  @Test
  void shouldCreateProcessInstance() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .name("start")
            .zeebeOutputExpression("\"active\"", "status")
            .userTask()
            .name("task")
            .endEvent()
            .name("end")
            .zeebeOutputExpression("\"ok\"", "result")
            .done();

    client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

    // when
    final ProcessInstanceEvent processInstance =
        client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();

    // then
    CamundaAssert.assertThatProcessInstance(processInstance)
        .isActive()
        .hasActiveElements(byName("task"))
        .hasVariable("status", "active");
  }

  @Test
  void shouldTriggerTimerEvent() {
    // given
    final Duration timerDuration = Duration.ofHours(1);

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .name("start")
            .userTask("A")
            .name("A")
            .endEvent()
            // attach boundary timer event
            .moveToActivity("A")
            .boundaryEvent()
            .timerWithDuration(timerDuration.toString())
            .userTask()
            .name("B")
            .endEvent()
            .done();

    client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

    final ProcessInstanceEvent processInstance =
        client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();

    // when
    CamundaAssert.assertThatProcessInstance(processInstance).hasActiveElements(byName("A"));

    final Instant timeBefore = processTestContext.getCurrentTime();

    processTestContext.increaseTime(timerDuration);

    final Instant timeAfter = processTestContext.getCurrentTime();

    // then
    CamundaAssert.assertThatProcessInstance(processInstance)
        .hasTerminatedElements(byName("A"))
        .hasActiveElements(byName("B"));

    assertThat(Duration.between(timeBefore, timeAfter))
        .isCloseTo(timerDuration, Duration.ofSeconds(10));
  }

  @Test
  void shouldInvokeInAndOutboundConnectors() throws IOException {
    // given
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("connector-process.bpmn")
        .send()
        .join();

    // when
    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("connector-process")
            .latestVersion()
            .variable("key", "key-1")
            .send()
            .join();

    // then: outbound connector is invoked
    CamundaAssert.assertThatProcessInstance(processInstance)
        .isActive()
        .hasCompletedElements(byName("Get connectors readiness status"))
        .hasVariable("health", "UP");

    // when: invoke the inbound connector
    final String inboundAddress =
        processTestContext.getConnectorsAddress() + "/inbound/" + INBOUND_CONNECTOR_ID;
    final HttpPost request = new HttpPost(inboundAddress);
    final String requestBody = "{\"key\":\"key-1\"}";
    request.setEntity(HttpEntities.create(requestBody, ContentType.APPLICATION_JSON));

    try (final CloseableHttpClient httpClient = HttpClients.createDefault()) {
      Awaitility.await()
          .atMost(Duration.ofSeconds(10))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(
              () -> {
                final Integer responseCode = httpClient.execute(request, HttpResponse::getCode);
                assertThat(responseCode)
                    .describedAs("Expect invoking the inbound connector successfully")
                    .isEqualTo(200);
              });
    }

    // then
    CamundaAssert.assertThatProcessInstance(processInstance)
        .isCompleted()
        .hasCompletedElements(byName("Wait for HTTP POST request"));
  }
}
