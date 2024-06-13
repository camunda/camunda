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
package io.camunda.zeebe.client.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.zeebe.client.impl.http.HttpClientFactory;
import io.camunda.zeebe.client.protocol.rest.TopologyResponse;
import org.junit.jupiter.api.Assertions;

public class RestGatewayService {

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private static final String URL_TOPOLOGY = HttpClientFactory.REST_API_PATH + "/topology";

  private final WireMockRuntimeInfo mockInfo;

  protected RestGatewayService(final WireMockRuntimeInfo mockInfo) {
    this.mockInfo = mockInfo;
  }

  /**
   * Register the given response for GET requests to {@value #URL_TOPOLOGY}
   *
   * @param topologyResponse the response to provide upon a topology request
   */
  public void onTopologyRequest(final TopologyResponse topologyResponse) {
    mockInfo
        .getWireMock()
        .register(WireMock.get(URL_TOPOLOGY).willReturn(WireMock.okJson(toJson(topologyResponse))));
  }

  private static String toJson(final Object response) {
    try {
      return JSON_MAPPER.writeValueAsString(response);
    } catch (final JsonProcessingException e) {
      Assertions.fail("Couldn't serialize response body to JSON", e);
      return null;
    }
  }
}
