/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.transport;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import dev.failsafe.Timeout;
import dev.failsafe.TimeoutExceededException;
import io.camunda.exporter.appint.event.Event;
import io.camunda.exporter.appint.transport.Authentication.ApiKey;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link Transport} that uses Apache HttpClient to send HTTP POST requests to a
 * specified URL with JSON payloads.
 */
public class HttpTransportImpl implements Transport<Event> {

  private static final String CONTENT_TYPE_JSON = "application/json";

  private final Logger log = LoggerFactory.getLogger(getClass().getPackageName());
  private final JsonMapper jsonMapper;
  private final CloseableHttpClient httpClient;
  private final String url;
  private final Authentication authentication;
  private final Timeout<Object> timeout;
  private final RetryPolicy<Object> retryPolicy;

  public HttpTransportImpl(
      final JsonMapper jsonMapper, final HttpTransportConfig httpTransportConfig) {
    this.jsonMapper = jsonMapper;

    url = httpTransportConfig.url();
    authentication = httpTransportConfig.authentication();

    retryPolicy =
        RetryPolicy.builder()
            .handle(TransportException.class)
            .withDelay(Duration.ofMillis(httpTransportConfig.retryDelayMs()))
            .withMaxRetries(httpTransportConfig.maxRetries())
            .build();

    timeout = Timeout.of(Duration.ofMillis(httpTransportConfig.requestTimeoutMs()));

    httpClient =
        HttpClientBuilder.create()
            .setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy())
            .build();
  }

  @Override
  public void send(final List<Event> events) {
    log.debug("Posting records to url: {}", url);
    final var json = jsonMapper.toJson(new BatchRequest(events));
    sendPostRequest(url, json);
  }

  private void sendPostRequest(final String url, final String json) {
    final HttpPost httpPost = new HttpPost(url);
    switch (authentication) {
      case final ApiKey apiKeyAuth -> httpPost.setHeader(ApiKey.HEADER_NAME, apiKeyAuth.apiKey());
      case final Authentication.None ignored ->
          log.warn("No authentication provided for HTTP transport");
    }
    httpPost.setEntity(createJsonEntity(json));
    post(httpPost);
  }

  private StringEntity createJsonEntity(final String json) {
    final StringEntity entity = new StringEntity(json, "UTF-8");
    entity.setContentType(CONTENT_TYPE_JSON);
    log.trace("Created JSON entity: {}", json);
    return entity;
  }

  private void post(final HttpPost httpPost) {
    Failsafe.with(timeout)
        .compose(retryPolicy)
        .onFailure(
            event -> {
              if (event.getException() instanceof TimeoutExceededException) {
                abortRequest(httpPost);
              }
            })
        .run(() -> executeRequest(httpPost));
  }

  private void executeRequest(final HttpPost httpPost) {
    try (final CloseableHttpResponse response = httpClient.execute(httpPost)) {
      handleResponse(response);
    } catch (final IOException e) {
      log.debug("IOException during HTTP request execution", e);
      throw new TransportException("Failed to execute HTTP request", e);
    }
  }

  private void handleResponse(final CloseableHttpResponse response) {
    final int statusCode = response.getStatusLine().getStatusCode();
    final String responseReason = response.getStatusLine().getReasonPhrase();
    if (statusCode >= 200 && statusCode < 300) {
      log.debug("Successfully posted records to: {}", responseReason);
    } else {
      log.debug("Failed posting records. Status: {} reason: {}", statusCode, responseReason);
      throw new TransportException(
          "Failed to post records. Status: " + statusCode + " reason: " + responseReason);
    }
  }

  private void abortRequest(final HttpPost httpPost) {
    log.debug("Aborting HTTP request due to requestTimeoutMs or failure");
    if (!httpPost.isAborted()) {
      try {
        httpPost.abort();
      } catch (final Exception e) {
        log.debug("Failed to abort HTTP request", e);
      }
    }
  }

  @Override
  public void close() {
    try {
      httpClient.close();
    } catch (final Exception e) {
      log.debug("Failed to close exporter http client", e);
    }
  }
}
