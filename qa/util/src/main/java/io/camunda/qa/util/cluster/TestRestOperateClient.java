/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import static java.util.Collections.emptyList;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.client.api.command.MigrationPlan;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.MigrationPlanDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.webapps.schema.entities.operation.OperationType;
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
import java.util.Map;
import org.testcontainers.containers.GenericContainer;

public class TestRestOperateClient implements AutoCloseable {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().registerModule(new Jdk8Module()).registerModule(new JavaTimeModule());

  private final URI endpoint;
  private final HttpClient httpClient;
  private String username;
  private String password;

  private final String batchOperationEndpoint;

  public TestRestOperateClient(final URI endpoint, final String username, final String password) {
    this(endpoint);
    this.username = username;
    this.password = password;
  }

  public TestRestOperateClient(final GenericContainer<?> camundaContainer) {
    this(
        URI.create("http://localhost:" + camundaContainer.getMappedPort(8080) + "/"),
        "demo",
        "demo");

    sendRequest(loginRequest());
  }

  public TestRestOperateClient(final URI endpoint) {
    this.endpoint = endpoint;
    httpClient = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

    batchOperationEndpoint = String.format("%sapi/process-instances/batch-operation", endpoint);
  }

  public static ListViewQueryDto createBasicListQueryDto() {
    return new ListViewQueryDto()
        .setRunning(true)
        .setActive(true)
        .setIncidents(true)
        .setFinished(false)
        .setCompleted(false)
        .setCanceled(false)
        .setRetriesLeft(false)
        .setIds(emptyList())
        .setExcludeIds(emptyList());
  }

  private HttpRequest loginRequest() {
    try {
      return createBuilder(
              String.format("%sapi/login?username=%s&password=%s", endpoint, username, password))
          .POST(HttpRequest.BodyPublishers.ofString("{}"))
          .build();
    } catch (final URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  public Either<Exception, HttpResponse<String>> deleteDecisionDefinition(
      final long decisionDefinitionKey) {
    try {
      final var path = String.format("%sapi/decisions/%s", endpoint, decisionDefinitionKey);
      final var request = createBuilder(path).DELETE().build();
      return sendRequest(request);
    } catch (final URISyntaxException e) {
      return Either.left(e);
    }
  }

  public Either<Exception, HttpResponse<String>> deleteProcessDefinition(
      final long processDefinitionKey) {
    try {
      final var path = String.format("%sapi/processes/%s", endpoint, processDefinitionKey);
      final var request = createBuilder(path).DELETE().build();
      return sendRequest(request);
    } catch (final URISyntaxException e) {
      return Either.left(e);
    }
  }

  public Either<Exception, HttpResponse<String>> cancelProcessInstance(
      final long processInstanceKey) {
    return createProcessInstanceOperationRequest(
        processInstanceKey, new CreateOperationRequestDto(OperationType.CANCEL_PROCESS_INSTANCE));
  }

  public Either<Exception, HttpResponse<String>> migrateProcessInstance(
      final CreateBatchOperationRequestDto requestBody) {
    try {
      final var path = String.format("%sapi/process-instances/batch-operation", endpoint);
      final var request =
          createBuilder(path)
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      OBJECT_MAPPER.writeValueAsString(requestBody)))
              .build();
      return sendRequest(request);
    } catch (final URISyntaxException e) {
      return Either.left(e);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
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
                      OBJECT_MAPPER.writeValueAsString(modificationsBody)))
              .build();
      return sendRequest(request);
    } catch (final URISyntaxException e) {
      return Either.left(e);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public Either<Exception, HttpResponse<String>> updateVariable(
      final long processInstanceKey,
      final String scopeId,
      final String variableName,
      final Object variableValue) {
    final var operationRequestBody = new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);
    operationRequestBody.setVariableScopeId(scopeId);
    operationRequestBody.setVariableName(variableName);
    operationRequestBody.setVariableValue(String.valueOf(variableValue));
    return createProcessInstanceOperationRequest(processInstanceKey, operationRequestBody);
  }

  /**
   * @return Either an Exception or the ID of the created batch operation.
   */
  public Either<Exception, String> addVariable(
      final long processInstanceKey,
      final String scopeId,
      final String variableName,
      final Object variableValue) {
    final var operationRequestBody = new CreateOperationRequestDto(OperationType.ADD_VARIABLE);
    operationRequestBody.setVariableScopeId(scopeId);
    operationRequestBody.setVariableName(variableName);
    operationRequestBody.setVariableValue(String.valueOf(variableValue));
    return mapResult(
            createProcessInstanceOperationRequest(processInstanceKey, operationRequestBody).get(),
            Map.class)
        .flatMap(result -> Either.right((String) result.get("id")));
  }

  public Either<Exception, HttpResponse<String>> resolveIncident(final long processInstanceKey) {
    // Currently, incidents in Operate only require the process instance key to be resolved.
    return createProcessInstanceOperationRequest(
        processInstanceKey, new CreateOperationRequestDto(OperationType.RESOLVE_INCIDENT));
  }

  public HttpResponse<String> sendGetRequest(final String endpointUriFormat, final String key)
      throws URISyntaxException, IOException, InterruptedException {
    final String url = endpoint + endpointUriFormat.formatted(key);
    final HttpRequest request = addAuthHeader(createBuilder(url)).GET().build();
    return httpClient.send(request, BodyHandlers.ofString());
  }

  public HttpResponse<String> sendGetRequest(final String endpointUriFormat, final long key)
      throws URISyntaxException, IOException, InterruptedException {
    return sendGetRequest(endpointUriFormat, String.valueOf(key));
  }

  public HttpResponse<String> sendV1SearchRequest(final String endpointUri, final String request)
      throws URISyntaxException, IOException, InterruptedException {
    final String url = endpoint + endpointUri + "/search";
    final HttpRequest httpRequest =
        addAuthHeader(createBuilder(url))
            .POST(HttpRequest.BodyPublishers.ofString(request))
            .header("Content-Type", "application/json")
            .build();
    return httpClient.send(httpRequest, BodyHandlers.ofString());
  }

  public HttpResponse sendInternalSearchRequest(final String endpointUri, final String request)
      throws URISyntaxException, IOException, InterruptedException {
    final HttpRequest httpRequest =
        addAuthHeader(createBuilder(endpoint + endpointUri))
            .POST(HttpRequest.BodyPublishers.ofString(request))
            .header("Content-Type", "application/json")
            .build();
    return httpClient.send(httpRequest, BodyHandlers.ofString());
  }

  public HttpResponse<String> sendDeleteRequest(final String endpointUri, final long key)
      throws URISyntaxException, IOException, InterruptedException {
    final String url = endpoint + endpointUri + "/" + key;
    final HttpRequest request = addAuthHeader(createBuilder(url)).DELETE().build();
    return httpClient.send(request, BodyHandlers.ofString());
  }

  public static String toJsonString(final Object request) throws JsonProcessingException {
    return OBJECT_MAPPER.writeValueAsString(request);
  }

  private Either<Exception, HttpResponse<String>> createProcessInstanceOperationRequest(
      final long processInstanceKey, final CreateOperationRequestDto operationRequestDto) {
    try {
      final var path =
          String.format("%sapi/process-instances/%s/operation", endpoint, processInstanceKey);
      final var request =
          createBuilder(path)
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      OBJECT_MAPPER.writeValueAsString(operationRequestDto)))
              .build();
      return sendRequest(request);
    } catch (final URISyntaxException e) {
      return Either.left(e);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private Either<Exception, HttpRequest> createProcessInstanceRequest(final long key) {
    final HttpRequest request;
    try {
      request =
          createBuilder(String.format("%sv1/process-instances/search", endpoint))
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

  private Builder createBuilder(final String uri) throws URISyntaxException {
    final HttpRequest.Builder builder =
        HttpRequest.newBuilder().uri(new URI(uri)).header("content-type", "application/json");
    return addAuthHeader(builder);
  }

  private Builder addAuthHeader(Builder builder) {
    if (username != null) {
      builder =
          builder.header(
              "Authorization",
              "Basic %s"
                  .formatted(
                      Base64.getEncoder()
                          .encodeToString("%s:%s".formatted(username, password).getBytes())));
    }
    return builder;
  }

  private Either<Exception, HttpRequest> createInternalGetProcessDefinitionByKeyRequest(
      final long key) {
    final HttpRequest request;
    try {
      request = createBuilder(String.format("%sapi/processes/%d", endpoint, key)).GET().build();
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
    return createProcessInstanceRequest(key)
        .flatMap(this::sendRequest)
        .flatMap(r -> mapResult(r, ProcessInstanceResult.class));
  }

  public Either<Exception, HttpResponse<String>> internalGetProcessDefinitionByKey(final long key) {
    return createInternalGetProcessDefinitionByKeyRequest(key).flatMap(this::sendRequest);
  }

  public URI getEndpoint() {
    return endpoint;
  }

  /**
   * @return Either an Exception or the ID of the created batch operation.
   */
  public Either<Exception, String> migrateProcessInstanceBatchOperationWith(
      final MigrationPlan migrationPlan) {
    return migrateProcessInstanceBatchOperationRequest(migrationPlan)
        .flatMap(this::sendRequest)
        .flatMap(r -> mapResult(r, Map.class))
        .flatMap(result -> Either.right((String) result.get("id")));
  }

  /**
   * @return Either an Exception or the ID of the created batch operation.
   */
  public Either<Exception, String> cancelProcessInstancesBatchOperationRequest(
      final ListViewQueryDto query) {
    final CreateBatchOperationRequestDto createBatchOperation =
        new CreateBatchOperationRequestDto();

    createBatchOperation.setOperationType(OperationType.CANCEL_PROCESS_INSTANCE);
    createBatchOperation.setQuery(query);

    return createBatchOperation(createBatchOperation)
        .flatMap(this::sendRequest)
        .flatMap(r -> mapResult(r, Map.class))
        .flatMap(result -> Either.right((String) result.get("id")));
  }

  private Either<Exception, HttpRequest> migrateProcessInstanceBatchOperationRequest(
      final MigrationPlan migrationPlan) {
    final CreateBatchOperationRequestDto createBatchOperation =
        new CreateBatchOperationRequestDto();

    createBatchOperation.setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE);
    createBatchOperation.setQuery(createBasicListQueryDto());

    final MigrationPlanDto migrationPlanDto = new MigrationPlanDto();
    migrationPlanDto.setTargetProcessDefinitionKey(
        String.valueOf(migrationPlan.getTargetProcessDefinitionKey()));
    migrationPlanDto.setMappingInstructions(
        migrationPlan.getMappingInstructions().stream()
            .map(
                mappingInstruction ->
                    new MigrationPlanDto.MappingInstruction()
                        .setSourceElementId(mappingInstruction.getSourceElementId())
                        .setTargetElementId(mappingInstruction.getTargetElementId()))
            .toList());

    createBatchOperation.setMigrationPlan(migrationPlanDto);

    return createBatchOperation(createBatchOperation);
  }

  private Either<Exception, HttpRequest> createBatchOperation(
      final CreateBatchOperationRequestDto createBatchOperationRequestDto) {
    final HttpRequest request;
    try {
      final String jsonBody = OBJECT_MAPPER.writeValueAsString(createBatchOperationRequestDto);
      request =
          createBuilder(batchOperationEndpoint)
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
              .build();
    } catch (final URISyntaxException | JsonProcessingException e) {
      return Either.left(e);
    }
    return Either.right(request);
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
