/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.benchmark;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class PrometheusClient {

  private final HttpClient http = HttpClient.newHttpClient();
  private final String baseUrl;

  public PrometheusClient(final String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public List<PrometheusMetric> fetchMetrics() throws IOException, InterruptedException {
    final HttpRequest req =
        HttpRequest.newBuilder().uri(URI.create(baseUrl + "/actuator/prometheus")).GET().build();

    final HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

    if (resp.statusCode() != 200) {
      throw new IOException("Bad HTTP response: " + resp.statusCode());
    }

    return PrometheusParser.parse(resp.body());
  }
}
