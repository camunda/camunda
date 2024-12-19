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
package io.camunda.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.CompleteUserTaskResponse;
import io.camunda.client.api.response.Topology;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Future;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@WireMockTest
public class ClientRestInterceptorTest {

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private CamundaClient client;

  @BeforeEach
  void setUp(final WireMockRuntimeInfo mockInfo) throws URISyntaxException {
    client = createClient(mockInfo);
  }

  @AfterEach
  void tearDown() {
    if (client != null) {
      client.close();
    }
  }

  @Test
  void shouldHaveTwoInterceptorsInExactOrder() {
    // given a Camunda client with interceptors

    // then the interceptors are in the correct order
    assertThat(client.getConfiguration().getChainHandlers())
        .hasSize(2)
        .map(AsyncExecChainHandler::getClass)
        .containsExactly(TestTopologyInterceptor.class, TestUserTasksInterceptor.class);
  }

  @Test
  void shouldTriggerFirstClientInterceptorForTopologyRequests(final WireMockRuntimeInfo mockInfo)
      throws JsonProcessingException {
    // given
    final WireMock mock = mockInfo.getWireMock();
    mock.register(
        WireMock.get("/v2/topology")
            .willReturn(WireMock.okJson(JSON_MAPPER.writeValueAsString("{}"))));

    // when
    final Future<Topology> response = client.newTopologyRequest().send();

    // then
    assertThatThrownBy(() -> response.get())
        .hasCauseInstanceOf(ClientException.class)
        .hasMessageContaining("No topology requests while testing.");
  }

  @Test
  void shouldTriggerSecondClientInterceptorForUserTaskRequests(final WireMockRuntimeInfo mockInfo)
      throws JsonProcessingException {
    // given
    final WireMock mock = mockInfo.getWireMock();
    mock.register(
        WireMock.post("/v2/user-tasks/1234/completion")
            .willReturn(WireMock.okJson(JSON_MAPPER.writeValueAsString("{}"))));

    // when
    final Future<CompleteUserTaskResponse> response =
        client.newUserTaskCompleteCommand(1234L).send();

    // then
    assertThatThrownBy(() -> response.get())
        .hasCauseInstanceOf(ClientException.class)
        .hasMessageContaining("No user task requests while testing.");
  }

  private CamundaClient createClient(final WireMockRuntimeInfo mockInfo) throws URISyntaxException {
    return CamundaClient.newClientBuilder()
        .usePlaintext()
        .preferRestOverGrpc(true)
        .restAddress(new URI(mockInfo.getHttpBaseUrl()))
        .withChainHandlers(new TestTopologyInterceptor(), new TestUserTasksInterceptor())
        .build();
  }

  static class TestTopologyInterceptor implements AsyncExecChainHandler {

    @Override
    public void execute(
        final HttpRequest httpRequest,
        final AsyncEntityProducer asyncEntityProducer,
        final AsyncExecChain.Scope scope,
        final AsyncExecChain asyncExecChain,
        final AsyncExecCallback asyncExecCallback)
        throws HttpException, IOException {

      asyncExecChain.proceed(
          httpRequest,
          asyncEntityProducer,
          scope,
          new AsyncExecCallback() {
            @Override
            public AsyncDataConsumer handleResponse(
                final HttpResponse response, final EntityDetails entityDetails)
                throws HttpException, IOException {
              if (httpRequest.getRequestUri().contains("/topology")) {
                throw new RuntimeException("No topology requests while testing.");
              }
              return asyncExecCallback.handleResponse(response, entityDetails);
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
    }
  }

  static class TestUserTasksInterceptor implements AsyncExecChainHandler {
    @Override
    public void execute(
        final HttpRequest httpRequest,
        final AsyncEntityProducer asyncEntityProducer,
        final AsyncExecChain.Scope scope,
        final AsyncExecChain asyncExecChain,
        final AsyncExecCallback asyncExecCallback)
        throws HttpException, IOException {

      asyncExecChain.proceed(
          httpRequest,
          asyncEntityProducer,
          scope,
          new AsyncExecCallback() {
            @Override
            public AsyncDataConsumer handleResponse(
                final HttpResponse response, final EntityDetails entityDetails)
                throws HttpException, IOException {
              if (httpRequest.getRequestUri().contains("/user-tasks")) {
                throw new RuntimeException("No user task requests while testing.");
              }
              return asyncExecCallback.handleResponse(response, entityDetails);
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
    }
  }
}
