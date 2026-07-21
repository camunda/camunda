/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.AuditLogLatestSuccessfulAggregation.AGGREGATION_NAME_BY_ENTITY_KEY;
import static io.camunda.search.aggregation.AuditLogLatestSuccessfulAggregation.AGGREGATION_NAME_LATEST_AUDIT_LOG;
import static io.camunda.search.aggregation.AuditLogLatestSuccessfulAggregation.AGGREGATION_SOURCE_NAME_ENTITY_KEY;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.composite;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.terms;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.topHits;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.ENTITY_KEY;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.TIMESTAMP;

import io.camunda.search.aggregation.AuditLogLatestSuccessfulAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchTopHitsAggregator.Builder;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntity;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;

public class AuditLogLatestSuccessfulAggregationTransformer
    implements AggregationTransformer<AuditLogLatestSuccessfulAggregation> {

  @Override
  public List<SearchAggregator> apply(
      final Tuple<AuditLogLatestSuccessfulAggregation, ServiceTransformers> value) {
    final Builder<AuditLogEntity> topHits = topHits();
    // Audit-log documents do not persist numeric partition and record position fields.
    // Their partition-position ID is a keyword, so it cannot chronologically break timestamp ties.
    final var latestAuditLog =
        topHits
            .name(AGGREGATION_NAME_LATEST_AUDIT_LOG)
            .sortOption(() -> List.of(new FieldSorting(TIMESTAMP, SortOrder.DESC)))
            .documentClass(AuditLogEntity.class)
            .build();
    final var entityKeySource =
        terms().name(AGGREGATION_SOURCE_NAME_ENTITY_KEY).field(ENTITY_KEY).build();

    return List.of(
        composite()
            .name(AGGREGATION_NAME_BY_ENTITY_KEY)
            .size(value.getLeft().page().size())
            .sources(List.of(entityKeySource))
            .aggregations(latestAuditLog)
            .build());
  }
}
