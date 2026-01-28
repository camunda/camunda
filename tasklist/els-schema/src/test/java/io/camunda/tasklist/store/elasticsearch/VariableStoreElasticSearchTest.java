/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ShardStatistics;
import co.elastic.clients.elasticsearch.core.ClearScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VariableStoreElasticSearchTest {

  @Captor private ArgumentCaptor<SearchRequest> searchRequestCaptor;
  @Mock private ElasticsearchClient es8Client;
  @Spy private VariableTemplate variableIndex = new VariableTemplate("test", true);

  @Spy
  private FlowNodeInstanceTemplate flowNodeInstanceIndex =
      new FlowNodeInstanceTemplate("test", true);

  @Spy
  private SnapshotTaskVariableTemplate taskVariableTemplate =
      new SnapshotTaskVariableTemplate("test", true);

  @Spy private TasklistProperties tasklistProperties = new TasklistProperties();
  @InjectMocks private VariableStoreElasticSearch instance;

  @BeforeEach
  void setUp() throws IOException {
    // Mock clear scroll to avoid NullPointerException
    final ClearScrollResponse clearScrollResponse = mock(ClearScrollResponse.class);
    when(es8Client.clearScroll(any(java.util.function.Function.class)))
        .thenReturn(clearScrollResponse);
  }

  @SuppressWarnings("unchecked")
  private <T> SearchResponse<T> createEmptySearchResponse() {
    final SearchResponse<T> response = mock(SearchResponse.class);
    final HitsMetadata<T> hitsMetadata = mock(HitsMetadata.class);
    when(response.hits()).thenReturn(hitsMetadata);
    when(hitsMetadata.hits()).thenReturn(Collections.emptyList());
    lenient()
        .when(hitsMetadata.total())
        .thenReturn(TotalHits.of(t -> t.value(0).relation(TotalHitsRelation.Eq)));
    lenient().when(response.scrollId()).thenReturn("scrolling_id");
    lenient()
        .when(response.shards())
        .thenReturn(ShardStatistics.of(s -> s.total(1).successful(1).failed(0)));
    return response;
  }

  @Test
  void getFlowNodeInstancesWhenInstancesNotFound() throws Exception {
    // Given
    final SearchResponse<FlowNodeInstanceEntity> mockedResponse = createEmptySearchResponse();
    when(es8Client.search(searchRequestCaptor.capture(), eq(FlowNodeInstanceEntity.class)))
        .thenReturn(mockedResponse);

    // When
    final List<FlowNodeInstanceEntity> result = instance.getFlowNodeInstances(List.of(1234567L));

    // Then
    final SearchRequest capturedSearchRequest = searchRequestCaptor.getValue();
    assertThat(capturedSearchRequest.index())
        .containsExactly(flowNodeInstanceIndex.getFullQualifiedName());
    assertThat(capturedSearchRequest.size()).isEqualTo(200);
    assertThat(result).isEmpty();
  }

  @Test
  void getVariablesByFlowNodeInstanceIdsShouldUseVariableIndex() throws Exception {
    // Given
    final SearchResponse<VariableEntity> mockedResponse = createEmptySearchResponse();
    when(es8Client.search(searchRequestCaptor.capture(), eq(VariableEntity.class)))
        .thenReturn(mockedResponse);

    // When
    final var result =
        instance.getVariablesByFlowNodeInstanceIds(List.of("flowNodeId1"), null, null);

    // Then
    final SearchRequest capturedSearchRequest = searchRequestCaptor.getValue();
    assertThat(capturedSearchRequest.index()).containsExactly(variableIndex.getFullQualifiedName());
    assertThat(result).isEmpty();
  }

  @Test
  void getVariablesByFlowNodeInstanceIdsWithVariableNamesShouldUseCorrectQuery() throws Exception {
    // Given
    final SearchResponse<VariableEntity> mockedResponse = createEmptySearchResponse();
    when(es8Client.search(searchRequestCaptor.capture(), eq(VariableEntity.class)))
        .thenReturn(mockedResponse);

    // When
    final List<String> varNames = List.of("varName1", "varName2");
    final var result =
        instance.getVariablesByFlowNodeInstanceIds(List.of("flowNodeId1"), varNames, null);

    // Then
    final SearchRequest capturedSearchRequest = searchRequestCaptor.getValue();
    assertThat(capturedSearchRequest.index()).containsExactly(variableIndex.getFullQualifiedName());

    // Verify query contains expected terms
    final var query = capturedSearchRequest.query();
    assertThat(query).isNotNull();
    assertThat(query.constantScore()).isNotNull();
    assertThat(query.constantScore().filter().bool().must()).hasSize(2);
    assertThat(result).isEmpty();
  }
}
