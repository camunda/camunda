/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.notifier;

import java.net.http.HttpClient;

public class HttpClientWrapper {
  private static HttpClient httpClient;

  public static HttpClient newHttpClient() {
    if (httpClient == null) {
      httpClient = HttpClient.newBuilder().build();
    }

    return httpClient;
  }

  public static void setHttpClient(final HttpClient newHttpClient) {
    httpClient = newHttpClient;
  }
}
