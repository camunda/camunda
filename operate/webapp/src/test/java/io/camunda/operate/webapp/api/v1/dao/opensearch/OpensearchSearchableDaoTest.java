/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import java.util.Collections;
import java.util.LinkedList;
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

@ExtendWith(MockitoExtension.class)
public class OpensearchSearchableDaoTest {

  @Mock private OpensearchQueryDSLWrapper mockQueryWrapper;

  @Mock private OpensearchRequestDSLWrapper mockRequestWrapper;

  @Mock private RichOpenSearchClient mockOpensearchClient;

  private OpensearchSearchableDao<Object, Object> underTest;

  @BeforeEach
  public void setup() {
    underTest =
        new OpensearchSearchableDao<>(mockQueryWrapper, mockRequestWrapper, mockOpensearchClient) {
          @Override
          protected String getUniqueSortKey() {
            return null;
          }

          @Override
          protected Class getInternalDocumentModelClass() {
            return Object.class;
          }

          @Override
          protected String getIndexName() {
            return "index";
          }

          @Override
          protected void buildFiltering(final Query query, final SearchRequest.Builder request) {}

          @Override
          protected Object convertInternalToApiResult(final Object internalResult) {
            return internalResult;
          }
        };
  }

  @Test
  public void testBuildRequest() {
    final SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    final org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);

    when(mockRequestWrapper.searchRequestBuilder("index")).thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    final SearchRequest.Builder result = underTest.buildSearchRequest(new Query<>());

    // Verify the request was built with a tenant check, the index name, and permissive matching
    assertThat(result).isSameAs(mockRequestBuilder);
    verify(mockQueryWrapper, times(1)).matchAll();
    verify(mockQueryWrapper, times(1)).withTenantCheck(any());
    verify(mockRequestWrapper, times(1)).searchRequestBuilder("index");
  }

  @Test
  public void testBuildPagingWithNoSortAfter() {
    final Query<Object> inputQuery = new Query<>().setSize(2);
    final SearchRequest.Builder request = Mockito.mock(SearchRequest.Builder.class);

    underTest.buildPaging(inputQuery, request);

    // Verify no searchAfter was set
    verify(request, times(0)).searchAfter(anyList());
    verify(request, times(1)).size(2);
  }

  @Test
  public void testBuildPagingWithSortAfter() {
    final Query<Object> inputQuery = new Query<>().setSize(2).setSearchAfter(new String[] {"foo"});
    final SearchRequest.Builder request = Mockito.mock(SearchRequest.Builder.class);

    underTest.buildPaging(inputQuery, request);

    // Verify searchAfter and page size were set on the request
    verify(request, times(1)).searchAfter(anyList());
    verify(request, times(1)).size(2);
  }

  @Test
  public void testFormatHitsWithNoResults() {
    final HitsMetadata<Object> mockHitsMetadata = Mockito.mock(HitsMetadata.class);
    final TotalHits mockTotalHits = Mockito.mock(TotalHits.class);

    when(mockHitsMetadata.hits()).thenReturn(new LinkedList<>());
    when(mockHitsMetadata.total()).thenReturn(mockTotalHits);
    when(mockTotalHits.value()).thenReturn(0L);

    final Results<Object> results = underTest.formatHitsIntoResults(mockHitsMetadata);

    assertThat(results.getItems().isEmpty()).isTrue();
    assertThat(results.getSortValues().length).isEqualTo(0);
    assertThat(results.getTotal()).isEqualTo(0L);
  }

  @Test
  public void testFormatHitsWithResults() {
    final HitsMetadata<Object> mockHitsMetadata = Mockito.mock(HitsMetadata.class);
    final TotalHits mockTotalHits = Mockito.mock(TotalHits.class);

    final Hit<Object> validHit = Mockito.mock(Hit.class);

    when(validHit.source()).thenReturn(new Object());
    when(validHit.sort()).thenReturn(Collections.singletonList("sortVal"));
    when(mockHitsMetadata.hits()).thenReturn(Collections.singletonList(validHit));
    when(mockHitsMetadata.total()).thenReturn(mockTotalHits);
    when(mockTotalHits.value()).thenReturn(1L);

    final Results<Object> results = underTest.formatHitsIntoResults(mockHitsMetadata);

    assertThat(results.getItems().size()).isEqualTo(1);
    assertThat(results.getSortValues().length).isEqualTo(1);
    assertThat(results.getSortValues()[0]).isEqualTo("sortVal");
    assertThat(results.getTotal()).isEqualTo(1L);
  }

  @Test
  public void testBuildSortingNoSortOptions() {
    final Query<Object> inputQuery = new Query<>();
    final String uniqueSortKey = "processId";
    final SearchRequest.Builder request = Mockito.mock(SearchRequest.Builder.class);

    underTest.buildSorting(inputQuery, uniqueSortKey, request);

    verify(mockQueryWrapper, times(1)).sortOptions(any(), any());
    verify(mockQueryWrapper, times(1)).sortOptions(uniqueSortKey, SortOrder.Asc);
  }

  @Test
  public void testBuildSortingWithAscOption() {
    final Query.Sort sortOption = new Query.Sort().setField("key").setOrder(Query.Sort.Order.ASC);
    final Query<Object> inputQuery = new Query<>().setSort(Collections.singletonList(sortOption));
    final String uniqueSortKey = "processId";
    final SearchRequest.Builder request = Mockito.mock(SearchRequest.Builder.class);

    underTest.buildSorting(inputQuery, uniqueSortKey, request);

    verify(mockQueryWrapper, times(2)).sortOptions(any(), any());
    verify(mockQueryWrapper, times(1)).sortOptions(uniqueSortKey, SortOrder.Asc);
    verify(mockQueryWrapper, times(1)).sortOptions(sortOption.getField(), SortOrder.Asc);
  }

  @Test
  public void testBuildSortingWithDescOption() {
    final Query.Sort sortOption = new Query.Sort().setField("key").setOrder(Query.Sort.Order.DESC);
    final Query<Object> inputQuery = new Query<>().setSort(Collections.singletonList(sortOption));
    final String uniqueSortKey = "processId";
    final SearchRequest.Builder request = Mockito.mock(SearchRequest.Builder.class);

    underTest.buildSorting(inputQuery, uniqueSortKey, request);

    verify(mockQueryWrapper, times(2)).sortOptions(any(), any());
    verify(mockQueryWrapper, times(1)).sortOptions(uniqueSortKey, SortOrder.Asc);
    verify(mockQueryWrapper, times(1)).sortOptions(sortOption.getField(), SortOrder.Desc);
  }
}
