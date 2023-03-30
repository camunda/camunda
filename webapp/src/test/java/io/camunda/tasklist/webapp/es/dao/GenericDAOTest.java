/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es.dao;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.MetricEntity;
import io.camunda.tasklist.schema.indices.MetricIndex;
import java.io.IOException;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.xcontent.XContentType;
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
    assertThatThrownBy(
            () ->
                new GenericDAO.Builder<MetricEntity, MetricIndex>()
                    .esClient(esClient)
                    .index(index)
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("ObjectMapper can't be null");
  }

  @Test
  public void instantiateWithoutESClientThrowsException() {
    assertThatThrownBy(
            () ->
                new GenericDAO.Builder<MetricEntity, MetricIndex>()
                    .objectMapper(objectMapper)
                    .index(index)
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("ES Client can't be null");
  }

  @Test
  public void instantiateWithoutIndexThrowsException() {
    assertThatThrownBy(
            () ->
                new GenericDAO.Builder<MetricEntity, MetricIndex>()
                    .objectMapper(objectMapper)
                    .esClient(esClient)
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Index can't be null");
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
