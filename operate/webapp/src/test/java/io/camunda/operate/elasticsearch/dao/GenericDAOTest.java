/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.store.elasticsearch.dao.GenericDAO;
import io.camunda.operate.store.elasticsearch.dao.response.InsertResponse;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.entities.MetricEntity;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GenericDAOTest {

  @Mock private ElasticsearchClient esClient;
  @Mock private ObjectMapper objectMapper;
  @Mock private MetricIndex index;
  @Mock private MetricEntity entity;

  @Captor private ArgumentCaptor<IndexRequest<MetricEntity>> indexRequestCaptor;

  @Test
  public void instantiateWithoutObjectMapperThrowsException() {
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(
            () ->
                new GenericDAO.Builder<MetricEntity, MetricIndex>()
                    .es8Client(esClient)
                    .index(index)
                    .build());
  }

  @Test
  public void instantiateWithoutESClientThrowsException() {
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(
            () ->
                new GenericDAO.Builder<MetricEntity, MetricIndex>()
                    .objectMapper(objectMapper)
                    .index(index)
                    .build());
  }

  @Test
  public void instantiateWithoutIndexThrowsException() {
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(
            () ->
                new GenericDAO.Builder<MetricEntity, MetricIndex>()
                    .objectMapper(objectMapper)
                    .es8Client(esClient)
                    .build());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void insertShouldReturnExpectedResponse() throws IOException {
    // Given
    final GenericDAO<MetricEntity, MetricIndex> dao = instantiateDao();
    final String indexName = "indexName";
    final String entityId = "test-id";
    when(index.getFullQualifiedName()).thenReturn(indexName);
    when(entity.getId()).thenReturn(entityId);

    final IndexResponse indexResponse = mock(IndexResponse.class);
    when(indexResponse.result()).thenReturn(Result.Created);
    when(esClient.index(any(IndexRequest.class))).thenReturn(indexResponse);

    // When
    final InsertResponse response = dao.insert(entity);

    // Then
    assertThat(response.hasError()).isFalse();

    verify(esClient).index(indexRequestCaptor.capture());
    final IndexRequest<MetricEntity> capturedRequest = indexRequestCaptor.getValue();
    assertThat(capturedRequest.index()).isEqualTo(indexName);
    assertThat(capturedRequest.id()).isEqualTo(entityId);
    assertThat(capturedRequest.document()).isEqualTo(entity);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void insertShouldReturnFailureWhenNotCreated() throws IOException {
    // Given
    final GenericDAO<MetricEntity, MetricIndex> dao = instantiateDao();
    final String indexName = "indexName";
    when(index.getFullQualifiedName()).thenReturn(indexName);
    when(entity.getId()).thenReturn("test-id");

    final IndexResponse indexResponse = mock(IndexResponse.class);
    when(indexResponse.result()).thenReturn(Result.Updated);
    when(esClient.index(any(IndexRequest.class))).thenReturn(indexResponse);

    // When
    final InsertResponse response = dao.insert(entity);

    // Then
    assertThat(response.hasError()).isTrue();
  }

  private GenericDAO<MetricEntity, MetricIndex> instantiateDao() {
    return new GenericDAO.Builder<MetricEntity, MetricIndex>()
        .es8Client(esClient)
        .index(index)
        .objectMapper(objectMapper)
        .build();
  }
}
