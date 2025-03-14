/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Interface for making API calls to the Camunda Platform REST API. This is to be used against the
 * 8.7 version of the Camunda Platform REST API.
 */
public abstract class ApiCallable {
  protected String url;
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private String cookie;
  private String csrfToken;

  public <T> HttpResponse<T> request(
      final Consumer<Builder> requestBuilder, final HttpResponse.BodyHandler<T> handler)
      throws IOException, InterruptedException {
    final HttpRequest.Builder builder = HttpRequest.newBuilder();
    builder.headers(requestHeaders());
    requestBuilder.accept(builder);
    final HttpRequest request = builder.build();
    return httpClient.send(request, handler);
  }

  public void login() throws IOException, InterruptedException {
    final HttpRequest login =
        HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.noBody())
            .uri(URI.create(url + "/api/login?username=demo&password=demo"))
            .build();
    final var loginRes = httpClient.send(login, HttpResponse.BodyHandlers.ofString());

    cookie =
        loginRes.headers().allValues("Set-Cookie").stream()
            .map(k -> k.split(";")[0])
            .collect(Collectors.joining("; "));

    csrfToken =
        loginRes
            .headers()
            .firstValue("X-CSRF-TOKEN")
            .orElse(
                loginRes.headers().allValues("Set-Cookie").stream()
                    .filter(c -> c.contains("X-CSRF-TOKEN"))
                    .filter(c -> !c.split("=")[1].isBlank())
                    .map(c -> c.split("=")[0] + "=" + c.split("=")[1])
                    .findFirst()
                    .get());
  }

  public String[] requestHeaders() {
    return new String[] {
      "Cookie",
      cookie,
      "X-Csrf-Token",
      csrfToken,
      "Content-Type",
      "application/json",
      "Accept",
      "application/json"
    };
  }
}
