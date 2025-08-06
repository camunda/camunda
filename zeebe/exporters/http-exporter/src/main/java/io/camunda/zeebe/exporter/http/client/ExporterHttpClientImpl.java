/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.http.client;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExporterHttpClientImpl implements ExporterHttpClient {

  private static final String CONTENT_TYPE_JSON = "application/json";
  private final Logger log = LoggerFactory.getLogger(getClass().getPackageName());
  private final CloseableHttpClient httpClient;

  public ExporterHttpClientImpl(final CloseableHttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public ExporterHttpClientImpl() {
    final ConnectionKeepAliveStrategy keepAliveStrategy = new DefaultConnectionKeepAliveStrategy();
    httpClient = HttpClientBuilder.create().setKeepAliveStrategy(keepAliveStrategy).build();
  }

  @Override
  public void postRecords(final String url, final String json) {
    log.debug("Posting records to url: {}", url);
    sendPostRequest(url, json);
  }

  private void sendPostRequest(final String url, final String json) {
    final HttpPost httpPost = new HttpPost(url);
    httpPost.setEntity(createJsonEntity(json));
    executeRequest(httpPost);
  }

  private StringEntity createJsonEntity(final String json) {
    final StringEntity entity = new StringEntity(json, "UTF-8");
    entity.setContentType(CONTENT_TYPE_JSON);
    return entity;
  }

  private void executeRequest(final HttpPost httpPost) {
    try (final CloseableHttpResponse response = httpClient.execute(httpPost)) {
      final int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode >= 200 && statusCode < 300) {
        log.debug("Successfully posted records to: {}", httpPost.getURI());
      } else {
        throw new RuntimeException(
            "Unexpected response from: " + httpPost.getURI() + " status: " + statusCode);
      }
    } catch (final Throwable e) {
      log.error("Failed to post records to: {}", httpPost.getURI(), e);
      throw new RuntimeException("Failed to post records", e);
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
