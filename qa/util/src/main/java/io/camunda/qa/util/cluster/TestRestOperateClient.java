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
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.client.CredentialsProvider;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.zeebe.util.Either;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

public class TestRestOperateClient implements AutoCloseable {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().registerModule(new Jdk8Module()).registerModule(new JavaTimeModule());

  private final URI endpoint;
  private final HttpClient httpClient;
  private Consumer<Builder> authenticationApplier;

  public TestRestOperateClient(final URI endpoint, final String username, final String password) {
    this(endpoint);
    this.authenticationApplier = addBasicAuthHeader(username, password);
  }

  public TestRestOperateClient(final URI endpoint, CredentialsProvider credentialsProvider) {
    this(endpoint);
    this.authenticationApplier = applyCredentialsProvider(credentialsProvider);
  }

  public TestRestOperateClient(final URI endpoint) {
    this.endpoint = endpoint;
    httpClient = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
  }

  public Either<Exception, HttpResponse<String>> deleteDecisionDefinition(
      final long decisionDefinitionKey) {
    try {
      final var path = String.format("%sapi/decisions/%s", endpoint, decisionDefinitionKey);
      final var request = createBuilder(path).DELETE();
      return sendRequestCatchingException(request);
    } catch (final URISyntaxException e) {
      return Either.left(e);
    }
  }

  public Either<Exception, HttpResponse<String>> deleteProcessDefinition(
      final long processDefinitionKey) {
    try {
      final var path = String.format("%sapi/processes/%s", endpoint, processDefinitionKey);
      final var builder = createBuilder(path).DELETE();
      return sendRequestCatchingException(builder);
    } catch (final URISyntaxException e) {
      return Either.left(e);
    }
  }

  public Either<Exception, HttpResponse<String>> modifyProcessInstance(
      final long processInstanceKey, final ModifyProcessInstanceRequestDto modificationsBody) {
    try {
      final var path =
          String.format("%sapi/process-instances/%s/modify", endpoint, processInstanceKey);
      final var request =
          createBuilder(path)
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      OBJECT_MAPPER.writeValueAsString(modificationsBody)));
      return sendRequestCatchingException(request);
    } catch (final URISyntaxException e) {
      return Either.left(e);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public HttpResponse<String> sendGetRequest(final String endpointUriFormat, final String key)
      throws Exception {
    final String url = endpoint + endpointUriFormat.formatted(key);
    return sendRequest(createBuilder(url).GET());
  }

  public HttpResponse<String> sendGetRequest(final String endpointUriFormat, final long key)
      throws Exception {
    return sendGetRequest(endpointUriFormat, String.valueOf(key));
  }

  public HttpResponse<String> sendV1SearchRequest(final String endpointUri, final String request)
      throws Exception {
    final String url = endpoint + endpointUri + "/search";

    final Builder requestBuilder =
        createBuilder(url)
            .POST(HttpRequest.BodyPublishers.ofString(request))
            .header("Content-Type", "application/json");

    return sendRequest(requestBuilder);
  }

  public HttpResponse sendInternalSearchRequest(final String endpointUri, final String request)
      throws Exception {
    final Builder requestBuilder =
        createBuilder(endpoint + endpointUri)
            .POST(HttpRequest.BodyPublishers.ofString(request))
            .header("Content-Type", "application/json");

    return sendRequest(requestBuilder);
  }

  public HttpResponse<String> sendDeleteRequest(final String endpointUri, final long key)
      throws Exception {
    final String url = endpoint + endpointUri + "/" + key;
    return sendRequest(createBuilder(url).DELETE());
  }

  public static String toJsonString(final Object request) throws JsonProcessingException {
    return OBJECT_MAPPER.writeValueAsString(request);
  }

  private Builder createProcessInstanceRequest(final long key) throws URISyntaxException {
    return createBuilder(String.format("%sv1/process-instances/search", endpoint))
        .POST(
            HttpRequest.BodyPublishers.ofString(
                String.format(
                    "{\"filter\":{\"key\":%d},\"sort\":[{\"field\":\"endDate\",\"order\":\"ASC\"}],\"size\":20}",
                    key)));
  }

  private Builder createBuilder(final String uri) throws URISyntaxException {
    return HttpRequest.newBuilder().uri(new URI(uri)).header("content-type", "application/json");
  }

  private Consumer<Builder> addBasicAuthHeader(String username, String password) {
    return builder -> {
      if (username != null) {
        builder =
            builder.header(
                "Authorization",
                "Basic %s"
                    .formatted(
                        Base64.getEncoder()
                            .encodeToString("%s:%s".formatted(username, password).getBytes())));
      }
    };
  }

  private HttpResponse<String> sendRequest(Builder requestBuilder) throws Exception {
    if (authenticationApplier != null) {
      authenticationApplier.accept(requestBuilder);
    }

    final HttpRequest request = requestBuilder.build();

    return httpClient.send(request, BodyHandlers.ofString());
  }

  private Either<Exception, HttpResponse<String>> sendRequestCatchingException(
      Builder requestBuilder) {

    try {
      return Either.right(sendRequest(requestBuilder));
    } catch (Exception e) {
      return Either.left(e);
    }
  }

  public <T> Either<Exception, T> mapResult(
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
    final Builder processInstanceRequest;
    try {
      processInstanceRequest = createProcessInstanceRequest(key);
    } catch (URISyntaxException e) {
      return Either.left(e);
    }

    return sendRequestCatchingException(processInstanceRequest)
        .flatMap(r -> mapResult(r, ProcessInstanceResult.class));
  }

  public URI getEndpoint() {
    return endpoint;
  }

  @Override
  public void close() {
    httpClient.close();
  }

  protected static Consumer<Builder> applyCredentialsProvider(
      CredentialsProvider credentialsProvider) {
    return builder -> {
      try {
        credentialsProvider.applyCredentials(builder::header);
      } catch (IOException e) {
        throw new RuntimeException("Could not apply credentials", e);
      }
    };
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ProcessInstanceResult(
      @JsonProperty("items") List<ProcessInstance> processInstances,
      @JsonProperty("total") long total) {}
}
