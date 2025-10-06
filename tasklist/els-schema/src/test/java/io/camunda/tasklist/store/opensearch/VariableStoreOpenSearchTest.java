/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.entities.VariableEntity;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;

@ExtendWith(MockitoExtension.class)
class VariableStoreOpenSearchTest {

  @Captor private ArgumentCaptor<SearchRequest> searchRequestCaptor;
  @Mock private OpenSearchClient osClient;
  @Spy private VariableTemplate variableIndex = new VariableTemplate("test", true);

  @Spy
  private FlowNodeInstanceTemplate flowNodeInstanceIndex =
      new FlowNodeInstanceTemplate("test", true);

  @Spy
  private SnapshotTaskVariableTemplate taskVariableTemplate =
      new SnapshotTaskVariableTemplate("test", true);

  @Spy private TasklistProperties tasklistProperties = new TasklistProperties();
  @Spy private ObjectMapper objectMapper = CommonUtils.OBJECT_MAPPER;
  @InjectMocks private VariableStoreOpenSearch instance;

  @Test
  void getVariablesByFlowNodeInstanceIdsShouldUseVariableIndex() throws Exception {
    // Given
    final SearchResponse<VariableEntity> mockedResponse = mock();
    when(osClient.search(searchRequestCaptor.capture(), any(Class.class)))
        .thenReturn(mockedResponse);

    when(mockedResponse.scrollId()).thenReturn("scrolling_id1");
    when(mockedResponse.hits()).thenReturn(mock());
    when(mockedResponse.hits().hits()).thenReturn(List.of());

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
    final SearchResponse mockedResponse = mock();
    when(osClient.search(searchRequestCaptor.capture(), any())).thenReturn(mockedResponse);

    when(mockedResponse.scrollId()).thenReturn("scrolling_id2");
    when(mockedResponse.hits()).thenReturn(mock());
    when(mockedResponse.hits().hits()).thenReturn(List.of());

    // When
    final List<String> varNames = List.of("varName1", "varName2");
    final var result =
        instance.getVariablesByFlowNodeInstanceIds(List.of("flowNodeId1"), varNames, Set.of());

    // Then
    final SearchRequest capturedSearchRequest = searchRequestCaptor.getValue();
    assertThat(capturedSearchRequest.index()).containsExactly(variableIndex.getFullQualifiedName());
    // Verify query
    final var filters = capturedSearchRequest.query().constantScore().filter().bool().must();
    assertThat(filters).hasSize(2);
    assertThat(filters.getFirst().terms().field()).isEqualTo(FlowNodeInstanceTemplate.SCOPE_KEY);
    assertThat(filters.getFirst().terms().terms().value().stream().map(FieldValue::_get))
        .isEqualTo(List.of("flowNodeId1"));
    assertThat(filters.getLast().terms().field()).isEqualTo(VariableTemplate.NAME);
    assertThat(filters.getLast().terms().terms().value().stream().map(FieldValue::_get))
        .isEqualTo(varNames);
    assertThat(capturedSearchRequest.size()).isEqualTo(200);
    assertThat(result).isEmpty();
  }
}
