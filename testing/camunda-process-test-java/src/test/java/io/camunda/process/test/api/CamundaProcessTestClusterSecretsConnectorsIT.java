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
import java.io.IOException;
import java.time.Duration;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Connectors resolve {@code camunda.secrets} references in their job input through the engine and
 * in their inbound properties through {@code /v2/secrets/resolve}; both must see the cluster
 * secrets.
 */
public class CamundaProcessTestClusterSecretsConnectorsIT {

  private static final String WEBHOOK_CONTEXT = "cluster-secrets-webhook";

  @RegisterExtension
  private static final CamundaProcessTestExtension EXTENSION =
      new CamundaProcessTestExtension()
          .withConnectorsEnabled(true)
          .withClusterSecret("HEALTH_PATH", "readiness")
          .withClusterSecret("WEBHOOK_CONTEXT", WEBHOOK_CONTEXT);

  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @Test
  void shouldResolveClusterSecretsInOutboundAndInboundConnectors() throws IOException {
    // given
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("cluster-secrets-connector-process.bpmn")
        .send()
        .join();

    // when
    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("cluster-secrets-connector-process")
            .latestVersion()
            .variable("key", "key-1")
            .send()
            .join();

    // then: the outbound connector input is resolved, i.e. the health URL is reachable
    CamundaAssert.assertThatProcessInstance(processInstance)
        .isActive()
        .hasCompletedElements(byName("Get connectors readiness status"))
        .hasVariable("health", "UP");

    // when: the webhook is registered under the context resolved from the cluster secret
    final HttpPost request =
        new HttpPost(processTestContext.getConnectorsAddress() + "/inbound/" + WEBHOOK_CONTEXT);
    request.setEntity(HttpEntities.create("{\"key\":\"key-1\"}", ContentType.APPLICATION_JSON));

    try (final CloseableHttpClient httpClient = HttpClients.createDefault()) {
      Awaitility.await("until the inbound connector accepts the request")
          .atMost(Duration.ofSeconds(30))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(
              () -> assertThat(httpClient.execute(request, HttpResponse::getCode)).isEqualTo(200));
    }

    // then
    CamundaAssert.assertThatProcessInstance(processInstance)
        .isCompleted()
        .hasCompletedElements(byName("Wait for HTTP POST request"));
  }
}
