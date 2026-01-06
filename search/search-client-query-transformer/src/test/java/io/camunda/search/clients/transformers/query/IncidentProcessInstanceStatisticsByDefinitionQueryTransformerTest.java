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
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation.AGGREGATION_NAME_BY_TENANT;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation.AGGREGATION_NAME_SORT_AND_PAGE;
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
import io.camunda.search.filter.FilterBuilders;
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

  public static final String TENANT_ID = "tenantId";

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
              assertThat(aggregations).hasSize(1);

              final var byDefinitionAgg = (SearchTermsAggregator) aggregations.getFirst();
              assertThat(byDefinitionAgg.name()).isEqualTo(AGGREGATION_NAME_BY_DEFINITION);
              assertThat(byDefinitionAgg.field())
                  .isEqualTo(IncidentTemplate.PROCESS_DEFINITION_KEY);

              final var subAggs = byDefinitionAgg.aggregations();
              assertThat(subAggs)
                  .extracting(SearchAggregator::getName)
                  .containsExactlyInAnyOrder(
                      AGGREGATION_NAME_BY_TENANT, AGGREGATION_NAME_SORT_AND_PAGE);

              final var bucketSortAgg =
                  subAggs.stream()
                      .filter(a -> a.getName().equals(AGGREGATION_NAME_SORT_AND_PAGE))
                      .findFirst()
                      .map(SearchBucketSortAggregator.class::cast)
                      .orElseThrow();
              assertThat(bucketSortAgg.sorting()).isNotNull();
              bucketSortAgg
                  .sorting()
                  .forEach(s -> assertThat(s.field()).isEqualTo(toBucketSortField(s.field())));

              final var byTenantAgg =
                  subAggs.stream()
                      .filter(a -> a.getName().equals(AGGREGATION_NAME_BY_TENANT))
                      .findFirst()
                      .map(SearchTermsAggregator.class::cast)
                      .orElseThrow();
              assertThat(byTenantAgg.field()).isEqualTo(TENANT_ID);
              assertThat(byTenantAgg.aggregations())
                  .singleElement()
                  .isInstanceOfSatisfying(
                      SearchCardinalityAggregator.class,
                      affectedInstancesAgg -> {
                        assertThat(affectedInstancesAgg.name())
                            .isEqualTo(AGGREGATION_NAME_AFFECTED_INSTANCES);
                        assertThat(affectedInstancesAgg.field())
                            .isEqualTo(IncidentTemplate.PROCESS_INSTANCE_KEY);
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
    assertThat(aggregations).hasSize(1);

    final var byDefinitionAgg = (SearchTermsAggregator) aggregations.getFirst();
    final List<SearchAggregator> subAggs = byDefinitionAgg.aggregations();

    final var bucketSortAgg =
        (SearchBucketSortAggregator)
            subAggs.stream()
                .filter(a -> a.getName().equals(AGGREGATION_NAME_SORT_AND_PAGE))
                .findFirst()
                .orElseThrow();

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
