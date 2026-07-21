/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.AuditLogLatestSuccessfulAggregation.AGGREGATION_NAME_BY_ENTITY_KEY;
import static io.camunda.search.aggregation.AuditLogLatestSuccessfulAggregation.AGGREGATION_NAME_LATEST_AUDIT_LOG;

import io.camunda.search.aggregation.result.AuditLogLatestSuccessfulAggregationResult;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.transformers.entity.AuditLogEntityTransformer;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntity;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AuditLogLatestSuccessfulAggregationResultTransformer
    implements AggregationResultTransformer<AuditLogLatestSuccessfulAggregationResult> {

  @Override
  public AuditLogLatestSuccessfulAggregationResult apply(
      final Map<String, AggregationResult> aggregations) {
    final var byEntityKey = aggregations.get(AGGREGATION_NAME_BY_ENTITY_KEY);
    if (byEntityKey == null || byEntityKey.aggregations() == null) {
      return new AuditLogLatestSuccessfulAggregationResult(List.of());
    }

    final var entityTransformer = new AuditLogEntityTransformer();
    final var items =
        byEntityKey.aggregations().values().stream()
            .map(AggregationResult::aggregations)
            .filter(Objects::nonNull)
            .map(bucketAggregations -> bucketAggregations.get(AGGREGATION_NAME_LATEST_AUDIT_LOG))
            .filter(Objects::nonNull)
            .flatMap(latest -> latest.hits().stream())
            .map(SearchQueryHit::source)
            .filter(AuditLogEntity.class::isInstance)
            .map(AuditLogEntity.class::cast)
            .map(entityTransformer::apply)
            .toList();
    return new AuditLogLatestSuccessfulAggregationResult(items);
  }
}
