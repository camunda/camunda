/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.ProcessDefinitionMessageSubscriptionStatisticsAggregation.AGGREGATION_NAME_ACTIVE_SUBSCRIPTIONS;
import static io.camunda.search.aggregation.ProcessDefinitionMessageSubscriptionStatisticsAggregation.AGGREGATION_NAME_BY_PROCESS_DEF_KEY_AND_TENANT_ID;
import static io.camunda.search.aggregation.ProcessDefinitionMessageSubscriptionStatisticsAggregation.AGGREGATION_NAME_PROCESS_INSTANCES_WITH_ACTIVE_SUBSCRIPTIONS;
import static io.camunda.search.aggregation.ProcessDefinitionMessageSubscriptionStatisticsAggregation.AGGREGATION_NAME_TOP_HIT;

import io.camunda.search.aggregation.result.ProcessDefinitionMessageSubscriptionStatisticsAggregationResult;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.entities.ProcessDefinitionMessageSubscriptionStatisticsEntity;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionEntity;
import java.util.Map;

public class ProcessDefinitionMessageSubscriptionStatisticsAggregationResultTransformer
    implements AggregationResultTransformer<
        ProcessDefinitionMessageSubscriptionStatisticsAggregationResult> {

  @Override
  public ProcessDefinitionMessageSubscriptionStatisticsAggregationResult apply(
      final Map<String, AggregationResult> aggregations) {
    return new ProcessDefinitionMessageSubscriptionStatisticsAggregationResult(
        aggregations
            .get(AGGREGATION_NAME_BY_PROCESS_DEF_KEY_AND_TENANT_ID)
            .aggregations()
            .values()
            .stream()
            .map(
                aggregationResult -> {
                  final var topHit = aggregationResult.aggregations().get(AGGREGATION_NAME_TOP_HIT);
                  final var firstHit = topHit.hits().getFirst();
                  final var source = (MessageSubscriptionEntity) firstHit.source();
                  final var processDefinitionId = source.getBpmnProcessId();
                  final var processDefinitionKey = source.getProcessDefinitionKey();

                  final var activeSubscriptions =
                      aggregationResult
                          .aggregations()
                          .get(AGGREGATION_NAME_ACTIVE_SUBSCRIPTIONS)
                          .docCount();

                  final var processInstancesWithActiveSubscriptions =
                      aggregationResult
                          .aggregations()
                          .get(AGGREGATION_NAME_PROCESS_INSTANCES_WITH_ACTIVE_SUBSCRIPTIONS)
                          .docCount();

                  return new ProcessDefinitionMessageSubscriptionStatisticsEntity(
                      processDefinitionId,
                      source.getTenantId(),
                      processDefinitionKey,
                      processInstancesWithActiveSubscriptions,
                      activeSubscriptions);
                })
            .toList(),
        aggregations.get(AGGREGATION_NAME_BY_PROCESS_DEF_KEY_AND_TENANT_ID).endCursor());
  }
}
