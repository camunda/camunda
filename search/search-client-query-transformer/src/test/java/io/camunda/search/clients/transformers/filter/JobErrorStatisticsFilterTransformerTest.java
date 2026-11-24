/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchTermsQuery;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import io.camunda.webapps.schema.descriptors.template.JobTemplate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobErrorStatisticsFilterTransformerTest extends AbstractTransformerTest {

  @Test
  void shouldApplyAuthorizationCheck() {
    // given
    final var authorization =
        Authorization.of(
            a -> a.processDefinition().readProcessInstance().resourceIds(List.of("1", "2")));
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.enabled(authorization), TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(
            FilterBuilders.jobErrorStatistics(
                b ->
                    b.from(OffsetDateTime.now().minusDays(1))
                        .to(OffsetDateTime.now())
                        .jobType("t")),
            resourceAccessChecks);

    // then
    assertThat(searchQuery.queryOption()).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) searchQuery.queryOption()).must())
        .map(SearchQuery::queryOption)
        .filteredOn(SearchTermsQuery.class::isInstance)
        .map(SearchTermsQuery.class::cast)
        .filteredOn(t -> JobTemplate.BPMN_PROCESS_ID.equals(t.field()))
        .singleElement()
        .satisfies(
            t ->
                assertThat(t.values().stream().map(TypedValue::stringValue).toList())
                    .containsExactlyInAnyOrder("1", "2"));
  }
}
