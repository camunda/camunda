/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch.dao;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.store.elasticsearch.dao.GenericDAO;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.entities.MetricEntity;
import java.io.IOException;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.xcontent.XContentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GenericDAOTest {

  @Mock private RestHighLevelClient esClient;
  @Mock private ObjectMapper objectMapper;
  @Mock private MetricIndex index;
  @Mock private MetricEntity entity;

  @Test
  public void instantiateWithoutObjectMapperThrowsException() {
    Assertions.assertThrows(
        IllegalStateException.class,
        () ->
            new GenericDAO.Builder<MetricEntity, MetricIndex>()
                .esClient(esClient)
                .index(index)
                .build());
  }

  @Test
  public void instantiateWithoutESClientThrowsException() {
    Assertions.assertThrows(
        IllegalStateException.class,
        () ->
            new GenericDAO.Builder<MetricEntity, MetricIndex>()
                .objectMapper(objectMapper)
                .index(index)
                .build());
  }

  @Test
  public void instantiateWithoutIndexThrowsException() {
    Assertions.assertThrows(
        IllegalStateException.class,
        () ->
            new GenericDAO.Builder<MetricEntity, MetricIndex>()
                .objectMapper(objectMapper)
                .esClient(esClient)
                .build());
  }

  @Test
  @Disabled("Skipping this test as we can't mock esClient final methods")
  public void insertShouldReturnExpectedResponse() throws IOException {
    // Given
    final GenericDAO<MetricEntity, MetricIndex> dao = instantiateDao();
    final String indexName = "indexName";
    final String json = "json";
    when(index.getIndexName()).thenReturn(indexName);
    when(entity.getId()).thenReturn(null);
    when(objectMapper.writeValueAsString(any())).thenReturn(json);

    final IndexRequest request =
        new IndexRequest(indexName).id(null).source(json, XContentType.JSON);

    // When
    dao.insert(entity);

    // Then
    verify(esClient).index(request, RequestOptions.DEFAULT);
  }

  private GenericDAO<MetricEntity, MetricIndex> instantiateDao() {
    return new GenericDAO.Builder<MetricEntity, MetricIndex>()
        .esClient(esClient)
        .index(index)
        .objectMapper(objectMapper)
        .build();
  }
}
