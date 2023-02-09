/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.FlowNodeInstanceEntity;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.FlowNodeInstanceIndex;
import io.camunda.tasklist.schema.indices.VariableIndex;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.webapp.CommonUtils;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VariableReaderWriterTest {

  @Captor private ArgumentCaptor<SearchRequest> searchRequestCaptor;
  @Mock private RestHighLevelClient esClient;
  @Spy private FlowNodeInstanceIndex flowNodeInstanceIndex = new FlowNodeInstanceIndex();
  @Spy private VariableIndex variableIndex = new VariableIndex();
  @Spy private TaskVariableTemplate taskVariableTemplate = new TaskVariableTemplate();
  @Spy private TasklistProperties tasklistProperties = new TasklistProperties();
  @Spy private ObjectMapper objectMapper = CommonUtils.getObjectMapper();

  @InjectMocks private VariableReaderWriter instance;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(taskVariableTemplate, "tasklistProperties", tasklistProperties);
    ReflectionTestUtils.setField(variableIndex, "tasklistProperties", tasklistProperties);
    ReflectionTestUtils.setField(flowNodeInstanceIndex, "tasklistProperties", tasklistProperties);
  }

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
    assertEquals(1, capturedSearchRequest.indices().length, "indices count is wrong");
    final String expectedAlias = "tasklist-flownode-instance-1.0.0_alias";
    assertEquals(expectedAlias, capturedSearchRequest.indices()[0], "search index is wrong");
    assertEquals(200, capturedSearchRequest.source().size(), "batch size is wrong");
    assertTrue(result.isEmpty(), "result is not empty");
  }
}
