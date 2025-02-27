/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.UserIndex;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.zeebe.util.Either;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;

public class TestRestOperateClient extends AbstractTestWebappClient<TestRestOperateClient> {

  private final UserIndex userIndex;

  public TestRestOperateClient(final URI endpoint, final OperateProperties operateProperties) {
    this(
        endpoint,
        HttpClient.newHttpClient(),
        createElasticsearchClient(
            operateProperties.getElasticsearch().getUrl(),
            operateProperties.getElasticsearch().getUsername(),
            operateProperties.getElasticsearch().getPassword()),
        getUserIndex(operateProperties));
  }

  private TestRestOperateClient(
      final URI endpoint,
      final HttpClient httpClient,
      final ElasticsearchClient elasticsearchClient,
      final UserIndex userIndex) {
    super(endpoint, httpClient, elasticsearchClient);
    this.userIndex = userIndex;
  }

  private static UserIndex getUserIndex(final OperateProperties operateProperties) {
    final UserIndex userIndex;
    userIndex = new UserIndex();
    try {
      FieldUtils.writeField(userIndex, "operateProperties", operateProperties, true);
    } catch (final IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    return userIndex;
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

  private Either<Exception, HttpRequest> createProcessInstanceRequest(
      final String processDefinitionId) {
    final HttpRequest request;
    try {
      request =
          HttpRequest.newBuilder()
              .uri(new URI(String.format("%sv1/process-instances/search", endpoint)))
              .header("content-type", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      String.format(
                          "{\"filter\":{\"bpmnProcessId\":\"%s\"},\"sort\":[{\"field\":\"endDate\",\"order\":\"ASC\"}],\"size\":20}",
                          processDefinitionId)))
              .build();
    } catch (final URISyntaxException e) {
      return Either.left(e);
    }
    return Either.right(request);
  }

  private Either<Exception, HttpResponse<String>> sendProcessInstanceQuery(
      final HttpRequest request) {
    try {
      return Either.right(httpClient.send(request, BodyHandlers.ofString()));
    } catch (final IOException | InterruptedException e) {
      return Either.left(e);
    }
  }

  private Either<Exception, ProcessInstanceResult> mapProcessInstanceResult(
      final HttpResponse<String> response) {
    if (response.statusCode() != 200) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected status code 200, but got %d. [Response body: %s, Endpoint: %s].",
                  response.statusCode(), response.body(), response.uri())));
    }

    try {
      return Either.right(OBJECT_MAPPER.readValue(response.body(), ProcessInstanceResult.class));
    } catch (final JsonProcessingException e) {
      return Either.left(e);
    }
  }

  public Either<Exception, ProcessInstanceResult> getProcessInstanceWith(final long key) {
    return createProcessInstanceRequest(key)
        .flatMap(this::sendProcessInstanceQuery)
        .flatMap(this::mapProcessInstanceResult);
  }

  public Either<Exception, ProcessInstanceResult> getProcessInstanceWith(
      final String processDefinitionId) {
    return createProcessInstanceRequest(processDefinitionId)
        .flatMap(this::sendProcessInstanceQuery)
        .flatMap(this::mapProcessInstanceResult);
  }

  @Override
  protected TestRestOperateClient create(final HttpClient httpClient) {
    return new TestRestOperateClient(endpoint, httpClient, elasticsearchClient, userIndex);
  }

  public void createUser(final String username, final String password) {
    super.createUser(userIndex.getFullQualifiedName(), username, password);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ProcessInstanceResult(
      @JsonProperty("items") List<ProcessInstance> processInstances,
      @JsonProperty("total") long total) {}
}
