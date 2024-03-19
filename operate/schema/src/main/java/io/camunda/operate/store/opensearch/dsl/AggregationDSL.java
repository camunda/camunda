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
package io.camunda.operate.store.opensearch.dsl;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.sourceInclude;
import static java.lang.String.format;

import io.camunda.operate.exceptions.OperateRuntimeException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.*;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.lang.Nullable;

public interface AggregationDSL {
  static BucketSortAggregation bucketSortAggregation(
      @Nullable Integer size, SortOptions... sortOptions) {
    return BucketSortAggregation.of(a -> a.sort(Arrays.asList(sortOptions)).size(size));
  }

  static CardinalityAggregation cardinalityAggregation(String field) {
    return CardinalityAggregation.of(a -> a.field(field));
  }

  static CardinalityAggregation cardinalityAggregation(String field, int precisionThreshold) {
    return CardinalityAggregation.of(a -> a.field(field).precisionThreshold(precisionThreshold));
  }

  static CalendarInterval calendarIntervalByAlias(String alias) {
    return Arrays.stream(CalendarInterval.values())
        .filter(ci -> Arrays.asList(ci.aliases()).contains(alias))
        .findFirst()
        .orElseThrow(
            () -> {
              final List<String> legalAliases =
                  Arrays.stream(CalendarInterval.values())
                      .flatMap(v -> Arrays.stream(v.aliases()))
                      .sorted()
                      .toList();
              return new OperateRuntimeException(
                  format(
                      "Unknown CalendarInterval alias %s! Legal aliases: %s", alias, legalAliases));
            });
  }

  static DateHistogramAggregation dateHistogramAggregation(
      String field, String calendarIntervalAlias, String format, boolean keyed) {
    return DateHistogramAggregation.of(
        a ->
            a.field(field)
                .calendarInterval(calendarIntervalByAlias(calendarIntervalAlias))
                .format(format)
                .keyed(keyed));
  }

  static FiltersAggregation filtersAggregation(Map<String, Query> queries) {
    return FiltersAggregation.of(a -> a.filters(Buckets.of(b -> b.keyed(queries))));
  }

  static TermsAggregation termAggregation(String field, int size) {
    return TermsAggregation.of(a -> a.field(field).size(size));
  }

  static TermsAggregation termAggregation(String field, int size, Map<String, SortOrder> orderBy) {
    return TermsAggregation.of(a -> a.field(field).size(size).order(orderBy));
  }

  static TopHitsAggregation topHitsAggregation(
      List<String> sourceFields, int size, SortOptions... sortOptions) {
    return TopHitsAggregation.of(
        a -> a.source(sourceInclude(sourceFields)).size(size).sort(List.of(sortOptions)));
  }

  static TopHitsAggregation topHitsAggregation(int size, SortOptions... sortOptions) {
    return TopHitsAggregation.of(a -> a.size(size).sort(List.of(sortOptions)));
  }

  static Aggregation withSubaggregations(
      DateHistogramAggregation aggregation, Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.dateHistogram(aggregation).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(
      FiltersAggregation aggregation, Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.filters(aggregation).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(
      ChildrenAggregation childrenAggregation, Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.children(childrenAggregation).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(Query query, Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.filter(query).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(
      TermsAggregation aggregation, Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.terms(aggregation).aggregations(aggregations));
  }

  static ParentAggregation parent(String type) {
    return ParentAggregation.of(p -> p.type(type));
  }

  static ChildrenAggregation children(String type) {
    return ChildrenAggregation.of(c -> c.type(type));
  }
}
