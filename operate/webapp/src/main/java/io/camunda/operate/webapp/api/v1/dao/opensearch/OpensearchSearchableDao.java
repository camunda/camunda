/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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

import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import java.util.List;
import java.util.Objects;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;

/**
 * @param <T> - API model class
 * @param <R> - Internal model class that maps to an opensearch document and fields
 */
public abstract class OpensearchSearchableDao<T, R> {

  protected final OpensearchQueryDSLWrapper queryDSLWrapper;
  protected final OpensearchRequestDSLWrapper requestDSLWrapper;
  protected final RichOpenSearchClient richOpenSearchClient;

  public OpensearchSearchableDao(
      OpensearchQueryDSLWrapper queryDSLWrapper,
      OpensearchRequestDSLWrapper requestDSLWrapper,
      RichOpenSearchClient richOpenSearchClient) {
    this.queryDSLWrapper = queryDSLWrapper;
    this.requestDSLWrapper = requestDSLWrapper;
    this.richOpenSearchClient = richOpenSearchClient;
  }

  public Results<T> search(Query<T> query) {
    final var request = buildSearchRequest(query);

    buildSorting(query, getUniqueSortKey(), request);
    buildFiltering(query, request);
    buildPaging(query, request);

    try {
      final HitsMetadata<R> results =
          richOpenSearchClient.doc().search(request, getInternalDocumentModelClass()).hits();

      return formatHitsIntoResults(results);
    } catch (Exception e) {
      throw new ServerException("Error in reading incidents", e);
    }
  }

  protected SearchRequest.Builder buildSearchRequest(Query<T> query) {
    return requestDSLWrapper
        .searchRequestBuilder(getIndexName())
        .query(queryDSLWrapper.withTenantCheck(queryDSLWrapper.matchAll()));
  }

  protected abstract String getUniqueSortKey();

  protected abstract Class<R> getInternalDocumentModelClass();

  protected abstract String getIndexName();

  protected void buildSorting(Query<T> query, String uniqueSortKey, SearchRequest.Builder request) {
    final List<Query.Sort> sorts = query.getSort();
    if (sorts != null) {
      sorts.forEach(
          sort -> {
            final Query.Sort.Order order = sort.getOrder();
            if (order.equals(Query.Sort.Order.DESC)) {
              request.sort(queryDSLWrapper.sortOptions(sort.getField(), SortOrder.Desc));
            } else {
              // if not specified always assume ASC order
              request.sort(queryDSLWrapper.sortOptions(sort.getField(), SortOrder.Asc));
            }
          });
    }
    request.sort(queryDSLWrapper.sortOptions(uniqueSortKey, SortOrder.Asc));
  }

  protected void buildPaging(Query<T> query, SearchRequest.Builder request) {
    final Object[] searchAfter = query.getSearchAfter();
    if (searchAfter != null) {
      request.searchAfter(CollectionUtil.toSafeListOfStrings(searchAfter));
    }
    request.size(query.getSize());
  }

  protected abstract void buildFiltering(Query<T> query, SearchRequest.Builder request);

  protected Results<T> formatHitsIntoResults(HitsMetadata<R> results) {
    final List<Hit<R>> hits = results.hits();

    if (!hits.isEmpty()) {
      final List<T> items =
          hits.stream()
              .map(hit -> convertInternalToApiResult(hit.source()))
              .filter(Objects::nonNull)
              .toList();

      final List<String> sortValues = hits.get(hits.size() - 1).sort();

      return new Results<T>()
          .setTotal(results.total().value())
          .setItems(items)
          .setSortValues(sortValues.toArray());
    } else {
      return new Results<T>().setTotal(results.total().value());
    }
  }

  protected abstract T convertInternalToApiResult(R internalResult);
}
