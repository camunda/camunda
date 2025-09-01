package io.camunda.search.clients.reader;

import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TypedSearchAggregationQuery;
import io.camunda.security.reader.ResourceAccessChecks;

public interface SearchQueryStatisticsReader<T, A extends TypedSearchAggregationQuery<?, ?, ?>>
    extends SearchClientReader {
  SearchQueryResult<T> aggregate(final A query, final ResourceAccessChecks resourceAccessChecks);
}
