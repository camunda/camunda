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
package io.camunda.client.util;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.client.CamundaClient;
import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

@WireMockTest
public abstract class ClientRestTest {

  public CamundaClient client;
  public RestGatewayService gatewayService;

  @BeforeEach
  void beforeEach(final WireMockRuntimeInfo mockInfo) throws URISyntaxException {
    client = createClient(mockInfo);
    gatewayService = new RestGatewayService(mockInfo);
  }

  @AfterEach
  void afterEach() {
    if (client != null) {
      client.close();
    }
  }

  private CamundaClient createClient(final WireMockRuntimeInfo mockInfo) throws URISyntaxException {
    return CamundaClient.newClientBuilder()
        .usePlaintext()
        .preferRestOverGrpc(true)
        .restAddress(new URI(mockInfo.getHttpBaseUrl()))
        .build();
  }
}
