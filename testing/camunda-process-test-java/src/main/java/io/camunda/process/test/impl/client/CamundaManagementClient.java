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
package io.camunda.process.test.impl.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.HttpEntities;

public class CamundaManagementClient {

  private static final String CLOCK_ENDPOINT = "/actuator/clock";
  private static final String CLOCK_ADD_ENDPOINT = "/actuator/clock/add";

  private final ObjectMapper objectMapper =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private final CloseableHttpClient httpClient = HttpClients.createDefault();

  private final URI camundaManagementApi;

  public CamundaManagementClient(final URI camundaManagementApi) {
    this.camundaManagementApi = camundaManagementApi;
  }

  public Instant getCurrentTime() {

    try {
      final HttpGet request = new HttpGet(camundaManagementApi + CLOCK_ENDPOINT);
      final String responseBody = sendRequest(request);

      final CamundaClockResponseDto clockResponseDto =
          objectMapper.readValue(responseBody, CamundaClockResponseDto.class);
      return Instant.parse(clockResponseDto.getInstant());

    } catch (final Exception e) {
      throw new RuntimeException("Failed to resolve the current time", e);
    }
  }

  public void increaseTime(final Duration timeToAdd) {

    final HttpPost request = new HttpPost(camundaManagementApi + CLOCK_ADD_ENDPOINT);

    final CamundaAddClockRequestDto requestDto = new CamundaAddClockRequestDto();
    requestDto.setOffsetMilli(timeToAdd.toMillis());

    try {
      final String requestBody = objectMapper.writeValueAsString(requestDto);
      request.setEntity(HttpEntities.create(requestBody, ContentType.APPLICATION_JSON));

      sendRequest(request);

    } catch (final Exception e) {
      throw new RuntimeException("Failed to increase the time", e);
    }
  }

  private String sendRequest(final ClassicHttpRequest request) throws IOException {
    return httpClient.execute(
        request,
        response -> {
          if (response.getCode() != 200) {
            throw new RuntimeException(
                String.format(
                    "Request failed. [code: %d, message: %s]",
                    response.getCode(), HttpClientUtil.getReponseAsString(response)));
          }
          return HttpClientUtil.getReponseAsString(response);
        });
  }
}
