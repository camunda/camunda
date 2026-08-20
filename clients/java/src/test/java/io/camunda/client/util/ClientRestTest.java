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
import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

@WireMockTest
public abstract class ClientRestTest {

  private static ProxySelector originalProxySelector;

  public CamundaClient client;
  public RestGatewayService gatewayService;

  /**
   * Override the default proxy selector to prevent the Apache HttpClient from routing requests
   * through a system-configured proxy. The HttpAsyncClientBuilder always uses
   * ProxySelector.getDefault() for route planning, which on some CI environments or developer
   * machines may resolve to an external proxy (e.g. nginx), causing intermittent HTML 404 responses
   * instead of the expected WireMock JSON responses.
   */
  @BeforeAll
  static void disableSystemProxy() {
    originalProxySelector = ProxySelector.getDefault();
    ProxySelector.setDefault(
        new ProxySelector() {
          @Override
          public List<Proxy> select(final URI uri) {
            return Collections.singletonList(Proxy.NO_PROXY);
          }

          @Override
          public void connectFailed(final URI uri, final SocketAddress sa, final IOException ioe) {}
        });
  }

  @AfterAll
  static void restoreSystemProxy() {
    ProxySelector.setDefault(originalProxySelector);
  }

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
        .preferRestOverGrpc(true)
        .restAddress(new URI(mockInfo.getHttpBaseUrl()))
        .build();
  }
}
