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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.impl.backpressure.BackpressureCall;
import io.camunda.client.impl.backpressure.BackpressureController;
import io.camunda.client.impl.backpressure.BackpressureScope;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin abstraction layer on top of Apache's HTTP client to wire up the expected Orchestration
 * Cluster API conventions, e.g. errors are always {@link
 * io.camunda.client.protocol.rest.ProblemDetail}, content type is always JSON, etc.
 */
public final class HttpClient implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpClient.class);

  private static final int MAX_RETRY_ATTEMPTS = 2;

  private final CloseableHttpAsyncClient client;
  private final ModalCloseable connectionManager;
  private final ObjectMapper jsonMapper;
  private final URI address;
  private final URI clusterApiAddress;
  private final RequestConfig defaultRequestConfig;
  private final int maxMessageSize;
  private final TimeValue shutdownTimeout;
  private final CredentialsProvider credentialsProvider;
  private final BackpressureController backpressure;

  public HttpClient(
      final CloseableHttpAsyncClient client,
      final ModalCloseable connectionManager,
      final ObjectMapper jsonMapper,
      final URI address,
      final URI clusterApiAddress,
      final RequestConfig defaultRequestConfig,
      final int maxMessageSize,
      final TimeValue shutdownTimeout,
      final CredentialsProvider credentialsProvider) {
    this(
        client,
        connectionManager,
        jsonMapper,
        address,
        clusterApiAddress,
        defaultRequestConfig,
        maxMessageSize,
        shutdownTimeout,
        credentialsProvider,
        BackpressureController.disabled());
  }

  public HttpClient(
      final CloseableHttpAsyncClient client,
      final ModalCloseable connectionManager,
      final ObjectMapper jsonMapper,
      final URI address,
      final URI clusterApiAddress,
      final RequestConfig defaultRequestConfig,
      final int maxMessageSize,
      final TimeValue shutdownTimeout,
      final CredentialsProvider credentialsProvider,
      final BackpressureController backpressure) {
    this.backpressure = backpressure;
    this.client = client;
    this.connectionManager = connectionManager;
    this.jsonMapper = jsonMapper;
    this.address = address;
    this.clusterApiAddress = clusterApiAddress;
    this.defaultRequestConfig = defaultRequestConfig;
    this.maxMessageSize = maxMessageSize;
    this.shutdownTimeout = shutdownTimeout;
    this.credentialsProvider = credentialsProvider;
  }

  public void start() {
    client.start();
  }

  /**
   * @return the backpressure handling that this client shares with the gRPC calls of the same
   *     {@link io.camunda.client.CamundaClient}
   */
  public BackpressureController backpressure() {
    return backpressure;
  }

  @Override
  public void close() throws Exception {
    backpressure.close();
    // Force-close the connection pool immediately to terminate all connections — both active
    // in-flight requests and idle keep-alive connections. Without this, close(GRACEFUL) waits
    // for idle connections to drain, causing several seconds of unnecessary delay and a
    // ClosedSelectorException race in the NIO reactor shutdown sequence.
    connectionManager.close(CloseMode.IMMEDIATE);
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
      final RequestConfig requestConfig,
      final Class<HttpT> responseType,
      final JsonResponseTransformer<HttpT, RespT> transformer,
      final HttpCamundaFuture<RespT> result) {
    get(path, Collections.emptyMap(), requestConfig, responseType, transformer, result);
  }

  public <HttpT, RespT> void get(
      final String path,
      final Map<String, String> queryParams,
      final RequestConfig requestConfig,
      final Class<HttpT> responseType,
      final JsonResponseTransformer<HttpT, RespT> transformer,
      final HttpCamundaFuture<RespT> result) {
    sendRequest(
        Method.GET, path, queryParams, null, requestConfig, responseType, transformer, result);
  }

  public <RespT> void get(
      final String path,
      final RequestConfig requestConfig,
      final Predicate<Integer> successPredicate,
      final JsonResponseAndStatusCodeTransformer<Void, RespT> transformer,
      final HttpCamundaFuture<RespT> result) {
    get(path, Collections.emptyMap(), requestConfig, successPredicate, transformer, result);
  }

  public <RespT> void get(
      final String path,
      final Map<String, String> queryParams,
      final RequestConfig requestConfig,
      final Predicate<Integer> successPredicate,
      final JsonResponseAndStatusCodeTransformer<Void, RespT> transformer,
      final HttpCamundaFuture<RespT> result) {
    sendRequest(
        Method.GET,
        path,
        queryParams,
        null,
        requestConfig,
        MAX_RETRY_ATTEMPTS,
        successPredicate,
        transformer,
        result,
        null);
  }

  /**
   * Sends a GET to the cluster-scoped API ({@code /cluster/v2}), which reports on the cluster as a
   * whole and is therefore not resolved against the physical-tenant-scoped base path.
   */
  public <HttpT, RespT> void getClusterApi(
      final String path,
      final RequestConfig requestConfig,
      final Class<HttpT> responseType,
      final Predicate<Integer> successPredicate,
      final JsonResponseAndStatusCodeTransformer<HttpT, RespT> transformer,
      final HttpCamundaFuture<RespT> result) {
    sendRequest(
        Method.GET,
        clusterApiAddress,
        path,
        Collections.emptyMap(),
        null,
        requestConfig,
        MAX_RETRY_ATTEMPTS,
        responseType,
        successPredicate,
        transformer,
        result,
        null,
        false);
  }

  public <RespT> void post(
      final String path,
      final String body,
      final RequestConfig requestConfig,
      final JsonResponseTransformer<Void, RespT> transformer,
      final HttpCamundaFuture<RespT> result) {
    post(path, body, requestConfig, Void.class, transformer, result);
  }

  public <HttpT, RespT> void post(
      final String path,
      final String body,
      final RequestConfig requestConfig,
      final Class<HttpT> responseType,
      final JsonResponseTransformer<HttpT, RespT> transformer,
      final HttpCamundaFuture<RespT> result) {
    post(path, Collections.emptyMap(), body, requestConfig, responseType, transformer, result);
  }

  public <HttpT, RespT> void post(
      final String path,
      final Map<String, String> queryParams,
      final String body,
      final RequestConfig requestConfig,
      final Class<HttpT> responseType,
      final JsonResponseTransformer<HttpT, RespT> transformer,
      final HttpCamundaFuture<RespT> result) {

    sendRequest(
        Method.POST, path, queryParams, body, requestConfig, responseType, transformer, result);
  }

  /**
   * Same as {@link #post(String, String, RequestConfig, Class, JsonResponseTransformer,
   * HttpCamundaFuture)}, except that the response body is never echoed into an error message. Use
   * it for endpoints that answer with secret material: a response the client cannot make sense of —
   * an unexpected content type, or a response body under an error status — is otherwise reported by
   * dumping the body, which would leak the secrets it carries into the caller's logs.
   */
  public <HttpT, RespT> void postWithSensitiveResponse(
      final String path,
      final String body,
      final RequestConfig requestConfig,
      final Class<HttpT> responseType,
      final JsonResponseTransformer<HttpT, RespT> transformer,
      final HttpCamundaFuture<RespT> result) {
    sendRequest(
        Method.POST,
        path,
        Collections.emptyMap(),
        body,
        requestConfig,
        MAX_RETRY_ATTEMPTS,
        responseType,
        transformer,
        result,
        null,
        true);
  }

  public <HttpT, RespT> void postMultipart(
      final String path,
      final MultipartEntityBuilder multipartBuilder,
      final RequestConfig requestConfig,
      final Class<HttpT> responseType,
      final JsonResponseTransformer<HttpT, RespT> transformer,
      final HttpCamundaFuture<RespT> result) {
    postMultipart(
        path,
        Collections.emptyMap(),
        multipartBuilder,
        requestConfig,
        responseType,
        transformer,
        result);
  }

  public <HttpT, RespT> void postMultipart(
      final String path,
      final Map<String, String> queryParams,
      final MultipartEntityBuilder multipartBuilder,
      final RequestConfig requestConfig,
      final Class<HttpT> responseType,
      final JsonResponseTransformer<HttpT, RespT> transformer,
      final HttpCamundaFuture<RespT> result) {

    final HttpEntity entity = multipartBuilder.build();
    sendRequest(
        Method.POST, path, queryParams, entity, requestConfig, responseType, transformer, result);
  }

  public <RespT> void put(
      final String path,
      final String body,
      final RequestConfig requestConfig,
      final JsonResponseTransformer<Void, RespT> transformer,
      final HttpCamundaFuture<RespT> result) {
    sendRequest(
        Method.PUT,
        path,
        Collections.emptyMap(),
        body,
        requestConfig,
        Void.class,
        transformer,
        result);
  }

  public <HttpT, RespT> void put(
      final String path,
      final String body,
      final RequestConfig requestConfig,
      final Class<HttpT> responseType,
      final JsonResponseTransformer<HttpT, RespT> transformer,
      final HttpCamundaFuture<RespT> result) {
    sendRequest(
        Method.PUT,
        path,
        Collections.emptyMap(),
        body,
        requestConfig,
        responseType,
        transformer,
        result);
  }

  public <RespT> void patch(
      final String path,
      final String body,
      final RequestConfig requestConfig,
      final JsonResponseTransformer<Void, RespT> transformer,
      final HttpCamundaFuture<RespT> result) {
    patch(path, body, requestConfig, Void.class, transformer, result);
  }

  public <HttpT, RespT> void patch(
      final String path,
      final String body,
      final RequestConfig requestConfig,
      final Class<HttpT> responseType,
      final JsonResponseTransformer<HttpT, RespT> transformer,
      final HttpCamundaFuture<RespT> result) {
    sendRequest(
        Method.PATCH,
        path,
        Collections.emptyMap(),
        body,
        requestConfig,
        responseType,
        transformer,
        result);
  }

  public <RespT> void delete(
      final String path,
      final RequestConfig requestConfig,
      final JsonResponseTransformer<Void, RespT> transformer,
      final HttpCamundaFuture<RespT> result) {
    delete(path, Collections.emptyMap(), requestConfig, transformer, result);
  }

  public <RespT> void delete(
      final String path,
      final Map<String, String> queryParams,
      final RequestConfig requestConfig,
      final JsonResponseTransformer<Void, RespT> transformer,
      final HttpCamundaFuture<RespT> result) {
    sendRequest(
        Method.DELETE, path, queryParams, null, requestConfig, Void.class, transformer, result);
  }

  private <HttpT, RespT> void sendRequest(
      final Method httpMethod,
      final String path,
      final Map<String, String> queryParams,
      final Object body, // Can be a String (for JSON) or HttpEntity (for Multipart)
      final RequestConfig requestConfig,
      final Class<HttpT> responseType,
      final JsonResponseTransformer<HttpT, RespT> transformer,
      final HttpCamundaFuture<RespT> result) {
    sendRequest(
        httpMethod,
        path,
        queryParams,
        body,
        requestConfig,
        MAX_RETRY_ATTEMPTS,
        responseType,
        transformer,
        result,
        null,
        false);
  }

  private <HttpT, RespT> void sendRequest(
      final Method httpMethod,
      final String path,
      final Map<String, String> queryParams,
      final Object body, // Can be a String (for JSON) or HttpEntity (for Multipart)
      final RequestConfig requestConfig,
      final int maxRetries,
      final Class<HttpT> responseType,
      final JsonResponseTransformer<HttpT, RespT> transformer,
      final HttpCamundaFuture<RespT> result,
      final ApiCallback<HttpT, RespT> callback,
      final boolean sensitiveResponseBody) {
    sendRequest(
        httpMethod,
        path,
        queryParams,
        body,
        requestConfig,
        maxRetries,
        responseType,
        null,
        (response, statusCode) -> transformer.transform(response),
        result,
        callback,
        sensitiveResponseBody);
  }

  private <RespT> void sendRequest(
      final Method httpMethod,
      final String path,
      final Map<String, String> queryParams,
      final Object body, // Can be a String (for JSON) or HttpEntity (for Multipart)
      final RequestConfig requestConfig,
      final int maxRetries,
      final Predicate<Integer> successPredicate,
      final JsonResponseAndStatusCodeTransformer<Void, RespT> transformer,
      final HttpCamundaFuture<RespT> result,
      final ApiCallback<Void, RespT> callback) {
    sendRequest(
        httpMethod,
        path,
        queryParams,
        body,
        requestConfig,
        maxRetries,
        Void.class,
        successPredicate,
        transformer,
        result,
        callback,
        false);
  }

  private <HttpT, RespT> void sendRequest(
      final Method httpMethod,
      final String path,
      final Map<String, String> queryParams,
      final Object body, // Can be a String (for JSON) or HttpEntity (for Multipart)
      final RequestConfig requestConfig,
      final int maxRetries,
      final Class<HttpT> responseType,
      final Predicate<Integer> successPredicate,
      final JsonResponseAndStatusCodeTransformer<HttpT, RespT> transformer,
      final HttpCamundaFuture<RespT> result,
      final ApiCallback<HttpT, RespT> callback,
      final boolean sensitiveResponseBody) {
    sendRequest(
        httpMethod,
        address,
        path,
        queryParams,
        body,
        requestConfig,
        maxRetries,
        responseType,
        successPredicate,
        transformer,
        result,
        callback,
        sensitiveResponseBody);
  }

  private <HttpT, RespT> void sendRequest(
      final Method httpMethod,
      final URI baseAddress,
      final String path,
      final Map<String, String> queryParams,
      final Object body, // Can be a String (for JSON) or HttpEntity (for Multipart)
      final RequestConfig requestConfig,
      final int maxRetries,
      final Class<HttpT> responseType,
      final Predicate<Integer> successPredicate,
      final JsonResponseAndStatusCodeTransformer<HttpT, RespT> transformer,
      final HttpCamundaFuture<RespT> result,
      final ApiCallback<HttpT, RespT> callback,
      final boolean sensitiveResponseBody) {
    // A multipart entity may stream its content from a source that can be read only once, and that
    // the caller closes as soon as the request is sent. The request may be sent after that, if it
    // waits for its turn or is retried, so the content is read once, now.
    final Object requestBody =
        body instanceof HttpEntity ? renderEntity((HttpEntity) body, result) : body;
    if (requestBody == null && body != null) {
      return;
    }

    final AtomicReference<ApiCallback<HttpT, RespT>> apiCallback = new AtomicReference<>(callback);
    // Create retry action to re-execute the same request
    final Runnable retryAction =
        () -> {
          if (result.isCancelled()) {
            return;
          }
          sendRequest(
              httpMethod,
              baseAddress,
              path,
              queryParams,
              requestBody,
              requestConfig,
              maxRetries,
              responseType,
              successPredicate,
              transformer,
              result,
              apiCallback.get(),
              sensitiveResponseBody);
        };

    if (callback == null) {
      // the first attempt of the request: the retries run through here with their callback
      final BackpressureCall backpressureCall =
          newBackpressureCall(httpMethod, path, requestConfig, result);
      apiCallback.set(
          new ApiCallback<>(
              result,
              transformer,
              successPredicate,
              credentialsProvider::shouldRetryRequest,
              retryAction,
              maxRetries,
              sensitiveResponseBody,
              backpressureCall));

      if (backpressureCall != null) {
        // wait for the turn of the request without blocking a thread
        backpressureCall
            .acquire()
            .whenComplete(
                (ignored, error) -> {
                  if (error != null) {
                    result.completeExceptionally(error);
                  } else if (!result.isDone()) {
                    try {
                      executeAttempt(
                          httpMethod,
                          baseAddress,
                          path,
                          queryParams,
                          requestBody,
                          requestConfig,
                          responseType,
                          result,
                          apiCallback.get());
                    } catch (final RuntimeException e) {
                      // nobody would see an exception thrown in this callback, and the request
                      // would hang
                      result.completeExceptionally(e);
                    }
                  }
                });
        return;
      }
    }

    executeAttempt(
        httpMethod,
        baseAddress,
        path,
        queryParams,
        requestBody,
        requestConfig,
        responseType,
        result,
        apiCallback.get());
  }

  private <HttpT, RespT> void executeAttempt(
      final Method httpMethod,
      final URI baseAddress,
      final String path,
      final Map<String, String> queryParams,
      final Object body,
      final RequestConfig requestConfig,
      final Class<HttpT> responseType,
      final HttpCamundaFuture<RespT> result,
      final ApiCallback<HttpT, RespT> callback) {
    final SimpleHttpRequest request =
        buildRequest(httpMethod, queryParams, body, result, buildRequestURI(baseAddress, path));
    if (request == null) {
      return;
    }
    request.setConfig(attemptConfig(requestConfig, callback.backpressure()));

    final HttpClientContext context = HttpClientContext.create();
    final AsyncEntityConsumer<ApiEntity<HttpT>> entityConsumer =
        createEntityConsumer(responseType, context);

    result.transportFuture(
        client.execute(
            SimpleRequestProducer.create(request),
            new ApiResponseConsumer<>(entityConsumer),
            context,
            callback));
  }

  /**
   * Starts the backpressure handling for a request, and ends it once the request is done.
   *
   * <p>Requests that only read, and are therefore not what the cluster signals backpressure for,
   * never wait for the concurrency limit.
   *
   * @return the handling, or {@code null} if the request is not subject to it
   */
  private BackpressureCall newBackpressureCall(
      final Method httpMethod,
      final String path,
      final RequestConfig requestConfig,
      final HttpCamundaFuture<?> result) {
    if (!backpressure.isEnabled()) {
      return null;
    }

    BackpressureScope scope = result.backpressureScope();
    if (scope == BackpressureScope.MANAGED && (httpMethod == Method.GET || isSearch(path))) {
      scope = BackpressureScope.UNGATED;
    }

    final Timeout responseTimeout = requestConfig.getResponseTimeout();
    final long timeoutMillis = responseTimeout == null ? 0 : responseTimeout.toMilliseconds();
    final BackpressureCall call = backpressure.newCall(scope, timeoutMillis);
    if (call != null) {
      result.whenComplete((response, error) -> call.release());
    }
    return call;
  }

  private static boolean isSearch(final String path) {
    return path.endsWith("/search");
  }

  /**
   * Makes sure that the response timeout of an attempt does not reach beyond the time that is left
   * of the timeout of the request, which also pays for waiting and for the earlier attempts.
   */
  private static RequestConfig attemptConfig(
      final RequestConfig requestConfig, final BackpressureCall backpressureCall) {
    if (backpressureCall == null) {
      return requestConfig;
    }
    final long remaining = backpressureCall.remainingMillis();
    final Timeout responseTimeout = requestConfig.getResponseTimeout();
    if (remaining == Long.MAX_VALUE
        || responseTimeout == null
        || responseTimeout.toMilliseconds() <= 0
        || remaining >= responseTimeout.toMilliseconds()) {
      return requestConfig;
    }
    return RequestConfig.copy(requestConfig)
        .setResponseTimeout(Timeout.ofMilliseconds(Math.max(1, remaining)))
        .build();
  }

  private <HttpT> AsyncEntityConsumer<ApiEntity<HttpT>> createEntityConsumer(
      final Class<HttpT> responseType, final HttpClientContext context) {
    if (responseType == InputStream.class) {
      return new DocumentDataConsumer<>(
          maxMessageSize, jsonMapper, () -> ExchangeAbortChainHandler.abort(context));
    } else {
      return new ApiEntityConsumer<>(jsonMapper, responseType, maxMessageSize);
    }
  }

  private <RespT> SimpleHttpRequest buildRequest(
      final Method httpMethod,
      final Map<String, String> queryParams,
      final Object body,
      final HttpCamundaFuture<RespT> result,
      final URI target) {

    final SimpleRequestBuilder requestBuilder =
        SimpleRequestBuilder.create(httpMethod).setUri(target);

    addQueryParameters(requestBuilder, queryParams);

    if (!setRequestBody(requestBuilder, body, result)) {
      return null;
    }

    return requestBuilder.build();
  }

  private void addQueryParameters(
      final SimpleRequestBuilder requestBuilder, final Map<String, String> queryParams) {

    if (queryParams != null && !queryParams.isEmpty()) {
      queryParams.forEach(requestBuilder::addParameter);
    }
  }

  private <RespT> boolean setRequestBody(
      final SimpleRequestBuilder requestBuilder,
      final Object body,
      final HttpCamundaFuture<RespT> result) {

    if (body == null) {
      return true;
    }

    if (body instanceof String) {
      requestBuilder.setBody((String) body, ContentType.APPLICATION_JSON);
      return true;
    }

    if (body instanceof RenderedEntity) {
      final RenderedEntity entity = (RenderedEntity) body;
      requestBuilder.setBody(entity.content, entity.contentType);
      return true;
    }

    result.completeExceptionally(
        new ClientException("Unsupported body type: " + body.getClass().getName()));
    return false;
  }

  /**
   * Reads the content of the entity.
   *
   * @return the content, or {@code null} if it could not be read, in which case the result was
   *     completed exceptionally
   */
  private <RespT> RenderedEntity renderEntity(
      final HttpEntity entity, final HttpCamundaFuture<RespT> result) {
    final byte[] content;
    try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
      entity.writeTo(byteArrayOutputStream);
      content = byteArrayOutputStream.toByteArray();
    } catch (final IOException e) {
      result.completeExceptionally(
          new ClientException("Failed to convert multipart entity to bytes", e));
      return null;
    }

    return new RenderedEntity(content, ContentType.parse(entity.getContentType()));
  }

  /** The content of an {@link HttpEntity}, which can be sent more than once. */
  private static final class RenderedEntity {
    private final byte[] content;
    private final ContentType contentType;

    private RenderedEntity(final byte[] content, final ContentType contentType) {
      this.content = content;
      this.contentType = contentType;
    }
  }

  private URI buildRequestURI(final URI baseAddress, final String path) {
    final URI target;
    try {
      target = new URIBuilder(baseAddress).appendPath(path).build();
    } catch (final URISyntaxException e) {
      throw new RuntimeException(e);
    }
    return target;
  }
}
