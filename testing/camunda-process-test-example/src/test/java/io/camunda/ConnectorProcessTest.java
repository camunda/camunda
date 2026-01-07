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
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "io.camunda.process.test.connectors-enabled=true",
      "io.camunda.process.test.connectors-secrets.WEATHER_URL=https://api.open-meteo.com/v1/forecast"
    })
@CamundaSpringProcessTest
public class ConnectorProcessTest {

  private static final String CONNECTOR_ID = "1jl2sne";
  private static final String CORRELATION_KEY = "key-1";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

  @Test
  void shouldInvokeConnectors() throws Exception {
    // given
    final var processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("get-weather-info")
            .latestVersion()
            .variable("key", CORRELATION_KEY)
            .send()
            .join();

    // when
    assertThatProcessInstance(processInstance).hasActiveElements(byName("Receive weather request"));

    final var inboundAddress =
        new URI(processTestContext.getConnectorsAddress() + "/inbound/" + CONNECTOR_ID);

    final var weatherInfoRequest =
        new WeatherInfoRequest(CORRELATION_KEY, 52.4946479, 13.3962125, "Europe/Berlin");
    final var requestBody = objectMapper.writeValueAsString(weatherInfoRequest);

    final var request =
        HttpRequest.newBuilder()
            .uri(inboundAddress)
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(requestBody))
            .build();

    final var httpClient = HttpClient.newHttpClient();

    Awaitility.await()
        .untilAsserted(
            () -> {
              final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

              assertThat(response.statusCode())
                  .describedAs("Expect invoking the inbound connector successfully")
                  .isEqualTo(200);
            });

    // then
    assertThatProcessInstance(processInstance)
        .hasCompletedElements(byName("Receive weather request"), byName("Get weather info"))
        .hasVariableNames("temperature", "rain", "weather_code")
        .isCompleted();
  }

  record WeatherInfoRequest(String key, double latitude, double longitude, String timezone) {}
}
