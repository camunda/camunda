/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.DecisionDefinitionLatestVersionAggregation.AGGREGATION_GROUP_DECISION_ID;
import static io.camunda.search.aggregation.DecisionDefinitionLatestVersionAggregation.AGGREGATION_GROUP_TENANT_ID;
import static io.camunda.search.aggregation.DecisionDefinitionLatestVersionAggregation.AGGREGATION_NAME_BY_DECISION_ID;
import static io.camunda.search.aggregation.DecisionDefinitionLatestVersionAggregation.AGGREGATION_NAME_LATEST_DEFINITION;
import static io.camunda.search.aggregation.DecisionDefinitionLatestVersionAggregation.AGGREGATION_SOURCE_NAME_DECISION_ID;
import static io.camunda.search.aggregation.DecisionDefinitionLatestVersionAggregation.AGGREGATION_SOURCE_NAME_TENANT_ID;
import static io.camunda.search.aggregation.DecisionDefinitionLatestVersionAggregation.AGGREGATION_TERMS_SIZE;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.composite;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.terms;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.topHits;

import io.camunda.search.aggregation.DecisionDefinitionLatestVersionAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.aggregator.SearchTopHitsAggregator;
import io.camunda.search.clients.aggregator.SearchTopHitsAggregator.Builder;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.DecisionDefinitionSort;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;
import java.util.Optional;

public class DecisionDefinitionLatestVersionAggregationTransformer
    implements AggregationTransformer<DecisionDefinitionLatestVersionAggregation> {

  @Override
  public List<SearchAggregator> apply(
      final Tuple<DecisionDefinitionLatestVersionAggregation, ServiceTransformers> value) {
    final var aggregation = value.getLeft();
    final var transformers = value.getRight();
    final var page = aggregation.page();
    final var sort = aggregation.sort();
    final Builder<DecisionDefinitionEntity> topHits = topHits();

    // get the MAX version per (decisionId, tenantId)
    final SearchTopHitsAggregator<DecisionDefinitionEntity> maxVersionsAgg =
        topHits
            .name(AGGREGATION_NAME_LATEST_DEFINITION)
            .sortOption(new DecisionDefinitionSort.Builder().version().desc().build())
            .documentClass(DecisionDefinitionEntity.class)
            .build();

    // aggregate terms by decision id
    final SearchTermsAggregator.Builder byDecisionIdAggSourceBuilder =
        terms().name(AGGREGATION_SOURCE_NAME_DECISION_ID).field(AGGREGATION_GROUP_DECISION_ID);
    Optional.ofNullable(sort)
        .map(findSortOptionFor(AGGREGATION_GROUP_DECISION_ID, transformers))
        .ifPresent(byDecisionIdAggSourceBuilder::sorting);

    // aggregate terms by tenant id
    final SearchTermsAggregator.Builder byTenantIdAggSourceBuilder =
        terms().name(AGGREGATION_SOURCE_NAME_TENANT_ID).field(AGGREGATION_GROUP_TENANT_ID);
    Optional.ofNullable(sort)
        .map(findSortOptionFor(AGGREGATION_GROUP_TENANT_ID, transformers))
        .ifPresent(byTenantIdAggSourceBuilder::sorting);

    final var finalAggregation =
        composite()
            .name(AGGREGATION_NAME_BY_DECISION_ID)
            .size(
                Optional.ofNullable(page).map(SearchQueryPage::size).orElse(AGGREGATION_TERMS_SIZE))
            .after(Optional.ofNullable(page).map(SearchQueryPage::after).orElse(null))
            .sources(
                List.of(byDecisionIdAggSourceBuilder.build(), byTenantIdAggSourceBuilder.build()))
            .aggregations(maxVersionsAgg)
            .build();

    return List.of(finalAggregation);
  }
}

