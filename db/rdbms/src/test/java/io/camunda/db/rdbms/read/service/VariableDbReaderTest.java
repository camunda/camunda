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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
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
            AuthorizationCheck.enabled(Authorization.of(a -> a.readProcessInstance().read())),
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

  @Test
  void shouldDeduplicateByNameAndKeepClosestScope() {
    when(variableMapper.search(any()))
        .thenReturn(
            List.of(
                new VariableEntity(1L, "vehicleModel", "\"X5\"", "\"X5\"", false, 1L, 11L, 11L, "pd", "t1"),
                new VariableEntity(
                    2L,
                    "vehicleModel",
                    "\"X5 M Competition\"",
                    "\"X5 M Competition\"",
                    false,
                    3L,
                    11L,
                    11L,
                    "pd",
                    "t1"),
                new VariableEntity(
                    3L,
                    "instructions",
                    "\"Use official lookup\"",
                    "\"Use official lookup\"",
                    false,
                    3L,
                    11L,
                    11L,
                    "pd",
                    "t1")));

    final VariableQuery query =
        VariableQuery.of(
            b ->
                b.filter(f -> f.scopeKeys(List.of(1L, 2L, 3L)))
                    .sort(s -> s.name().asc())
                    .page(p -> p.from(0).size(10)));

    final var result = variableDbReader.search(query, ResourceAccessChecks.disabled());

    assertThat(result.total()).isEqualTo(2L);
    assertThat(result.items())
        .extracting(VariableEntity::name)
        .containsExactly("instructions", "vehicleModel");
    assertThat(result.items().get(1).scopeKey()).isEqualTo(3L);
  }
}
