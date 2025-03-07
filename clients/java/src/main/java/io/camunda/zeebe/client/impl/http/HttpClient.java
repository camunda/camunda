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
import io.camunda.zeebe.client.api.command.ClientException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin abstraction layer on top of Apache's HTTP client to wire up the expected Zeebe API
 * conventions, e.g. errors are always {@link io.camunda.client.protocol.rest.ProblemDetail},
 * content type is always JSON, etc.
 */
public final class HttpClient implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpClient.class);

  private final CloseableHttpAsyncClient client;
  private final ObjectMapper jsonMapper;
  private final URI address;
  private final RequestConfig defaultRequestConfig;
  private final int maxMessageSize;
  private final TimeValue shutdownTimeout;
  private final CredentialsProvider credentialsProvider;

  public HttpClient(
      final CloseableHttpAsyncClient client,
      final ObjectMapper jsonMapper,
      final URI address,
      final RequestConfig defaultRequestConfig,
      final int maxMessageSize,
      final TimeValue shutdownTimeout,
      final CredentialsProvider credentialsProvider) {
    this.client = client;
    this.jsonMapper = jsonMapper;
    this.address = address;
    this.defaultRequestConfig = defaultRequestConfig;
    this.maxMessageSize = maxMessageSize;
    this.shutdownTimeout = shutdownTimeout;
    this.credentialsProvider = credentialsProvider;
  }

  public void start() {
    client.start();
  }

  @Override
  public void close() throws Exception {
    client.close(CloseMode.GRACEFUL);
    try {
      client.awaitShutdown(shutdownTimeout);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.warn(
          "Expected to await HTTP client shutdown, but was interrupted; client may not be "
              + "completely shut down",
          e);
    }
  }

  /**
   * Creates a new request configuration builder with the default values. The builder can be used to
   * customize the request configuration for a specific request.
   *
   * @return a new request configuration builder
   */
  public RequestConfig.Builder newRequestConfig() {
    return RequestConfig.copy(defaultRequestConfig);
  }

  public <HttpT, RespT> void get(
      final String path,
      final Map<String, String> queryParams,
      final RequestConfig requestConfig,
      final Class<HttpT> responseType,
      final JsonResponseTransformer<HttpT, RespT> transformer,
      final HttpZeebeFuture<RespT> result) {
    sendRequest(
        Method.GET, path, queryParams, null, requestConfig, responseType, transformer, result);
  }

  public <HttpT, RespT> void get(
      final String path,
      final RequestConfig requestConfig,
      final Class<HttpT> responseType,
      final JsonResponseTransformer<HttpT, RespT> transformer,
      final HttpZeebeFuture<RespT> result) {
    sendRequest(Method.GET, path, null, requestConfig, responseType, transformer, result);
  }

  public <RespT> void post(
      final String path,
      final String body,
      final RequestConfig requestConfig,
      final HttpZeebeFuture<RespT> result) {
    post(path, body, requestConfig, Void.class, r -> null, result);
  }

  public <HttpT, RespT> void post(
      final String path,
      final String body,
      final RequestConfig requestConfig,
      final Class<HttpT> responseType,
      final JsonResponseTransformer<HttpT, RespT> transformer,
      final HttpZeebeFuture<RespT> result) {
    sendRequest(Method.POST, path, body, requestConfig, responseType, transformer, result);
  }

  public <HttpT, RespT> void post(
      final String path,
      final Map<String, String> queryParams,
      final String body,
      final RequestConfig requestConfig,
      final Class<HttpT> responseType,
      final JsonResponseTransformer<HttpT, RespT> transformer,
      final HttpZeebeFuture<RespT> result) {
    sendRequest(
        Method.POST, path, queryParams, body, requestConfig, responseType, transformer, result);
  }

  public <HttpT, RespT> void postMultipart(
      final String path,
      final Map<String, String> queryParams,
      final MultipartEntityBuilder multipartBuilder,
      final RequestConfig requestConfig,
      final Class<HttpT> responseType,
      final JsonResponseTransformer<HttpT, RespT> transformer,
      final HttpZeebeFuture<RespT> result) {

    final HttpEntity entity = multipartBuilder.build();
    sendRequest(
        Method.POST, path, queryParams, entity, requestConfig, responseType, transformer, result);
  }

  public <HttpT, RespT> void postMultipart(
      final String path,
      final MultipartEntityBuilder multipartBuilder,
      final RequestConfig requestConfig,
      final Class<HttpT> responseType,
      final JsonResponseTransformer<HttpT, RespT> transformer,
      final HttpZeebeFuture<RespT> result) {

    final HttpEntity entity = multipartBuilder.build();
    sendRequest(Method.POST, path, entity, requestConfig, responseType, transformer, result);
  }

  public <RespT> void put(
      final String path,
      final String body,
      final RequestConfig requestConfig,
      final HttpZeebeFuture<RespT> result) {
    sendRequest(Method.PUT, path, body, requestConfig, Void.class, r -> null, result);
  }

  public <RespT> void patch(
      final String path,
      final String body,
      final RequestConfig requestConfig,
      final HttpZeebeFuture<RespT> result) {
    sendRequest(Method.PATCH, path, body, requestConfig, Void.class, r -> null, result);
  }

  public <RespT> void delete(
      final String path, final RequestConfig requestConfig, final HttpZeebeFuture<RespT> result) {
    sendRequest(Method.DELETE, path, null, requestConfig, Void.class, r -> null, result);
  }

  public <RespT> void delete(
      final String path,
      final Map<String, String> queryParams,
      final RequestConfig requestConfig,
      final HttpZeebeFuture<RespT> result) {
    sendRequest(
        Method.DELETE, path, queryParams, null, requestConfig, Void.class, r -> null, result);
  }

  private <HttpT, RespT> void sendRequest(
      final Method httpMethod,
      final String path,
      final Object body, // Can be a String (for JSON) or HttpEntity (for Multipart)
      final RequestConfig requestConfig,
      final Class<HttpT> responseType,
      final JsonResponseTransformer<HttpT, RespT> transformer,
      final HttpZeebeFuture<RespT> result) {
    sendRequest(httpMethod, path, null, body, requestConfig, responseType, transformer, result);
  }

  private <HttpT, RespT> void sendRequest(
      final Method httpMethod,
      final String path,
      final Map<String, String> queryParams,
      final Object body, // Can be a String (for JSON) or HttpEntity (for Multipart)
      final RequestConfig requestConfig,
      final Class<HttpT> responseType,
      final JsonResponseTransformer<HttpT, RespT> transformer,
      final HttpZeebeFuture<RespT> result) {

    final URI target = buildRequestURI(path);

    final Runnable retryAction =
        () -> {
          if (result.isCancelled()) {
            return;
          }
          sendRequest(
              httpMethod,
              path,
              queryParams,
              body,
              requestConfig,
              responseType,
              transformer,
              result);
        };

    final SimpleRequestBuilder requestBuilder =
        SimpleRequestBuilder.create(httpMethod).setUri(target);

    if (queryParams != null && !queryParams.isEmpty()) {
      queryParams.forEach(requestBuilder::addParameter);
    }

    if (body != null) {
      if (body instanceof String) {
        requestBuilder.setBody((String) body, ContentType.APPLICATION_JSON);
      } else if (body instanceof HttpEntity) {
        final HttpEntity entity = (HttpEntity) body;
        final byte[] entityBytes;
        try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
          entity.writeTo(byteArrayOutputStream);
          entityBytes = byteArrayOutputStream.toByteArray();
        } catch (final IOException e) {
          result.completeExceptionally(
              new ClientException("Failed to convert multipart entity to bytes", e));
          return;
        }

        final ContentType contentType = ContentType.parse(entity.getContentType());
        requestBuilder.setBody(entityBytes, contentType);
      } else {
        result.completeExceptionally(
            new ClientException("Unsupported body type: " + body.getClass().getName()));
        return;
      }
    }

    try {
      credentialsProvider.applyCredentials(requestBuilder::addHeader);
    } catch (final IOException e) {
      result.completeExceptionally(
          new ClientException("Failed to apply credentials to request", e));
      return;
    }

    final SimpleHttpRequest request = requestBuilder.build();
    request.setConfig(requestConfig);

    final AsyncEntityConsumer<ApiEntity<HttpT>> entityConsumer;
    if (responseType == InputStream.class) {
      entityConsumer = new DocumentDataConsumer<>(maxMessageSize, jsonMapper);
    } else {
      entityConsumer = new ApiEntityConsumer<>(jsonMapper, responseType, maxMessageSize);
    }

    result.transportFuture(
        client.execute(
            SimpleRequestProducer.create(request),
            new ApiResponseConsumer<>(entityConsumer),
            new ApiCallback<>(
                result, transformer, credentialsProvider::shouldRetryRequest, retryAction)));
  }

  private URI buildRequestURI(final String path) {
    final URI target;
    try {
      target = new URIBuilder(address).appendPath(path).build();
    } catch (final URISyntaxException e) {
      throw new RuntimeException(e);
    }
    return target;
  }
}
