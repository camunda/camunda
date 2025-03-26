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

    final SearchHits mockedHints = mock();
    when(mockedResponse.getHits()).thenReturn(mockedHints);

    when(mockedHints.getHits()).thenReturn(new SearchHit[] {});

    // When
    final List<FlowNodeInstanceEntity> result = instance.getFlowNodeInstances(List.of("1234567"));

    // Then
    verify(esClient, never()).scroll(any(SearchScrollRequest.class), any(RequestOptions.class));

    final SearchRequest capturedSearchRequest = searchRequestCaptor.getValue();
    final String expectedAlias =
        String.format("test-operate-flownode-instance-%s_", flowNodeInstanceIndex.getVersion());
    assertThat(capturedSearchRequest.indices()).containsExactly(expectedAlias);
    assertThat(capturedSearchRequest.source().size()).isEqualTo(200);
    assertThat(result).isEmpty();
  }
}
