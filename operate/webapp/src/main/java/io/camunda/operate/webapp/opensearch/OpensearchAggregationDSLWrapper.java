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
package io.camunda.operate.webapp.opensearch;

import io.camunda.operate.store.opensearch.dsl.AggregationDSL;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.BucketSortAggregation;
import org.opensearch.client.opensearch._types.aggregations.CalendarInterval;
import org.opensearch.client.opensearch._types.aggregations.CardinalityAggregation;
import org.opensearch.client.opensearch._types.aggregations.ChildrenAggregation;
import org.opensearch.client.opensearch._types.aggregations.DateHistogramAggregation;
import org.opensearch.client.opensearch._types.aggregations.FiltersAggregation;
import org.opensearch.client.opensearch._types.aggregations.ParentAggregation;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.aggregations.TopHitsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Wrapper class around the static AggregationDSL interface. Enhances testability by allowing
 * classes to utilize the AggregationDSL class without static calls, enabling unit tests to mock
 * this out and reduce test complexity
 */
@Component
public class OpensearchAggregationDSLWrapper {

  public BucketSortAggregation bucketSortAggregation(
      @Nullable Integer size, SortOptions... sortOptions) {
    return AggregationDSL.bucketSortAggregation(size, sortOptions);
  }

  public CardinalityAggregation cardinalityAggregation(String field) {
    return AggregationDSL.cardinalityAggregation(field);
  }

  public CardinalityAggregation cardinalityAggregation(String field, int precisionThreshold) {
    return AggregationDSL.cardinalityAggregation(field, precisionThreshold);
  }

  public CalendarInterval calendarIntervalByAlias(String alias) {
    return AggregationDSL.calendarIntervalByAlias(alias);
  }

  public DateHistogramAggregation dateHistogramAggregation(
      String field, String calendarIntervalAlias, String format, boolean keyed) {
    return AggregationDSL.dateHistogramAggregation(field, calendarIntervalAlias, format, keyed);
  }

  public FiltersAggregation filtersAggregation(Map<String, Query> queries) {
    return AggregationDSL.filtersAggregation(queries);
  }

  public TermsAggregation termAggregation(String field, int size) {
    return AggregationDSL.termAggregation(field, size);
  }

  public TermsAggregation termAggregation(String field, int size, Map<String, SortOrder> orderBy) {
    return AggregationDSL.termAggregation(field, size, orderBy);
  }

  public TopHitsAggregation topHitsAggregation(
      List<String> sourceFields, int size, SortOptions... sortOptions) {
    return AggregationDSL.topHitsAggregation(sourceFields, size, sortOptions);
  }

  public TopHitsAggregation topHitsAggregation(int size, SortOptions... sortOptions) {
    return AggregationDSL.topHitsAggregation(size, sortOptions);
  }

  public Aggregation withSubaggregations(
      DateHistogramAggregation aggregation, Map<String, Aggregation> aggregations) {
    return AggregationDSL.withSubaggregations(aggregation, aggregations);
  }

  public Aggregation withSubaggregations(
      FiltersAggregation aggregation, Map<String, Aggregation> aggregations) {
    return AggregationDSL.withSubaggregations(aggregation, aggregations);
  }

  public Aggregation withSubaggregations(
      ChildrenAggregation childrenAggregation, Map<String, Aggregation> aggregations) {
    return AggregationDSL.withSubaggregations(childrenAggregation, aggregations);
  }

  public Aggregation withSubaggregations(Query query, Map<String, Aggregation> aggregations) {
    return AggregationDSL.withSubaggregations(query, aggregations);
  }

  public Aggregation withSubaggregations(
      TermsAggregation aggregation, Map<String, Aggregation> aggregations) {
    return AggregationDSL.withSubaggregations(aggregation, aggregations);
  }

  public ParentAggregation parent(String type) {
    return AggregationDSL.parent(type);
  }

  public ChildrenAggregation children(String type) {
    return AggregationDSL.children(type);
  }
}
