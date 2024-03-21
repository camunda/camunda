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
package io.camunda.zeebe.client.impl.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.impl.NoopCredentialsProvider;
import io.camunda.zeebe.client.impl.util.VersionUtil;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.RequestConfig.Builder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

public class HttpClientFactory {

  private static final String REST_API_PATH = "/v1";
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private final ZeebeClientConfiguration config;

  public HttpClientFactory(final ZeebeClientConfiguration config) {
    this.config = config;
  }

  public HttpClient createClient() {
    final RequestConfig defaultRequestConfig = defaultClientRequestConfigBuilder().build();
    final CloseableHttpAsyncClient client =
        defaultClientBuilder().setDefaultRequestConfig(defaultRequestConfig).build();
    final URI gatewayAddress = buildGatewayAddress();
    final CredentialsProvider credentialsProvider =
        config.getCredentialsProvider() != null
            ? config.getCredentialsProvider()
            : new NoopCredentialsProvider();

    return new HttpClient(
        client,
        JSON_MAPPER,
        gatewayAddress,
        defaultRequestConfig,
        config.getMaxMessageSize(),
        TimeValue.ofSeconds(15),
        credentialsProvider);
  }

  private URI buildGatewayAddress() {
    try {
      final URIBuilder builder = new URIBuilder(config.getRestAddress()).setPath(REST_API_PATH);
      builder.setScheme(config.isPlaintextConnectionEnabled() ? "http" : "https");

      return builder.build();
    } catch (final URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  private HttpAsyncClientBuilder defaultClientBuilder() {
    final Header acceptHeader =
        new BasicHeader(
            HttpHeaders.ACCEPT,
            String.join(
                ", ",
                ContentType.APPLICATION_JSON.getMimeType(),
                ContentType.APPLICATION_PROBLEM_JSON.getMimeType()));
    final PoolingAsyncClientConnectionManager connectionManager =
        PoolingAsyncClientConnectionManagerBuilder.create().build();

    return HttpAsyncClients.custom()
        .setConnectionManager(connectionManager)
        .setDefaultHeaders(Collections.singletonList(acceptHeader))
        .setUserAgent("zeebe-client-java/" + VersionUtil.getVersion())
        .evictExpiredConnections()
        .setCharCodingConfig(CharCodingConfig.custom().setCharset(StandardCharsets.UTF_8).build())
        .evictIdleConnections(TimeValue.ofSeconds(30))
        .useSystemProperties(); // allow users to customize via system properties
  }

  private Builder defaultClientRequestConfigBuilder() {
    return RequestConfig.custom()
        .setResponseTimeout(Timeout.of(config.getDefaultRequestTimeout()))
        // TODO: determine if the existing (gRPC) property makes sense for the HTTP client
        .setConnectionKeepAlive(TimeValue.of(config.getKeepAlive()))
        // hard cancellation may cause other requests to fail as it will kill the connection; can be
        // enabled when using HTTP/2
        .setHardCancellationEnabled(false);
  }
}
