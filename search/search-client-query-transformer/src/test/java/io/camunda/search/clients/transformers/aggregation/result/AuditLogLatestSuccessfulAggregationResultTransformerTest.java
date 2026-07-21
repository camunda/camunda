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
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationCategory;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationResult;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationType;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditLogLatestSuccessfulAggregationResultTransformerTest {

  @Test
  void shouldExtractOneLatestAuditLogPerEntityKey() {
    final var first = document("1", "entity-1");
    final var second = document("2", "entity-2");
    final var buckets = new LinkedHashMap<String, AggregationResult>();
    buckets.put("entity-1", bucket(first));
    buckets.put("entity-2", bucket(second));

    final var result =
        new AuditLogLatestSuccessfulAggregationResultTransformer()
            .apply(Map.of(AGGREGATION_NAME_BY_ENTITY_KEY, new AggregationResult(null, buckets)));

    assertThat(result.items())
        .extracting(entity -> entity.auditLogKey(), entity -> entity.entityKey())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("1", "entity-1"),
            org.assertj.core.groups.Tuple.tuple("2", "entity-2"));
  }

  @Test
  void shouldReturnEmptyResultWhenAggregationIsMissing() {
    assertThat(new AuditLogLatestSuccessfulAggregationResultTransformer().apply(Map.of()).items())
        .isEmpty();
  }

  private static AggregationResult bucket(
      final io.camunda.webapps.schema.entities.auditlog.AuditLogEntity entity) {
    final var hit = new SearchQueryHit.Builder<>().source(entity).build();
    final var topHit = new AggregationResult.Builder().hits(List.of(hit)).build();
    return new AggregationResult(1L, Map.of(AGGREGATION_NAME_LATEST_AUDIT_LOG, topHit));
  }

  private static io.camunda.webapps.schema.entities.auditlog.AuditLogEntity document(
      final String id, final String entityKey) {
    return new io.camunda.webapps.schema.entities.auditlog.AuditLogEntity()
        .setId(id)
        .setEntityKey(entityKey)
        .setEntityType(AuditLogEntityType.USER_TASK)
        .setOperationType(AuditLogOperationType.UPDATE)
        .setTimestamp(OffsetDateTime.parse("2026-07-21T12:00:00Z"))
        .setResult(AuditLogOperationResult.SUCCESS)
        .setCategory(AuditLogOperationCategory.USER_TASKS);
  }
}
