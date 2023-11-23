/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.TotalHits;

import java.util.Collections;
import java.util.LinkedList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OpensearchBaseDaoTest {

  @Mock
  private OpensearchQueryDSLWrapper mockQueryWrapper;

  @Mock
  private OpensearchRequestDSLWrapper mockRequestWrapper;

  @Mock
  private RichOpenSearchClient mockOpensearchClient;

  private OpensearchPageableDao<Object, Object> underTest;

  @BeforeEach
  public void setup() {
    underTest = new OpensearchPageableDao<>(mockQueryWrapper, mockRequestWrapper, mockOpensearchClient, null) {
      @Override
      protected String getUniqueSortKey() { return null; }

      @Override
      protected Class getModelClass() { return Object.class; }

      @Override
      protected String getIndexName() { return "index"; }

      @Override
      protected void buildFiltering(Query query, SearchRequest.Builder request) {}
    };
  }

  @Test
  public void testBuildRequest() {
    SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);

    when(mockRequestWrapper.searchRequestBuilder("index")).thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    SearchRequest.Builder result = underTest.buildSearchRequest(new Query<>());

    // Verify the request was built with a tenant check, the index name, and permissive matching
    assertThat(result).isSameAs(mockRequestBuilder);
    verify(mockQueryWrapper, times(1)).matchAll();
    verify(mockQueryWrapper, times(1)).withTenantCheck(any());
    verify(mockRequestWrapper, times(1)).searchRequestBuilder("index");
  }

  @Test
  public void testBuildPagingWithNoSortAfter() {
    Query<Object> inputQuery = new Query<>().setSize(2);
    SearchRequest.Builder request = Mockito.mock(SearchRequest.Builder.class);

    underTest.buildPaging(inputQuery, request);

    // Verify no searchAfter was set
    verify(request, times(0)).searchAfter(anyList());
    verify(request, times(1)).size(2);
  }

  @Test
  public void testBuildPagingWithSortAfter() {
    Query<Object> inputQuery = new Query<>().setSize(2).setSearchAfter(new String[]{"foo"});
    SearchRequest.Builder request = Mockito.mock(SearchRequest.Builder.class);

    underTest.buildPaging(inputQuery, request);

    // Verify searchAfter and page size were set on the request
    verify(request, times(1)).searchAfter(anyList());
    verify(request, times(1)).size(2);
  }

  @Test
  public void testFormatHitsWithNoResults() {
    HitsMetadata<Object> mockHitsMetadata = Mockito.mock(HitsMetadata.class);
    TotalHits mockTotalHits = Mockito.mock(TotalHits.class);

    when(mockHitsMetadata.hits()).thenReturn(new LinkedList<>());
    when(mockHitsMetadata.total()).thenReturn(mockTotalHits);
    when(mockTotalHits.value()).thenReturn(0L);

    Results<Object> results = underTest.formatHitsIntoResults(mockHitsMetadata);

    assertThat(results.getItems().isEmpty()).isTrue();
    assertThat(results.getSortValues().length).isEqualTo(0);
    assertThat(results.getTotal()).isEqualTo(0L);
  }

  @Test
  public void testFormatHitsWithResults() {
    HitsMetadata<Object> mockHitsMetadata = Mockito.mock(HitsMetadata.class);
    TotalHits mockTotalHits = Mockito.mock(TotalHits.class);

    Hit<Object> validHit = Mockito.mock(Hit.class);

    when(validHit.source()).thenReturn(new Object());
    when(validHit.sort()).thenReturn(Collections.singletonList("sortVal"));
    when(mockHitsMetadata.hits()).thenReturn(Collections.singletonList(validHit));
    when(mockHitsMetadata.total()).thenReturn(mockTotalHits);
    when(mockTotalHits.value()).thenReturn(1L);

    Results<Object> results = underTest.formatHitsIntoResults(mockHitsMetadata);

    assertThat(results.getItems().size()).isEqualTo(1);
    assertThat(results.getSortValues().length).isEqualTo(1);
    assertThat(results.getSortValues()[0]).isEqualTo("sortVal");
    assertThat(results.getTotal()).isEqualTo(1L);
  }

  @Test
  public void testBuildSortingNoSortOptions() {
    Query<Object> inputQuery = new Query<>();
    String uniqueSortKey = "processId";
    SearchRequest.Builder request = Mockito.mock(SearchRequest.Builder.class);

    underTest.buildSorting(inputQuery, uniqueSortKey, request);

    verify(mockQueryWrapper, times(1)).sortOptions(any(), any());
    verify(mockQueryWrapper, times(1)).sortOptions(uniqueSortKey, SortOrder.Asc);
  }

  @Test
  public void testBuildSortingWithAscOption() {
    Query.Sort sortOption = new Query.Sort().setField("key").setOrder(Query.Sort.Order.ASC);
    Query<Object> inputQuery = new Query<>().setSort(Collections.singletonList(sortOption));
    String uniqueSortKey = "processId";
    SearchRequest.Builder request = Mockito.mock(SearchRequest.Builder.class);

    underTest.buildSorting(inputQuery, uniqueSortKey, request);

    verify(mockQueryWrapper, times(2)).sortOptions(any(), any());
    verify(mockQueryWrapper, times(1)).sortOptions(uniqueSortKey, SortOrder.Asc);
    verify(mockQueryWrapper, times(1)).sortOptions(sortOption.getField(), SortOrder.Asc);
  }

  @Test
  public void testBuildSortingWithDescOption() {
    Query.Sort sortOption = new Query.Sort().setField("key").setOrder(Query.Sort.Order.DESC);
    Query<Object> inputQuery = new Query<>().setSort(Collections.singletonList(sortOption));
    String uniqueSortKey = "processId";
    SearchRequest.Builder request = Mockito.mock(SearchRequest.Builder.class);

    underTest.buildSorting(inputQuery, uniqueSortKey, request);

    verify(mockQueryWrapper, times(2)).sortOptions(any(), any());
    verify(mockQueryWrapper, times(1)).sortOptions(uniqueSortKey, SortOrder.Asc);
    verify(mockQueryWrapper, times(1)).sortOptions(sortOption.getField(), SortOrder.Desc);
  }
}
