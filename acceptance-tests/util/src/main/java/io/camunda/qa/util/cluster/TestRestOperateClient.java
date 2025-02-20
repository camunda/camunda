/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.zeebe.util.Either;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;

public class TestRestOperateClient implements AutoCloseable {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final URI endpoint;
  private final HttpClient httpClient;

  public TestRestOperateClient(final URI endpoint, final String username, final String password) {
    this(
        endpoint,
        HttpClient.newBuilder()
            .authenticator(
                new Authenticator() {
                  @Override
                  protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password.toCharArray());
                  }
                })
            .build());
  }

  public TestRestOperateClient(final URI endpoint) {
    this(endpoint, HttpClient.newHttpClient());
  }

  private TestRestOperateClient(final URI endpoint, final HttpClient httpClient) {
    this.endpoint = endpoint;
    this.httpClient = httpClient;
  }

  private Either<Exception, HttpRequest> createProcessInstanceRequest(final long key) {
    final HttpRequest request;
    try {
      request =
          HttpRequest.newBuilder()
              .uri(new URI(String.format("%sv1/process-instances/search", endpoint)))
              .header("content-type", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      String.format(
                          "{\"filter\":{\"key\":%d},\"sort\":[{\"field\":\"endDate\",\"order\":\"ASC\"}],\"size\":20}",
                          key)))
              .build();
    } catch (final URISyntaxException e) {
      return Either.left(e);
    }
    return Either.right(request);
  }

  private Either<Exception, HttpRequest> createInternalGetProcessDefinitionByKeyRequest(
      final long key) {
    final HttpRequest request;
    try {
      request =
          HttpRequest.newBuilder()
              .uri(new URI(String.format("%sapi/processes/%d", endpoint, key)))
              .GET()
              .build();
    } catch (final URISyntaxException e) {
      return Either.left(e);
    }
    return Either.right(request);
  }

  private Either<Exception, HttpResponse<String>> sendRequest(final HttpRequest request) {
    try {
      return Either.right(httpClient.send(request, BodyHandlers.ofString()));
    } catch (final IOException | InterruptedException e) {
      return Either.left(e);
    }
  }

  private <T> Either<Exception, T> mapResult(
      final HttpResponse<String> response, final Class<T> tClass) {
    if (response.statusCode() != 200) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected status code 200, but got %d. [Response body: %s, Endpoint: %s].",
                  response.statusCode(), response.body(), response.uri())));
    }

    try {
      return Either.right(OBJECT_MAPPER.readValue(response.body(), tClass));
    } catch (final JsonProcessingException e) {
      return Either.left(e);
    }
  }

  public Either<Exception, ProcessInstanceResult> getProcessInstanceWith(final long key) {
    return createProcessInstanceRequest(key)
        .flatMap(this::sendRequest)
        .flatMap(r -> mapResult(r, ProcessInstanceResult.class));
  }

  public Either<Exception, HttpResponse<String>> internalGetProcessDefinitionByKey(final long key) {
    return createInternalGetProcessDefinitionByKeyRequest(key).flatMap(this::sendRequest);
  }

  @Override
  public void close() {
    httpClient.close();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ProcessInstanceResult(
      @JsonProperty("items") List<ProcessInstance> processInstances,
      @JsonProperty("total") long total) {}
}
