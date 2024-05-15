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
package io.camunda.zeebe.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.zeebe.client.api.response.Topology;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@WireMockTest
public final class ClientInterceptorRestTest {
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private ZeebeClient client;

  @BeforeEach
  void beforeEach(final WireMockRuntimeInfo mockInfo) throws URISyntaxException {
    client = createClient(mockInfo);
  }

  @AfterEach
  void afterEach() {
    if (client != null) {
      client.close();
    }
  }

  @Test
  void shouldInterceptRequest(final WireMockRuntimeInfo mockInfo)
      throws JsonProcessingException, ExecutionException, InterruptedException {
    // given
    final WireMock mock = mockInfo.getWireMock();
    mock.register(
        WireMock.get("/v1/topology")
            .willReturn(WireMock.okJson(JSON_MAPPER.writeValueAsString("{}"))));

    // when
    final Future<Topology> response = client.newTopologyRequest().send();

    // then
    assertThatThrownBy(() -> response.get())
        .hasRootCauseInstanceOf(HttpException.class)
        .hasRootCauseMessage("This is a test");
  }

  private ZeebeClient createClient(final WireMockRuntimeInfo mockInfo) throws URISyntaxException {
    return ZeebeClient.newClientBuilder()
        .usePlaintext()
        .preferRestOverGrpc(true)
        .restAddress(new URI(mockInfo.getHttpBaseUrl()))
        .withChainHandlers(
            (request, entityProducer, scope, chain, asyncExecCallback) -> {
              chain.proceed(
                  request,
                  entityProducer,
                  scope,
                  new AsyncExecCallback() {

                    @Override
                    public AsyncDataConsumer handleResponse(
                        final HttpResponse response, final EntityDetails entityDetails)
                        throws HttpException {
                      throw new HttpException("This is a test");
                    }

                    @Override
                    public void handleInformationResponse(final HttpResponse response)
                        throws HttpException, IOException {
                      asyncExecCallback.handleInformationResponse(response);
                    }

                    @Override
                    public void completed() {
                      asyncExecCallback.completed();
                    }

                    @Override
                    public void failed(final Exception cause) {
                      asyncExecCallback.failed(cause);
                    }
                  });
            })
        .build();
  }
}
