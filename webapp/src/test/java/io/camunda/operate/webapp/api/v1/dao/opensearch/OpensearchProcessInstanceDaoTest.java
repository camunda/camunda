/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import io.camunda.operate.webapp.writer.ProcessInstanceWriter;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OpensearchProcessInstanceDaoTest {

  @Mock
  private OpensearchQueryDSLWrapper mockQueryWrapper;

  @Mock
  private OpensearchRequestDSLWrapper mockRequestWrapper;

  @Mock
  private RichOpenSearchClient mockOpensearchClient;

  @Mock
  private ProcessInstanceWriter mockProcessInstanceWriter;

  @Mock
  private ListViewTemplate mockProcessInstanceIndex;

  private OpensearchProcessInstanceDao underTest;

  @BeforeEach
  public void setup() {
    underTest = new OpensearchProcessInstanceDao(mockQueryWrapper, mockRequestWrapper, mockProcessInstanceIndex,
        mockOpensearchClient, mockProcessInstanceWriter);
  }

  @Test
  public void testGetSortKey() {
    assertThat(underTest.getUniqueSortKey()).isEqualTo(ListViewTemplate.KEY);
  }

  @Test
  public void testGetModelClass() {
    assertThat(underTest.getModelClass()).isEqualTo(ProcessInstance.class);
  }

  @Test
  public void testGetIndexName() {
    when(mockProcessInstanceIndex.getAlias()).thenReturn("processInstanceIndex");
    assertThat(underTest.getIndexName()).isEqualTo("processInstanceIndex");
    verify(mockProcessInstanceIndex, times(1)).getAlias();
  }

  @Test
  public void testBuildRequest() {
    SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);

    when(mockProcessInstanceIndex.getAlias()).thenReturn("processInstanceIndex");
    when(mockRequestWrapper.searchRequestBuilder("processInstanceIndex")).thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    SearchRequest.Builder result = underTest.buildRequest(new Query<>());

    // Verify the request was built with a tenant check, the process instance index, and permissive matching
    assertThat(result).isSameAs(mockRequestBuilder);
    verify(mockQueryWrapper, times(1)).matchAll();
    verify(mockQueryWrapper, times(1)).withTenantCheck(any());
    verify(mockRequestWrapper, times(1)).searchRequestBuilder("processInstanceIndex");
  }

  @Test
  public void testBuildFilteringWithNullFilter() {
    SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    underTest.buildFiltering(new Query<>(), mockSearchRequest);

    // Verify that the join relation was still set
    verify(mockQueryWrapper, times(1)).term(ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION);
  }

  @Test
  public void testBuildFilteringWithAllNullFilterFields() {
    SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    Query<ProcessInstance> inputQuery = new Query<ProcessInstance>().setFilter(new ProcessInstance());

    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Verify that the join relation was still set
    verify(mockQueryWrapper, times(1)).term(ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION);
  }

  @Test
  public void testBuildFilteringWithValidFields() {
    SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    ProcessInstance filter = new ProcessInstance().setKey(1L).setProcessDefinitionKey(2L).setParentKey(3L)
        .setParentFlowNodeInstanceKey(4L).setProcessVersion(1).setBpmnProcessId("bpmnId").setState("state")
        .setTenantId("tenant").setStartDate("01-01-2020").setEndDate("01-02-2020");

    Query<ProcessInstance> inputQuery = new Query<ProcessInstance>().setFilter(filter);

    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Verify that each field from the process instance filter was added as a query term to the query
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.KEY, filter.getKey());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.PARENT_KEY, filter.getParentKey());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.PARENT_FLOW_NODE_INSTANCE_KEY, filter.getParentFlowNodeInstanceKey());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.VERSION, filter.getProcessVersion());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.BPMN_PROCESS_ID, filter.getBpmnProcessId());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.STATE, filter.getState());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.TENANT_ID, filter.getTenantId());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.START_DATE, filter.getStartDate());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.END_DATE, filter.getEndDate());

    // Verify that the join relation was still set
    verify(mockQueryWrapper, times(1)).term(ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION);
  }

  @Test
  public void testByKeyWithServerException() {
    // Mock the request building classes
    SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);

    when(mockProcessInstanceIndex.getAlias()).thenReturn("processInstanceIndex");
    when(mockRequestWrapper.searchRequestBuilder("processInstanceIndex")).thenReturn(mockRequestBuilder);
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

    when(mockProcessInstanceIndex.getAlias()).thenReturn("processInstanceIndex");
    when(mockRequestWrapper.searchRequestBuilder("processInstanceIndex")).thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    // Mock the response objects from opensearch
    SearchResponse mockOsResponse = Mockito.mock(SearchResponse.class);
    HitsMetadata<ProcessInstance> mockOsResults = Mockito.mock(HitsMetadata.class);
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

    when(mockProcessInstanceIndex.getAlias()).thenReturn("processInstanceIndex");
    when(mockRequestWrapper.searchRequestBuilder("processInstanceIndex")).thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    // Mock the response objects from opensearch
    SearchResponse mockOsResponse = Mockito.mock(SearchResponse.class);
    HitsMetadata<ProcessInstance> mockOsResults = Mockito.mock(HitsMetadata.class);
    TotalHits mockTotalHits = Mockito.mock(TotalHits.class);

    ProcessInstance validResult = new ProcessInstance().setKey(100L);
    Hit<ProcessInstance> validHit = Mockito.mock(Hit.class);
    when(validHit.source()).thenReturn(validResult);

    when(mockOsResults.hits()).thenReturn(Collections.singletonList(validHit));
    when(mockOsResponse.hits()).thenReturn(mockOsResults);
    when(mockOsResults.total()).thenReturn(mockTotalHits);
    when(mockTotalHits.value()).thenReturn(1L);

    // Set the mocked opensearch client to return the mocked response
    when(mockOpensearchClient.doc()).thenReturn(mockDocumentOperations);
    when(mockDocumentOperations.search(any(), any())).thenReturn(mockOsResponse);

    // Verify the process instance returned is the same as the one that came directly from opensearch
    ProcessInstance result = underTest.byKey(100L);
    assertThat(result).isSameAs(validResult);
  }
}
