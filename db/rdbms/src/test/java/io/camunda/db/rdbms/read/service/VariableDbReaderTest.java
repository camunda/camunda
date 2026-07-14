/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.read.domain.VariableNameDbQuery;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.search.query.VariableNameQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.AuthorizationCheck;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.security.core.authz.TenantCheck;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariableDbReaderTest {
  private final VariableMapper variableMapper = mock(VariableMapper.class);
  private final VariableDbReader variableDbReader =
      new VariableDbReader(variableMapper, AbstractEntityReaderTest.TEST_CONFIG);

  @Test
  void shouldReturnEmptyListWhenAuthorizedResourceIdsIsNull() {
    final VariableQuery query = VariableQuery.of(b -> b);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                RequiredAuthorization.of(a -> a.readProcessInstance().read())),
            TenantCheck.disabled());

    final var items = variableDbReader.search(query, resourceAccessChecks).items();
    assertThat(items).isEmpty();
  }

  @Test
  void shouldReturnEmptyListWhenAuthorizedTenantIdsIsNull() {
    final VariableQuery query = VariableQuery.of(b -> b);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of()));

    final var items = variableDbReader.search(query, resourceAccessChecks).items();
    assertThat(items).isEmpty();
  }

  @Test
  void shouldReturnEmptyPageWhenPageSizeIsZero() {
    when(variableMapper.count(any())).thenReturn(21L);

    final VariableQuery query = VariableQuery.of(b -> b.page(p -> p.size(0)));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    final var result = variableDbReader.search(query, resourceAccessChecks);

    assertThat(result.total()).isEqualTo(21L);
    assertThat(result.items()).isEmpty();
    verify(variableMapper, times(0)).search(any());
  }

  // ===== searchVariableNames =====

  @Test
  void shouldReturnEmptyListWhenNoProcessDefinitionKeyGiven() {
    final VariableNameQuery query = VariableNameQuery.of(b -> b);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    final var names = variableDbReader.searchVariableNames(query, resourceAccessChecks);

    assertThat(names).isEmpty();
    verify(variableMapper, never()).findVariableNames(any());
  }

  @Test
  void shouldReturnEmptyListWhenAuthorizedResourceIdsIsNullForNameSearch() {
    final VariableNameQuery query =
        VariableNameQuery.of(b -> b.filter(f -> f.processDefinitionKeys(1L)));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                RequiredAuthorization.of(a -> a.processDefinition().readProcessInstance())),
            TenantCheck.disabled());

    final var names = variableDbReader.searchVariableNames(query, resourceAccessChecks);

    assertThat(names).isEmpty();
    verify(variableMapper, never()).findVariableNames(any());
  }

  @Test
  void shouldInjectAuthorizedProcessDefinitionIdsIntoNameQuery() {
    final var authorizedIds = List.of("process-a", "process-b");
    when(variableMapper.findVariableNames(any())).thenReturn(List.of("amount", "total"));

    final VariableNameQuery query =
        VariableNameQuery.of(b -> b.filter(f -> f.processDefinitionKeys(1L)).page(p -> p.size(5)));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                RequiredAuthorization.of(
                    a -> a.processDefinition().readProcessInstance().resourceIds(authorizedIds))),
            TenantCheck.disabled());

    final var names = variableDbReader.searchVariableNames(query, resourceAccessChecks);

    assertThat(names).containsExactly("amount", "total");
    verify(variableMapper)
        .findVariableNames(
            argThat(
                (VariableNameDbQuery q) ->
                    q.authorizedResourceIds().equals(authorizedIds) && q.limit() == 5));
  }
}
