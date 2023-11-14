/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Variable;
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
public class OpensearchVariableDaoTest {

  @Mock
  private OpensearchQueryDSLWrapper mockQueryWrapper;

  @Mock
  private OpensearchRequestDSLWrapper mockRequestWrapper;

  @Mock
  private VariableTemplate mockVariableIndex;

  @Mock
  private RichOpenSearchClient mockOpensearchClient;

  private OpensearchVariableDao underTest;

  @BeforeEach
  public void setup() {
    underTest = new OpensearchVariableDao(mockQueryWrapper, mockRequestWrapper, mockVariableIndex, mockOpensearchClient);
  }

  @Test
  public void testBuildRequest() {
    SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);

    when(mockVariableIndex.getAlias()).thenReturn("variableIndex");
    when(mockRequestWrapper.searchRequestBuilder("variableIndex")).thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    SearchRequest.Builder result = underTest.buildRequest(new Query<>());

    // Verify the request was built with a tenant check, the variable index, and permissive matching
    assertThat(result).isSameAs(mockRequestBuilder);
    verify(mockQueryWrapper, times(1)).matchAll();
    verify(mockQueryWrapper, times(1)).withTenantCheck(any());
    verify(mockRequestWrapper, times(1)).searchRequestBuilder("variableIndex");
  }

  @Test
  public void testGetSortKey() {
    assertThat(underTest.getUniqueSortKey()).isEqualTo(Variable.KEY);
  }

  @Test
  public void testGetModelClass() {
    assertThat(underTest.getModelClass()).isEqualTo(Variable.class);
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
    Query<Variable> inputQuery = new Query<Variable>().setFilter(new Variable());

    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Verify that the query was not modified in any way
    verifyNoInteractions(mockSearchRequest);
    verifyNoInteractions(mockQueryWrapper);
  }

  @Test
  public void testBuildFilteringWithValidFields() {
    SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    Variable filter = new Variable().setKey(1L).setName("var").setScopeKey(2L)
        .setProcessInstanceKey(3L).setTruncated(true).setValue("val").setTenantId("tenant");

    Query<Variable> inputQuery = new Query<Variable>().setFilter(filter);

    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Verify that each field from the variable was added as a query term to the query
    verify(mockQueryWrapper, times(1)).term(Variable.KEY, filter.getKey());
    verify(mockQueryWrapper, times(1)).term(Variable.TENANT_ID, filter.getTenantId());
    verify(mockQueryWrapper, times(1)).term(Variable.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey());
    verify(mockQueryWrapper, times(1)).term(Variable.SCOPE_KEY, filter.getScopeKey());
    verify(mockQueryWrapper, times(1)).term(Variable.NAME, filter.getName());
    verify(mockQueryWrapper, times(1)).term(Variable.VALUE, filter.getValue());
    verify(mockQueryWrapper, times(1)).term(Variable.TRUNCATED, filter.getTruncated());
  }

  @Test
  public void testByKeyWithServerException() {
    // Mock the request building classes
    SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);

    when(mockVariableIndex.getAlias()).thenReturn("variableIndex");
    when(mockRequestWrapper.searchRequestBuilder("variableIndex")).thenReturn(mockRequestBuilder);
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

    when(mockVariableIndex.getAlias()).thenReturn("variableIndex");
    when(mockRequestWrapper.searchRequestBuilder("variableIndex")).thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    // Mock the response objects from opensearch
    SearchResponse mockOsResponse = Mockito.mock(SearchResponse.class);
    HitsMetadata<Variable> mockOsResults = Mockito.mock(HitsMetadata.class);
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

    when(mockVariableIndex.getAlias()).thenReturn("variableIndex");
    when(mockRequestWrapper.searchRequestBuilder("variableIndex")).thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    // Mock the response objects from opensearch
    SearchResponse mockOsResponse = Mockito.mock(SearchResponse.class);
    HitsMetadata<Variable> mockOsResults = Mockito.mock(HitsMetadata.class);
    TotalHits mockTotalHits = Mockito.mock(TotalHits.class);

    Variable validResult = new Variable().setName("var");
    Hit<Variable> validHit = Mockito.mock(Hit.class);
    when(validHit.source()).thenReturn(validResult);

    when(mockOsResults.hits()).thenReturn(Collections.singletonList(validHit));
    when(mockOsResponse.hits()).thenReturn(mockOsResults);
    when(mockOsResults.total()).thenReturn(mockTotalHits);
    when(mockTotalHits.value()).thenReturn(1L);

    // Set the mocked opensearch client to return the mocked response
    when(mockOpensearchClient.doc()).thenReturn(mockDocumentOperations);
    when(mockDocumentOperations.search(any(), any())).thenReturn(mockOsResponse);

    // Verify the variable returned is the same as the one that came directly from opensearch
    Variable result = underTest.byKey(1L);
    assertThat(result).isSameAs(validResult);
  }

  @Test
  public void testByKeyWithMultipleResult() {
    // Mock the request building classes
    SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);
    OpenSearchDocumentOperations mockDocumentOperations = Mockito.mock(OpenSearchDocumentOperations.class);

    when(mockVariableIndex.getAlias()).thenReturn("variableIndex");
    when(mockRequestWrapper.searchRequestBuilder("variableIndex")).thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    // Mock the response objects from opensearch
    SearchResponse mockOsResponse = Mockito.mock(SearchResponse.class);
    HitsMetadata<Variable> mockOsResults = Mockito.mock(HitsMetadata.class);
    TotalHits mockTotalHits = Mockito.mock(TotalHits.class);

    Hit<Variable> validHit = Mockito.mock(Hit.class);
    when(validHit.source()).thenReturn(new Variable().setName("var1"), new Variable().setName("var2"));

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
