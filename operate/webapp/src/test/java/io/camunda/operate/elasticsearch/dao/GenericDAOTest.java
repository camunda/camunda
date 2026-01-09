/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch.dao;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.store.elasticsearch.dao.GenericDAO;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.entities.MetricEntity;
import java.io.IOException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GenericDAOTest {

  @Mock private ElasticsearchClient es8Client;
  @Mock private ObjectMapper objectMapper;
  @Mock private MetricIndex index;
  @Mock private MetricEntity entity;

  @Test
  public void instantiateWithoutObjectMapperThrowsException() {
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(
            () ->
                new GenericDAO.Builder<MetricEntity, MetricIndex>()
                    .es8Client(es8Client)
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
                    .es8Client(es8Client)
                    .build());
  }

  @Test
  @Disabled("Skipping this test as we can't mock es8Client final methods")
  public void insertShouldReturnExpectedResponse() throws IOException {
    // Given
    final GenericDAO<MetricEntity, MetricIndex> dao = instantiateDao();
    final String indexName = "indexName";
    final String json = "json";
    when(index.getIndexName()).thenReturn(indexName);
    when(entity.getId()).thenReturn(null);
    when(objectMapper.writeValueAsString(any())).thenReturn(json);

    // When
    dao.insert(entity);

    // Then
    // Note: Cannot verify ES8 client calls due to final methods
    // This test is disabled as mocking ES8 client is not straightforward
  }

  private GenericDAO<MetricEntity, MetricIndex> instantiateDao() {
    return new GenericDAO.Builder<MetricEntity, MetricIndex>()
        .es8Client(es8Client)
        .index(index)
        .objectMapper(objectMapper)
        .build();
  }
}
