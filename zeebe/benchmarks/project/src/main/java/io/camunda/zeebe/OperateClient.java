/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.util.CloseableSilently;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.agrona.CloseHelper;

final class OperateClient implements CloseableSilently {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final HttpClient client;

  private final URI baseUri;

  OperateClient(final URI baseUri, final Executor executor) {
    this.baseUri = baseUri;

    client =
        HttpClient.newBuilder()
            .authenticator(
                new Authenticator() {
                  @Override
                  protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication("demo", "demo".toCharArray());
                  }
                })
            .executor(executor)
            .connectTimeout(Duration.ofSeconds(1))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(Version.HTTP_1_1)
            .build();
  }

  @Override
  public void close() {
    CloseHelper.quietClose(client);
  }

  public CompletableFuture<ProcessInstance> getProcessInstance(final long processInstanceKey) {
    final var result = new CompletableFuture<ProcessInstance>();
    final var requestUri = baseUri.resolve("/v1/process-instances/" + processInstanceKey);
    final var request =
        HttpRequest.newBuilder().GET().uri(requestUri).timeout(Duration.ofSeconds(1)).build();
    client
        .sendAsync(request, BodyHandlers.ofByteArray())
        .whenCompleteAsync(
            (response, error) -> {
              if (error != null) {
                result.completeExceptionally(
                    new RuntimeException(
                        "Failed to poll [%s] for PI visibility".formatted(requestUri), error));
                return;
              }

              mapProcessInstanceResponse(requestUri, response, result);
            });

    return result;
  }

  private void mapProcessInstanceResponse(
      final URI requestUri,
      HttpResponse<byte[]> response,
      CompletableFuture<ProcessInstance> result) {
    if (response.statusCode() < 200 || response.statusCode() > 299) {
      result.completeExceptionally(new HttpResponseException(requestUri, response.statusCode()));
      return;
    }

    try {
      result.complete(MAPPER.readValue(response.body(), ProcessInstance.class));
    } catch (final IOException e) {
      result.completeExceptionally(new HttpResponseException(requestUri, response.statusCode(), e));
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ProcessInstance(long key) {}

  static final class HttpResponseException extends RuntimeException {
    private final int code;

    public HttpResponseException(final URI requestUri, final int code) {
      super("Failed to execute request " + requestUri + ", received non-success code " + code);
      this.code = code;
    }

    public HttpResponseException(final URI requestUri, final int code, final Throwable cause) {
      super(
          "Failed to execute request %s, received non-success code %d".formatted(requestUri, code),
          cause);
      this.code = code;
    }

    int code() {
      return code;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
      return this;
    }
  }
}
