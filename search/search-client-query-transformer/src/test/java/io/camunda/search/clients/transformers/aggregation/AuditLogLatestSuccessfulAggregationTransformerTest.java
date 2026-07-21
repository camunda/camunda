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
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.ENTITY_KEY;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.TIMESTAMP;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.aggregation.AuditLogLatestSuccessfulAggregation;
import io.camunda.search.aggregation.result.AuditLogLatestSuccessfulAggregationResult;
import io.camunda.search.clients.aggregator.SearchCompositeAggregator;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.aggregator.SearchTopHitsAggregator;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.clients.transformers.aggregation.result.AuditLogLatestSuccessfulAggregationResultTransformer;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AuditLogLatestSuccessfulQuery;
import io.camunda.search.sort.SortOrder;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntity;
import io.camunda.zeebe.util.collection.Tuple;
import org.junit.jupiter.api.Test;

class AuditLogLatestSuccessfulAggregationTransformerTest {

  private final ServiceTransformers transformers =
      ServiceTransformers.newInstance(new IndexDescriptors("", true));

  @Test
  void shouldRegisterQueryAggregationAndResultTransformers() {
    assertThat(transformers.getTypedSearchQueryTransformer(AuditLogLatestSuccessfulQuery.class))
        .isNotNull();
    assertThat(transformers.getAggregationTransformer(AuditLogLatestSuccessfulAggregation.class))
        .isInstanceOf(AuditLogLatestSuccessfulAggregationTransformer.class);
    assertThat(
            transformers.getSearchAggregationResultTransformer(
                AuditLogLatestSuccessfulAggregationResult.class))
        .isInstanceOf(AuditLogLatestSuccessfulAggregationResultTransformer.class);
  }

  @Test
  void shouldGroupByPhysicalEntityKeyAndSelectLatestAuditLog() {
    final var aggregation =
        new AuditLogLatestSuccessfulAggregation(
            FilterBuilders.auditLog().build(), SearchQueryPage.of(b -> b.size(3)));

    final var result =
        new AuditLogLatestSuccessfulAggregationTransformer()
            .apply(Tuple.of(aggregation, transformers));

    assertThat(result).singleElement().isInstanceOf(SearchCompositeAggregator.class);
    final var composite = (SearchCompositeAggregator) result.getFirst();
    assertThat(composite.name()).isEqualTo(AGGREGATION_NAME_BY_ENTITY_KEY);
    assertThat(composite.size()).isEqualTo(3);
    assertThat(composite.sources())
        .singleElement()
        .isInstanceOfSatisfying(
            SearchTermsAggregator.class,
            source -> {
              assertThat(source.name()).isEqualTo(AGGREGATION_SOURCE_NAME_ENTITY_KEY);
              assertThat(source.field()).isEqualTo(ENTITY_KEY);
            });
    assertThat(composite.aggregations())
        .singleElement()
        .isInstanceOfSatisfying(
            SearchTopHitsAggregator.class,
            topHits -> {
              assertThat(topHits.name()).isEqualTo(AGGREGATION_NAME_LATEST_AUDIT_LOG);
              assertThat(topHits.size()).isEqualTo(1);
              assertThat(topHits.documentClass()).isEqualTo(AuditLogEntity.class);
              assertThat(topHits.sortOption().getFieldSortings())
                  .extracting(sorting -> sorting.field(), sorting -> sorting.order())
                  .containsExactly(org.assertj.core.groups.Tuple.tuple(TIMESTAMP, SortOrder.DESC));
            });
  }
}
