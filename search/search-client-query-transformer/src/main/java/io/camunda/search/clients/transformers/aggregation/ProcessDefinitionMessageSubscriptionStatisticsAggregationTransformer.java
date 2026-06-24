/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.ProcessDefinitionMessageSubscriptionStatisticsAggregation.AGGREGATION_COMPOSITE_SIZE;
import static io.camunda.search.aggregation.ProcessDefinitionMessageSubscriptionStatisticsAggregation.AGGREGATION_FIELD_BPMN_PROCESS_ID;
import static io.camunda.search.aggregation.ProcessDefinitionMessageSubscriptionStatisticsAggregation.AGGREGATION_FIELD_FLOW_NODE_INSTANCE_KEY;
import static io.camunda.search.aggregation.ProcessDefinitionMessageSubscriptionStatisticsAggregation.AGGREGATION_FIELD_PROCESS_DEFINITION_KEY;
import static io.camunda.search.aggregation.ProcessDefinitionMessageSubscriptionStatisticsAggregation.AGGREGATION_FIELD_PROCESS_INSTANCE_KEY;
import static io.camunda.search.aggregation.ProcessDefinitionMessageSubscriptionStatisticsAggregation.AGGREGATION_FIELD_TENANT_ID;
import static io.camunda.search.aggregation.ProcessDefinitionMessageSubscriptionStatisticsAggregation.AGGREGATION_NAME_ACTIVE_SUBSCRIPTIONS;
import static io.camunda.search.aggregation.ProcessDefinitionMessageSubscriptionStatisticsAggregation.AGGREGATION_NAME_BY_PROCESS_DEF_KEY_AND_TENANT_ID;
import static io.camunda.search.aggregation.ProcessDefinitionMessageSubscriptionStatisticsAggregation.AGGREGATION_NAME_PROCESS_INSTANCES_WITH_ACTIVE_SUBSCRIPTIONS;
import static io.camunda.search.aggregation.ProcessDefinitionMessageSubscriptionStatisticsAggregation.AGGREGATION_NAME_TOP_HIT;
import static io.camunda.search.aggregation.ProcessDefinitionMessageSubscriptionStatisticsAggregation.AGGREGATION_SOURCE_NAME_PROCESS_DEFINITION_KEY;
import static io.camunda.search.aggregation.ProcessDefinitionMessageSubscriptionStatisticsAggregation.AGGREGATION_SOURCE_NAME_TENANT_ID;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.cardinality;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.composite;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.terms;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.topHits;

import io.camunda.search.aggregation.ProcessDefinitionMessageSubscriptionStatisticsAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.aggregator.SearchTopHitsAggregator.Builder;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionEntity;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;
import java.util.Optional;

public class ProcessDefinitionMessageSubscriptionStatisticsAggregationTransformer
    implements AggregationTransformer<ProcessDefinitionMessageSubscriptionStatisticsAggregation> {

  @Override
  public List<SearchAggregator> apply(
      final Tuple<ProcessDefinitionMessageSubscriptionStatisticsAggregation, ServiceTransformers>
          value) {
    final var aggregation = value.getLeft();
    final var page = aggregation.page();
    final Builder<MessageSubscriptionEntity> topHits = topHits();

    // build the top_hits aggregation for processDefinitionId
    final var processDefinitionIdAgg =
        topHits
            .name(AGGREGATION_NAME_TOP_HIT)
            .fields(
                List.of(
                    AGGREGATION_FIELD_BPMN_PROCESS_ID,
                    AGGREGATION_FIELD_PROCESS_DEFINITION_KEY,
                    AGGREGATION_FIELD_TENANT_ID))
            .documentClass(MessageSubscriptionEntity.class)
            .size(1)
            .build();

    // build the cardinality aggregations
    final var activeSubscriptionsAgg =
        cardinality()
            .name(AGGREGATION_NAME_ACTIVE_SUBSCRIPTIONS)
            .field(AGGREGATION_FIELD_FLOW_NODE_INSTANCE_KEY)
            .build();

    final var processInstancesWithActiveSubscriptionsAgg =
        cardinality()
            .name(AGGREGATION_NAME_PROCESS_INSTANCES_WITH_ACTIVE_SUBSCRIPTIONS)
            .field(AGGREGATION_FIELD_PROCESS_INSTANCE_KEY)
            .build();

    // build the terms source, will group results by for processDefinitionKey
    final SearchTermsAggregator.Builder byProcessDefinitionKeyAggSourceBuilder =
        terms()
            .name(AGGREGATION_SOURCE_NAME_PROCESS_DEFINITION_KEY)
            .field(AGGREGATION_FIELD_PROCESS_DEFINITION_KEY);
    // build the terms source, will group results by for tenantId
    final SearchTermsAggregator.Builder byTenantIdAggSourceBuilder =
        terms().name(AGGREGATION_SOURCE_NAME_TENANT_ID).field(AGGREGATION_FIELD_TENANT_ID);

    final var finalAggregation =
        composite()
            .name(AGGREGATION_NAME_BY_PROCESS_DEF_KEY_AND_TENANT_ID)
            .size(
                Optional.ofNullable(page)
                    .map(SearchQueryPage::size)
                    .orElse(AGGREGATION_COMPOSITE_SIZE))
            .after(Optional.ofNullable(page).map(SearchQueryPage::after).orElse(null))
            .sources(
                List.of(
                    byProcessDefinitionKeyAggSourceBuilder.build(),
                    byTenantIdAggSourceBuilder.build()))
            .aggregations(
                processDefinitionIdAgg,
                activeSubscriptionsAgg,
                processInstancesWithActiveSubscriptionsAgg)
            .build();

    return List.of(finalAggregation);
  }
}
