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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
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
  @Mock private RestHighLevelClient esClient;
  @Spy private VariableTemplate variableIndex = new VariableTemplate("test", true);

  @Spy
  private FlowNodeInstanceTemplate flowNodeInstanceIndex =
      new FlowNodeInstanceTemplate("test", true);

  @Spy
  private SnapshotTaskVariableTemplate taskVariableTemplate =
      new SnapshotTaskVariableTemplate("test", true);

  @Spy private TasklistProperties tasklistProperties = new TasklistProperties();
  @Spy private ObjectMapper objectMapper = CommonUtils.OBJECT_MAPPER;
  @InjectMocks private VariableStoreElasticSearch instance;

  @Test
  void getFlowNodeInstancesWhenInstancesNotFound() throws Exception {
    // Given
    final SearchResponse mockedResponse = mock();
    when(esClient.search(searchRequestCaptor.capture(), eq(RequestOptions.DEFAULT)))
        .thenReturn(mockedResponse);

    when(mockedResponse.getScrollId()).thenReturn("scrolling_id0");

    final SearchHits mockedHits = mock();
    when(mockedResponse.getHits()).thenReturn(mockedHits);

    when(mockedHits.getHits()).thenReturn(new SearchHit[] {});

    // When
    final List<FlowNodeInstanceEntity> result = instance.getFlowNodeInstances(List.of(1234567L));

    // Then
    verify(esClient, never()).scroll(any(SearchScrollRequest.class), any(RequestOptions.class));

    final SearchRequest capturedSearchRequest = searchRequestCaptor.getValue();
    assertThat(capturedSearchRequest.indices())
        .containsExactly(flowNodeInstanceIndex.getFullQualifiedName());
    assertThat(capturedSearchRequest.source().size()).isEqualTo(200);
    assertThat(result).isEmpty();
  }

  @Test
  void getVariablesByFlowNodeInstanceIdsShouldUseVariableIndex() throws Exception {
    // Given
    final SearchResponse mockedResponse = mock();
    when(esClient.search(searchRequestCaptor.capture(), eq(RequestOptions.DEFAULT)))
        .thenReturn(mockedResponse);

    when(mockedResponse.getScrollId()).thenReturn("scrolling_id1");

    final SearchHits mockedHits = mock();
    when(mockedResponse.getHits()).thenReturn(mockedHits);

    when(mockedHits.getHits()).thenReturn(new SearchHit[] {});

    // When
    final var result =
        instance.getVariablesByFlowNodeInstanceIds(List.of("flowNodeId1"), null, null);

    // Then
    final SearchRequest capturedSearchRequest = searchRequestCaptor.getValue();
    assertThat(capturedSearchRequest.indices())
        .containsExactly(variableIndex.getFullQualifiedName());
    assertThat(result).isEmpty();
  }

  @Test
  void getVariablesByFlowNodeInstanceIdsWithVariableNamesShouldUseCorrectQuery() throws Exception {
    // Given
    final SearchResponse mockedResponse = mock();
    when(esClient.search(searchRequestCaptor.capture(), eq(RequestOptions.DEFAULT)))
        .thenReturn(mockedResponse);

    when(mockedResponse.getScrollId()).thenReturn("scrolling_id2");

    final SearchHits mockedHits = mock();
    when(mockedResponse.getHits()).thenReturn(mockedHits);

    when(mockedHits.getHits()).thenReturn(new SearchHit[] {});

    // When
    final List<String> varNames = List.of("varName1", "varName2");
    final var result =
        instance.getVariablesByFlowNodeInstanceIds(List.of("flowNodeId1"), varNames, null);

    // Then
    verify(esClient, never()).scroll(any(SearchScrollRequest.class), any(RequestOptions.class));

    final SearchRequest capturedSearchRequest = searchRequestCaptor.getValue();
    assertThat(capturedSearchRequest.indices())
        .containsExactly(variableIndex.getFullQualifiedName());

    // Verify query
    final String queryAsString = capturedSearchRequest.source().toString();
    assertThat(queryAsString)
        .isEqualTo(
            """
           {"size":200,"query":{"constant_score":{"filter":{"bool":{"must":[{"terms":{"scopeKey":["flowNodeId1"],"boost":1.0}},{"terms":{"name":["varName1","varName2"],"boost":1.0}}],"adjust_pure_negative":true,"boost":1.0}},"boost":1.0}}}""");
  }
}
