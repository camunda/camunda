/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.entities.TestExporterEntity;
import io.camunda.exporter.errorhandling.Error;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.utils.ElasticsearchScriptBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.BiConsumer;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StreamingElasticsearchBatchRequestTest {

  private static final String ID = "id";
  private static final String INDEX = "index";

  private RestClient restClient;
  private ObjectMapper objectMapper;
  private JacksonJsonpMapper jsonpMapper;
  private ElasticsearchScriptBuilder scriptBuilder;
  private BulkRequest.Builder bulkRequestBuilder;
  private StreamingElasticsearchBatchRequest batchRequest;

  @BeforeEach
  void setUp() throws IOException {
    restClient = mock(RestClient.class);
    objectMapper = new ObjectMapper();
    jsonpMapper = new JacksonJsonpMapper(objectMapper);
    scriptBuilder = mock(ElasticsearchScriptBuilder.class);
    bulkRequestBuilder = new BulkRequest.Builder();
    batchRequest =
        new StreamingElasticsearchBatchRequest(
            restClient, jsonpMapper, objectMapper, bulkRequestBuilder, scriptBuilder);

    final var mockResponse = mock(org.elasticsearch.client.Response.class);
    final var mockEntity = mock(org.apache.http.HttpEntity.class);
    when(mockResponse.getEntity()).thenReturn(mockEntity);
    when(mockEntity.getContent())
        .thenReturn(
            new ByteArrayInputStream(
                """
                {"errors":false,"items":[]}"""
                    .getBytes(StandardCharsets.UTF_8)));
    when(restClient.performRequest(any(Request.class))).thenReturn(mockResponse);
  }

  @Test
  void shouldStreamIndexOperationAsNdJson() throws IOException, PersistenceException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);

    // when
    batchRequest.add(INDEX, entity);
    batchRequest.execute();

    // then
    final String ndJson = captureNdJsonBody();
    final String[] lines = ndJson.split("\n");
    assertThat(lines).hasSize(2);

    final String actionLine = lines[0];
    assertThat(actionLine).contains("\"index\"");
    assertThat(actionLine).contains("\"_index\"");
    assertThat(actionLine).contains("\"_id\"");
  }

  @Test
  void shouldVerifyRequestMethodAndEndpoint() throws IOException, PersistenceException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);

    // when
    batchRequest.add(INDEX, entity);
    batchRequest.execute();

    // then
    final ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
    verify(restClient).performRequest(captor.capture());
    final Request request = captor.getValue();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getEndpoint()).isEqualTo("/_bulk");
    assertThat(request.getEntity().getContentType().getValue()).containsIgnoringCase("ndjson");
  }

  @Test
  void shouldStreamIndexWithRouting() throws IOException, PersistenceException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);
    final String routing = "routing";

    // when
    batchRequest.addWithRouting(INDEX, entity, routing);
    batchRequest.execute();

    // then
    final String ndJson = captureNdJsonBody();
    final String[] lines = ndJson.split("\n");
    assertThat(lines).hasSize(2);

    final String actionLine = lines[0];
    assertThat(actionLine).contains("\"index\"");
    assertThat(actionLine).contains(routing);
  }

  @Test
  void shouldStreamUpsertOperation() throws IOException, PersistenceException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);
    final Map<String, Object> updateFields = Map.of("id", "id2");

    // when
    batchRequest.upsertWithRouting(INDEX, ID, entity, updateFields, "routing");
    batchRequest.execute();

    // then
    final String ndJson = captureNdJsonBody();
    final String[] lines = ndJson.split("\n");
    assertThat(lines).hasSize(2);

    final String actionLine = lines[0];
    assertThat(actionLine).contains("\"update\"");

    final String bodyLine = lines[1];
    assertThat(bodyLine).contains("upsert");
  }

  @Test
  void shouldStreamDeleteOperation() throws IOException, PersistenceException {
    // when
    batchRequest.delete(INDEX, ID);
    batchRequest.execute();

    // then
    final String ndJson = captureNdJsonBody();
    final String[] lines = ndJson.split("\n");
    assertThat(lines).hasSize(1);

    final String actionLine = lines[0];
    assertThat(actionLine).contains("\"delete\"");
    assertThat(actionLine).contains("\"_index\"");
    assertThat(actionLine).contains("\"_id\"");
  }

  @Test
  void shouldNotSendRequestForEmptyBatch() throws IOException, PersistenceException {
    // when
    batchRequest.execute();

    // then
    verify(restClient, never()).performRequest(any(Request.class));
  }

  @Test
  void shouldThrowOnBulkErrors() throws IOException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);

    final String errorJson =
        """
        {"took":1,"errors":true,"items":[{"index":{"_index":"test","_id":"1",\
        "status":400,"error":{"type":"mapper_parsing_exception",\
        "reason":"failed to parse"}}}]}""";

    final var errorResponse = mock(org.elasticsearch.client.Response.class);
    final var errorEntity = mock(org.apache.http.HttpEntity.class);
    when(errorResponse.getEntity()).thenReturn(errorEntity);
    when(errorEntity.getContent())
        .thenReturn(new ByteArrayInputStream(errorJson.getBytes(StandardCharsets.UTF_8)));
    when(restClient.performRequest(any(Request.class))).thenReturn(errorResponse);

    // when
    batchRequest.add(INDEX, entity);

    // then
    assertThatThrownBy(() -> batchRequest.execute())
        .isInstanceOf(PersistenceException.class)
        .hasMessageContaining("failed to parse");
  }

  @Test
  void shouldCallCustomErrorHandler() throws IOException, PersistenceException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);

    final String errorJson =
        """
        {"took":1,"errors":true,"items":[{"index":{"_index":"%s","_id":"%s",\
        "status":400,"error":{"type":"mapper_parsing_exception",\
        "reason":"failed to parse"}}}]}"""
            .formatted(INDEX, ID);

    final var errorResponse = mock(org.elasticsearch.client.Response.class);
    final var errorEntity = mock(org.apache.http.HttpEntity.class);
    when(errorResponse.getEntity()).thenReturn(errorEntity);
    when(errorEntity.getContent())
        .thenReturn(new ByteArrayInputStream(errorJson.getBytes(StandardCharsets.UTF_8)));
    when(restClient.performRequest(any(Request.class))).thenReturn(errorResponse);

    @SuppressWarnings("unchecked")
    final BiConsumer<String, Error> errorHandler = mock(BiConsumer.class);

    // when
    batchRequest.add(INDEX, entity);
    batchRequest.execute(errorHandler);

    // then
    final ArgumentCaptor<String> indexCaptor = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<Error> errorCaptor = ArgumentCaptor.forClass(Error.class);
    verify(errorHandler).accept(indexCaptor.capture(), errorCaptor.capture());

    assertThat(indexCaptor.getValue()).isEqualTo(INDEX);
    final Error capturedError = errorCaptor.getValue();
    assertThat(capturedError.type()).isEqualTo("mapper_parsing_exception");
    assertThat(capturedError.status()).isEqualTo(400);
    assertThat(capturedError.message()).contains("failed to parse");
  }

  @Test
  void shouldStreamMultipleOperationsInBatch() throws IOException, PersistenceException {
    // given
    final TestExporterEntity entity1 = new TestExporterEntity().setId("id1");
    final TestExporterEntity entity2 = new TestExporterEntity().setId("id2");
    final Map<String, Object> updateFields = Map.of("id", "id3");

    // when
    batchRequest.add(INDEX, entity1);
    batchRequest.upsert(INDEX, "id2", entity2, updateFields);
    batchRequest.delete(INDEX, "id3");
    batchRequest.execute();

    // then
    final String ndJson = captureNdJsonBody();
    final String[] lines = ndJson.split("\n");

    // index: 1 action + 1 doc = 2 lines
    // update: 1 action + 1 doc = 2 lines
    // delete: 1 action line only = 1 line
    assertThat(lines).hasSize(5);

    assertThat(lines[0]).contains("\"index\"");
    assertThat(lines[2]).contains("\"update\"");
    assertThat(lines[4]).contains("\"delete\"");
  }

  private String captureNdJsonBody() throws IOException {
    final ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
    verify(restClient).performRequest(captor.capture());
    final Request request = captor.getValue();
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    request.getEntity().writeTo(baos);
    return baos.toString(StandardCharsets.UTF_8);
  }
}
