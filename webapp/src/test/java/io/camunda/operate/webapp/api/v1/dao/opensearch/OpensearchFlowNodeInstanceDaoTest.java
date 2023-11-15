/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.TotalHits;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OpensearchFlowNodeInstanceDaoTest {

  @Mock
  private OpensearchQueryDSLWrapper mockQueryWrapper;

  @Mock
  private OpensearchRequestDSLWrapper mockRequestWrapper;

  @Mock
  private RichOpenSearchClient mockOpensearchClient;

  @Mock
  private FlowNodeInstanceTemplate mockFlowNodeIndex;

  @Mock
  private ProcessCache mockProcessCache;

  private OpensearchFlowNodeInstanceDao underTest;

  @BeforeEach
  public void setup() {
    underTest = new OpensearchFlowNodeInstanceDao(mockQueryWrapper, mockRequestWrapper, mockFlowNodeIndex, mockOpensearchClient, mockProcessCache);
  }

  @Test
  public void testGetSortKey() {
    assertThat(underTest.getUniqueSortKey()).isEqualTo(FlowNodeInstance.KEY);
  }

  @Test
  public void testGetModelClass() {
    assertThat(underTest.getModelClass()).isEqualTo(FlowNodeInstance.class);
  }

  @Test
  public void testGetIndexName() {
    when(mockFlowNodeIndex.getAlias()).thenReturn("flowNodeIndex");
    assertThat(underTest.getIndexName()).isEqualTo("flowNodeIndex");
    verify(mockFlowNodeIndex, times(1)).getAlias();
  }

  @Test
  public void testBuildRequest() {
    SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);

    when(mockFlowNodeIndex.getAlias()).thenReturn("flowNodeIndex");
    when(mockRequestWrapper.searchRequestBuilder("flowNodeIndex")).thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    SearchRequest.Builder result = underTest.buildRequest(new Query<>());

    // Verify the request was built with a tenant check, the flow node index, and permissive matching
    assertThat(result).isSameAs(mockRequestBuilder);
    verify(mockQueryWrapper, times(1)).matchAll();
    verify(mockQueryWrapper, times(1)).withTenantCheck(any());
    verify(mockRequestWrapper, times(1)).searchRequestBuilder("flowNodeIndex");
  }

  @Test
  public void testBuildFilteringWithNullFilter() {
    SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    underTest.buildFiltering(new Query<>(), mockSearchRequest);

    // Verify that the query was not modified in any way
    verifyNoInteractions(mockSearchRequest);
    verifyNoInteractions(mockQueryWrapper);
  }

  @Test
  public void testBuildFilteringWithAllNullFilterFields() {
    SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    Query<FlowNodeInstance> inputQuery = new Query<FlowNodeInstance>().setFilter(new FlowNodeInstance());

    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Verify that the query was not modified in any way
    verifyNoInteractions(mockSearchRequest);
    verifyNoInteractions(mockQueryWrapper);
  }

  @Test
  public void testBuildFilteringWithValidFields() {
    SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    FlowNodeInstance filter = new FlowNodeInstance().setKey(1L).setProcessInstanceKey(2L).setProcessDefinitionKey(3L)
        .setStartDate("01-01-2020").setEndDate("01-02-2020").setState("state").setType("type").setFlowNodeId("nodeA")
        .setIncident(true).setIncidentKey(4L).setTenantId("tenant");

    Query<FlowNodeInstance> inputQuery = new Query<FlowNodeInstance>().setFilter(filter);

    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Verify that each field from the flow node filter was added as a query term to the query
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.KEY, filter.getKey());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.START_DATE, filter.getStartDate());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.END_DATE, filter.getEndDate());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.STATE, filter.getState());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.TYPE, filter.getType());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.FLOW_NODE_ID, filter.getFlowNodeId());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.INCIDENT, filter.getIncident());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.INCIDENT_KEY, filter.getIncidentKey());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.TENANT_ID, filter.getTenantId());
  }

  @Test
  public void testBuildFilteringIgnoresFlowNodeName() {
    SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    FlowNodeInstance filter = new FlowNodeInstance().setFlowNodeName("name");

    Query<FlowNodeInstance> inputQuery = new Query<FlowNodeInstance>().setFilter(filter);

    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Verify that the query was not modified in any way
    verifyNoInteractions(mockSearchRequest);
    verifyNoInteractions(mockQueryWrapper);
  }

  @Test
  public void testByKeyWithServerException() {
    // Mock the request building classes
    SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);

    when(mockFlowNodeIndex.getAlias()).thenReturn("flowNodeIndex");
    when(mockRequestWrapper.searchRequestBuilder("flowNodeIndex")).thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    // Set the mocked opensearch client to throw an exception
    when(mockOpensearchClient.doc()).thenThrow(new RuntimeException());

    assertThrows(ServerException.class, () -> {
      underTest.byKey(1L);
    });
  }

  @Test
  public void testByKeyWithEmptyResults() {
    // Mock the request building classes
    SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);
    OpenSearchDocumentOperations mockDocumentOperations = Mockito.mock(OpenSearchDocumentOperations.class);

    when(mockFlowNodeIndex.getAlias()).thenReturn("flowNodeIndex");
    when(mockRequestWrapper.searchRequestBuilder("flowNodeIndex")).thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    // Mock the response objects from opensearch
    SearchResponse mockOsResponse = Mockito.mock(SearchResponse.class);
    HitsMetadata<FlowNodeInstance> mockOsResults = Mockito.mock(HitsMetadata.class);
    TotalHits mockTotalHits = Mockito.mock(TotalHits.class);

    when(mockOsResults.hits()).thenReturn(new LinkedList<>());
    when(mockOsResponse.hits()).thenReturn(mockOsResults);
    when(mockOsResults.total()).thenReturn(mockTotalHits);

    // Set the mocked opensearch client to return the mocked response
    when(mockOpensearchClient.doc()).thenReturn(mockDocumentOperations);
    when(mockDocumentOperations.search(any(), any())).thenReturn(mockOsResponse);

    assertThrows(ResourceNotFoundException.class, () -> {
      underTest.byKey(1L);
    });
  }

  @Test
  public void testByKeyWithValidResult() {
    // Mock the request building classes
    SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);
    OpenSearchDocumentOperations mockDocumentOperations = Mockito.mock(OpenSearchDocumentOperations.class);

    when(mockFlowNodeIndex.getAlias()).thenReturn("flowNodeIndex");
    when(mockRequestWrapper.searchRequestBuilder("flowNodeIndex")).thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    // Mock the response objects from opensearch
    SearchResponse mockOsResponse = Mockito.mock(SearchResponse.class);
    HitsMetadata<FlowNodeInstance> mockOsResults = Mockito.mock(HitsMetadata.class);
    TotalHits mockTotalHits = Mockito.mock(TotalHits.class);

    FlowNodeInstance validResult = new FlowNodeInstance().setFlowNodeId("taskA");
    Hit<FlowNodeInstance> validHit = Mockito.mock(Hit.class);
    when(validHit.source()).thenReturn(validResult);

    when(mockOsResults.hits()).thenReturn(Collections.singletonList(validHit));
    when(mockOsResponse.hits()).thenReturn(mockOsResults);
    when(mockOsResults.total()).thenReturn(mockTotalHits);
    when(mockTotalHits.value()).thenReturn(1L);

    // Set the mocked opensearch client to return the mocked response
    when(mockOpensearchClient.doc()).thenReturn(mockDocumentOperations);
    when(mockDocumentOperations.search(any(), any())).thenReturn(mockOsResponse);

    // Verify the flow node instance returned is the same as the one that came directly from opensearch
    FlowNodeInstance result = underTest.byKey(1L);
    assertThat(result).isSameAs(validResult);
  }

  @Test
  public void testByKeyWithMultipleResult() {
    // Mock the request building classes
    SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);
    OpenSearchDocumentOperations mockDocumentOperations = Mockito.mock(OpenSearchDocumentOperations.class);

    when(mockFlowNodeIndex.getAlias()).thenReturn("flowNodeIndex");
    when(mockRequestWrapper.searchRequestBuilder("flowNodeIndex")).thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    // Mock the response objects from opensearch
    SearchResponse mockOsResponse = Mockito.mock(SearchResponse.class);
    HitsMetadata<FlowNodeInstance> mockOsResults = Mockito.mock(HitsMetadata.class);
    TotalHits mockTotalHits = Mockito.mock(TotalHits.class);

    Hit<FlowNodeInstance> validHit = Mockito.mock(Hit.class);
    when(validHit.source()).thenReturn(new FlowNodeInstance().setFlowNodeId("start"),
        new FlowNodeInstance().setFlowNodeId("taskA"));

    when(mockOsResults.hits()).thenReturn(List.of(validHit, validHit));
    when(mockOsResponse.hits()).thenReturn(mockOsResults);
    when(mockOsResults.total()).thenReturn(mockTotalHits);
    when(mockTotalHits.value()).thenReturn(2L);

    // Set the mocked opensearch client to return the mocked response
    when(mockOpensearchClient.doc()).thenReturn(mockDocumentOperations);
    when(mockDocumentOperations.search(any(), any())).thenReturn(mockOsResponse);

    assertThrows(ServerException.class, () -> {
      underTest.byKey(1L);
    });
  }
}
