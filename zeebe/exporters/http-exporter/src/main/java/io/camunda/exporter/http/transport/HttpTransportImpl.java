/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.http.transport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.RawValue;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import dev.failsafe.Timeout;
import dev.failsafe.TimeoutExceededException;
import io.camunda.exporter.http.subscription.Transport;
import io.camunda.zeebe.protocol.record.Record;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.apache.http.client.ClientProtocolException;
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
public class HttpTransportImpl implements Transport<String> {

  private static final String CONTENT_TYPE_JSON = "application/json";

  private final Logger log = LoggerFactory.getLogger(getClass().getPackageName());
  private final ObjectMapper objectMapper;
  private final CloseableHttpClient httpClient;
  private final String url;
  private final Timeout<Object> timeout;
  private final RetryPolicy<Object> retryPolicy;

  public HttpTransportImpl(
      final ObjectMapper objectMapper, final HttpTransportConfig httpTransportConfig) {
    this.objectMapper = objectMapper;

    url = httpTransportConfig.url();

    retryPolicy =
        RetryPolicy.builder()
            .handle(IOException.class, ClientProtocolException.class, RuntimeException.class)
            .withDelay(Duration.ofMillis(httpTransportConfig.retryDelay()))
            .withMaxRetries(httpTransportConfig.maxRetries())
            .build();

    timeout = Timeout.of(Duration.ofMillis(httpTransportConfig.timeout()));

    httpClient =
        HttpClientBuilder.create()
            .setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy())
            .build();
  }

  @Override
  public String prepare(final Record<?> record) {
    return toJson(record);
  }

  @Override
  public void send(final List<String> records) {
    log.debug("Posting records to url: {}", url);
    sendPostRequest(url, toJson(toRawValues(records)));
  }

  private void sendPostRequest(final String url, final String json) {
    final HttpPost httpPost = new HttpPost(url);
    httpPost.setEntity(createJsonEntity(json));
    post(httpPost);
  }

  private StringEntity createJsonEntity(final String json) {
    final StringEntity entity = new StringEntity(json, "UTF-8");
    entity.setContentType(CONTENT_TYPE_JSON);
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

  private void executeRequest(final HttpPost httpPost) throws Exception {
    try (final CloseableHttpResponse response = httpClient.execute(httpPost)) {
      handleResponse(response);
    }
  }

  private void abortRequest(final HttpPost httpPost) {
    log.debug("Aborting HTTP request due to timeout or failure");
    if (!httpPost.isAborted()) {
      try {
        httpPost.abort();
      } catch (final Exception e) {
        log.error("Failed to abort HTTP request", e);
      }
    }
  }

  private void handleResponse(final CloseableHttpResponse response) {
    final int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode >= 200 && statusCode < 300) {
      log.debug("Successfully posted records to: {}", response.getStatusLine().getReasonPhrase());
    } else {
      log.debug(
          "Failed posting records status:{} reason: {}",
          statusCode,
          response.getStatusLine().getReasonPhrase());
      throw new RuntimeException("Failed to post records, status: " + statusCode);
    }
  }

  private List<RawValue> toRawValues(final List<String> records) {
    return records.stream().map(RawValue::new).toList();
  }

  private String toJson(final Object object) {
    try {
      return objectMapper.writeValueAsString(object);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    try {
      httpClient.close();
    } catch (final Exception e) {
      log.error("Failed to close exporter http client", e);
    }
  }
}
