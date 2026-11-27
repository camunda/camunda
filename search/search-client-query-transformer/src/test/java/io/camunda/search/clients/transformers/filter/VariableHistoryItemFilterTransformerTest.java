/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchTermsQuery;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.AuthorizationCheck;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.security.core.authz.TenantCheck;
import io.camunda.webapps.schema.descriptors.template.VariableHistoryTemplate;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariableHistoryItemFilterTransformerTest extends AbstractTransformerTest {

  @Test
  void shouldQueryProcessDefinitionIdForTheReadAuthorization() {
    // given
    final var authorization =
        RequiredAuthorization.of(
            a -> a.processDefinition().readProcessInstance().resourceIds(List.of("P1", "P2")));
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.enabled(authorization), TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.variableHistoryItem(b -> b), resourceAccessChecks);

    // then
    assertThat(searchQuery.queryOption())
        .isInstanceOfSatisfying(
            SearchTermsQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo(VariableHistoryTemplate.PROCESS_DEFINITION_ID);
              assertThat(t.values().stream().map(TypedValue::stringValue).toList())
                  .containsExactlyInAnyOrder("P1", "P2");
            });
  }

  @Test
  void shouldQueryTenantIdForTheTenantCheck() {
    // given
    final var resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.disabled(), TenantCheck.enabled(List.of("A", "B")));

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.variableHistoryItem(b -> b), resourceAccessChecks);

    // then
    assertThat(searchQuery.queryOption())
        .isInstanceOfSatisfying(
            SearchTermsQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo(VariableHistoryTemplate.TENANT_ID);
              assertThat(t.values().stream().map(TypedValue::stringValue).toList())
                  .containsExactlyInAnyOrder("A", "B");
            });
  }
}
