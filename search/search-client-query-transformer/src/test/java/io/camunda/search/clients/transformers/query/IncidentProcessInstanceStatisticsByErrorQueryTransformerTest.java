/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_AFFECTED_INSTANCES;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_BY_ERROR;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_ERROR_HASH;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_SORT_AND_PAGE;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_TOTAL_ESTIMATE;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.toBucketSortField;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchBucketSortAggregator;
import io.camunda.search.clients.aggregator.SearchCardinalityAggregator;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.page.SearchQueryPage.SearchQueryResultType;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByErrorQuery;
import io.camunda.search.query.TypedSearchAggregationQuery;
import io.camunda.search.sort.IncidentProcessInstanceStatisticsByErrorSort;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import java.util.List;
import org.junit.jupiter.api.Test;

public class IncidentProcessInstanceStatisticsByErrorQueryTransformerTest {

  public static final String TENANT_ID = "tenant-1";
  private final ServiceTransformers transformers =
      ServiceTransformers.newInstance(new IndexDescriptors("", true));

  protected <Q extends TypedSearchAggregationQuery> SearchQueryRequest transformQuery(
      final Q query) {
    final var transformer = transformers.getTypedSearchQueryTransformer(query.getClass());
    return transformer.apply(query);
  }

  @Test
  public void shouldQueryByProcessDefinitionKeyAndAggregation() {
    // given
    final var query =
        IncidentProcessInstanceStatisticsByErrorQuery.of(
            q -> q.filter(f -> f.states(IncidentState.ACTIVE.name()).tenantIds(TENANT_ID)));

    // when
    final var searchRequest = transformQuery(query);

    // then
    assertThat(searchRequest.sort()).isNull();
    assertThat(searchRequest.from()).isNull();
    assertThat(searchRequest.size()).isZero();

    final var queryVariant = searchRequest.query().queryOption();

    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            boolQuery -> {
              final var must = boolQuery.must();
              assertThat(must).hasSize(2);

              assertThat(must.getFirst())
                  .isInstanceOfSatisfying(
                      SearchQuery.class,
                      stateSearchQuery -> {
                        final var stateQueryOption = stateSearchQuery.queryOption();
                        assertThat(stateQueryOption)
                            .isInstanceOfSatisfying(
                                SearchTermQuery.class,
                                stateTermQuery -> {
                                  assertThat(stateTermQuery.field())
                                      .isEqualTo(IncidentTemplate.STATE);
                                  assertThat(stateTermQuery.value().stringValue())
                                      .isEqualTo(IncidentState.ACTIVE.name());
                                });
                      });

              assertThat(must.getLast())
                  .isInstanceOfSatisfying(
                      SearchQuery.class,
                      tenantSearchQuery -> {
                        final var tenantQueryOption = tenantSearchQuery.queryOption();
                        assertThat(tenantQueryOption)
                            .isInstanceOfSatisfying(
                                SearchTermQuery.class,
                                tenantTermQuery -> {
                                  assertThat(tenantTermQuery.field())
                                      .isEqualTo(IncidentTemplate.TENANT_ID);
                                  assertThat(tenantTermQuery.value().stringValue())
                                      .isEqualTo(TENANT_ID);
                                });
                      });

              final var aggregations = searchRequest.aggregations();
              assertThat(aggregations).hasSize(2);

              assertThat(aggregations.getFirst())
                  .isInstanceOfSatisfying(
                      SearchTermsAggregator.class,
                      byErrorAgg -> {
                        assertThat(byErrorAgg.name()).isEqualTo(AGGREGATION_NAME_BY_ERROR);
                        assertThat(byErrorAgg.script()).isNotNull();

                        final var subAggs = byErrorAgg.aggregations();
                        assertThat(subAggs).hasSize(3);

                        assertThat(subAggs.get(0))
                            .isInstanceOfSatisfying(
                                SearchTermsAggregator.class,
                                errorHashAgg -> {
                                  assertThat(errorHashAgg.name())
                                      .isEqualTo(AGGREGATION_NAME_ERROR_HASH);
                                  assertThat(errorHashAgg.field())
                                      .isEqualTo(IncidentTemplate.ERROR_MSG_HASH);
                                });

                        assertThat(subAggs.get(1))
                            .isInstanceOfSatisfying(
                                SearchCardinalityAggregator.class,
                                affectedInstancesAgg -> {
                                  assertThat(affectedInstancesAgg.name())
                                      .isEqualTo(AGGREGATION_NAME_AFFECTED_INSTANCES);
                                  assertThat(affectedInstancesAgg.field())
                                      .isEqualTo(IncidentTemplate.PROCESS_INSTANCE_KEY);
                                });

                        assertThat(subAggs.get(2))
                            .isInstanceOfSatisfying(
                                SearchBucketSortAggregator.class,
                                bucketSortAgg -> {
                                  assertThat(bucketSortAgg.name())
                                      .isEqualTo(AGGREGATION_NAME_SORT_AND_PAGE);
                                  assertThat(bucketSortAgg.sorting()).isNotNull();
                                  bucketSortAgg
                                      .sorting()
                                      .forEach(
                                          s ->
                                              assertThat(s.field())
                                                  .isEqualTo(toBucketSortField(s.field())));
                                });
                      });

              assertThat(aggregations.get(1))
                  .isInstanceOfSatisfying(
                      SearchCardinalityAggregator.class,
                      totalEstimateAgg -> {
                        assertThat(totalEstimateAgg.name())
                            .isEqualTo(AGGREGATION_NAME_TOTAL_ESTIMATE);
                        assertThat(totalEstimateAgg.script()).isNotNull();
                      });
            });
  }

  @Test
  public void shouldApplyBucketSortPagingAndSorting() {
    // given
    final var from = 10;
    final var size = 25;
    final var sort =
        IncidentProcessInstanceStatisticsByErrorSort.of(
            s -> s.activeInstancesWithErrorCount().desc().errorMessage().asc());

    final var query =
        IncidentProcessInstanceStatisticsByErrorQuery.of(
            q ->
                q.filter(FilterBuilders.incident(f -> f.tenantIds(TENANT_ID)))
                    .sort(sort)
                    .page(
                        new SearchQueryPage(
                            from, size, null, null, SearchQueryResultType.PAGINATED)));

    // when
    final var searchRequest = transformQuery(query);

    // then
    assertThat(searchRequest.size()).isZero();
    assertThat(searchRequest.sort()).isNull();

    final var aggregations = searchRequest.aggregations();
    assertThat(aggregations).hasSize(2);

    final var byErrorAgg = (SearchTermsAggregator) aggregations.getFirst();
    final List<SearchAggregator> subAggs = byErrorAgg.aggregations();

    final var bucketSortAgg = (SearchBucketSortAggregator) subAggs.get(2);
    assertThat(bucketSortAgg.name()).isEqualTo(AGGREGATION_NAME_SORT_AND_PAGE);
    assertThat(bucketSortAgg.from()).isEqualTo(from);
    assertThat(bucketSortAgg.size()).isEqualTo(size);

    assertThat(bucketSortAgg.sorting())
        .containsExactly(
            new FieldSorting(toBucketSortField("activeInstancesWithErrorCount"), SortOrder.DESC),
            new FieldSorting(toBucketSortField("errorMessage"), SortOrder.ASC));
  }
}
