/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation.AGGREGATION_NAME_AFFECTED_INSTANCES;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation.AGGREGATION_NAME_BY_DEFINITION;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation.AGGREGATION_NAME_SORT_AND_PAGE;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation.AGGREGATION_NAME_TOTAL_ESTIMATE;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation.toBucketSortField;
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
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.page.SearchQueryPage.SearchQueryResultType;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByDefinitionQuery;
import io.camunda.search.query.TypedSearchAggregationQuery;
import io.camunda.search.sort.IncidentProcessInstanceStatisticsByDefinitionSort;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import java.util.List;
import org.junit.jupiter.api.Test;

public class IncidentProcessInstanceStatisticsByDefinitionQueryTransformerTest {

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
        IncidentProcessInstanceStatisticsByDefinitionQuery.of(
            q ->
                q.filter(
                    f -> f.state(IncidentState.ACTIVE.name()).errorHashCode(123)));

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
                      hashSearchQuery -> {
                        final var hashQueryOption = hashSearchQuery.queryOption();
                        assertThat(hashQueryOption)
                            .isInstanceOfSatisfying(
                                SearchTermQuery.class,
                                hashTermQuery -> {
                                  assertThat(hashTermQuery.field())
                                      .isEqualTo(IncidentTemplate.ERROR_MSG_HASH);
                                  assertThat(hashTermQuery.value().intValue()).isEqualTo(123);
                                });
                      });

              final var aggregations = searchRequest.aggregations();
              assertThat(aggregations).hasSize(2);

              assertThat(aggregations.getFirst())
                  .isInstanceOfSatisfying(
                      SearchTermsAggregator.class,
                      byDefinitionAgg -> {
                        assertThat(byDefinitionAgg.name())
                            .isEqualTo(AGGREGATION_NAME_BY_DEFINITION);
                        assertThat(byDefinitionAgg.field())
                            .isEqualTo(IncidentTemplate.PROCESS_DEFINITION_KEY);
                        assertThat(byDefinitionAgg.script()).isNull();
                        assertThat(byDefinitionAgg.lang()).isNull();

                        final var subAggs = byDefinitionAgg.aggregations();
                        assertThat(subAggs).hasSize(2);

                        assertThat(subAggs.getFirst())
                            .isInstanceOfSatisfying(
                                SearchCardinalityAggregator.class,
                                affectedInstancesAgg -> {
                                  assertThat(affectedInstancesAgg.name())
                                      .isEqualTo(AGGREGATION_NAME_AFFECTED_INSTANCES);
                                  assertThat(affectedInstancesAgg.field())
                                      .isEqualTo(IncidentTemplate.PROCESS_INSTANCE_KEY);
                                });

                        assertThat(subAggs.getLast())
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

              assertThat(aggregations.getLast())
                  .isInstanceOfSatisfying(
                      SearchCardinalityAggregator.class,
                      totalEstimateAgg -> {
                        assertThat(totalEstimateAgg.name())
                            .isEqualTo(AGGREGATION_NAME_TOTAL_ESTIMATE);
                        assertThat(totalEstimateAgg.field())
                            .isEqualTo(IncidentTemplate.PROCESS_DEFINITION_KEY);
                        assertThat(totalEstimateAgg.script()).isNull();
                        assertThat(totalEstimateAgg.lang()).isNull();
                      });
            });
  }

  @Test
  public void shouldApplyBucketSortPagingAndSorting() {
    // given
    final var from = 10;
    final var size = 25;
    final var sort =
        IncidentProcessInstanceStatisticsByDefinitionSort.of(
            s ->
                s.activeInstancesWithErrorCount()
                    .desc()
                    .processDefinitionKey()
                    .asc()
                    .tenantId()
                    .asc());

    final var query =
        IncidentProcessInstanceStatisticsByDefinitionQuery.of(
            q ->
                q.filter(
                        f -> f.state(IncidentState.ACTIVE.name()).errorHashCode(123))
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

    final var byDefinitionAgg = (SearchTermsAggregator) aggregations.getFirst();
    assertThat(byDefinitionAgg.field()).isEqualTo(IncidentTemplate.PROCESS_DEFINITION_KEY);
    final List<SearchAggregator> subAggs = byDefinitionAgg.aggregations();

    final var bucketSortAgg = (SearchBucketSortAggregator) subAggs.getLast();
    assertThat(bucketSortAgg.name()).isEqualTo(AGGREGATION_NAME_SORT_AND_PAGE);
    assertThat(bucketSortAgg.from()).isEqualTo(from);
    assertThat(bucketSortAgg.size()).isEqualTo(size);

    assertThat(bucketSortAgg.sorting())
        .containsExactly(
            new FieldSorting(toBucketSortField("activeInstancesWithErrorCount"), SortOrder.DESC),
            new FieldSorting(toBucketSortField("processDefinitionKey"), SortOrder.ASC),
            new FieldSorting(toBucketSortField("tenantId"), SortOrder.ASC));
  }
}
