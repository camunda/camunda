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
package io.camunda.client.impl.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.client.CamundaClient;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.client.util.ClientRestTest;
import java.net.URI;
import java.time.Duration;
import java.util.stream.Stream;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A 429 or 503 can reach the client after the gateway has already executed the command, so the
 * transport must not resend the request on its own. Retrying is left to the caller.
 */
class HttpClientAutomaticRetryTest extends ClientRestTest {

  private static final String PATH = "/retry-probe";
  private static final String URL = HttpClientFactory.REST_API_PATH + PATH;

  private HttpClient httpClient;

  static Stream<Arguments> retryableResponses() {
    return Stream.of(Method.GET, Method.POST, Method.PUT, Method.PATCH, Method.DELETE)
        .flatMap(method -> Stream.of(429, 503).map(status -> Arguments.of(method, status)));
  }

  @BeforeEach
  void createHttpClient(final WireMockRuntimeInfo mockInfo) {
    final CamundaClientBuilderImpl configuration =
        (CamundaClientBuilderImpl)
            CamundaClient.newClientBuilder()
                .applyEnvironmentVariableOverrides(false)
                .preferRestOverGrpc(true)
                .restAddress(URI.create(mockInfo.getHttpBaseUrl()));
    httpClient = new HttpClientFactory(configuration).createClient();
    httpClient.start();
  }

  @AfterEach
  void closeHttpClient() throws Exception {
    httpClient.close();
  }

  @ParameterizedTest
  @MethodSource("retryableResponses")
  void shouldSendRequestOnlyOnceWhenServerAnswersWithRetryableStatus(
      final Method method, final int status) {
    // given
    stubFor(any(urlEqualTo(URL)).willReturn(aResponse().withStatus(status)));

    // when
    final HttpCamundaFuture<Void> result = send(method);

    // then
    assertThat(result).failsWithin(Duration.ofSeconds(5));
    verify(exactly(1), anyRequestedFor(urlEqualTo(URL)));
  }

  private HttpCamundaFuture<Void> send(final Method method) {
    final HttpCamundaFuture<Void> result = new HttpCamundaFuture<>();
    final RequestConfig requestConfig = httpClient.newRequestConfig().build();
    switch (method) {
      case GET:
        httpClient.get(PATH, requestConfig, Void.class, entity -> null, result);
        break;
      case POST:
        httpClient.post(PATH, "{}", requestConfig, entity -> null, result);
        break;
      case PUT:
        httpClient.put(PATH, "{}", requestConfig, entity -> null, result);
        break;
      case PATCH:
        httpClient.patch(PATH, "{}", requestConfig, entity -> null, result);
        break;
      case DELETE:
        httpClient.delete(PATH, requestConfig, entity -> null, result);
        break;
      default:
        throw new IllegalArgumentException("Unsupported method " + method);
    }
    return result;
  }
}
