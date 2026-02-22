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

import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByDefinitionEntity;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByDefinitionQuery;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncidentProcessInstanceStatisticsByDefinitionDbReaderTest {

  private final IncidentMapper incidentMapper = mock(IncidentMapper.class);
  private final IncidentProcessInstanceStatisticsByDefinitionDbReader reader =
      new IncidentProcessInstanceStatisticsByDefinitionDbReader(
          incidentMapper, AbstractEntityReaderTest.TEST_CONFIG);

  @Test
  void shouldReturnEmptyListWhenAuthorizedResourceIdsIsNull() {
    final var query =
        IncidentProcessInstanceStatisticsByDefinitionQuery.of(
            b -> b.filter(f -> f.errorHashCode(123)));

    final var resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                Authorization.of(a -> a.processDefinition().readProcessInstance())),
            TenantCheck.disabled());

    final var result = reader.aggregate(query, resourceAccessChecks);

    assertThat(result.items()).isEmpty();
    assertThat(result.total()).isZero();
    verify(incidentMapper, times(0)).processInstanceStatisticsByDefinitionCount(any());
    verify(incidentMapper, times(0)).processInstanceStatisticsByDefinition(any());
  }

  @Test
  void shouldReturnEmptyListWhenAuthorizedTenantIdsIsNull() {
    final var query =
        IncidentProcessInstanceStatisticsByDefinitionQuery.of(
            b -> b.filter(f -> f.errorHashCode(123)));

    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of()));

    final var result = reader.aggregate(query, resourceAccessChecks);

    assertThat(result.items()).isEmpty();
    assertThat(result.total()).isZero();
    verify(incidentMapper, times(0)).processInstanceStatisticsByDefinitionCount(any());
    verify(incidentMapper, times(0)).processInstanceStatisticsByDefinition(any());
  }

  @Test
  void shouldReturnEmptyPageWhenPageSizeIsZero() {
    when(incidentMapper.processInstanceStatisticsByDefinitionCount(any())).thenReturn(21L);

    final var query =
        IncidentProcessInstanceStatisticsByDefinitionQuery.of(
            b -> b.filter(f -> f.errorHashCode(123)).page(p -> p.size(0)));
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    final var result = reader.aggregate(query, resourceAccessChecks);

    assertThat(result.total()).isEqualTo(21L);
    assertThat(result.items()).isEmpty();
    verify(incidentMapper, times(0)).processInstanceStatisticsByDefinition(any());
  }

  @Test
  void shouldReturnItemsFromMapper() {
    final var entities =
        List.of(
            new IncidentProcessInstanceStatisticsByDefinitionEntity(
                null, 1L, null, null, "t1", 5L));

    when(incidentMapper.processInstanceStatisticsByDefinitionCount(any())).thenReturn(1L);
    when(incidentMapper.processInstanceStatisticsByDefinition(any())).thenReturn(entities);

    final var query =
        IncidentProcessInstanceStatisticsByDefinitionQuery.of(
            b -> b.filter(f -> f.errorHashCode(123)).page(p -> p.size(10)));
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    final var result = reader.aggregate(query, resourceAccessChecks);

    assertThat(result.total()).isEqualTo(1L);
    assertThat(result.items()).containsExactlyElementsOf(entities);
    verify(incidentMapper, times(1)).processInstanceStatisticsByDefinitionCount(any());
    verify(incidentMapper, times(1)).processInstanceStatisticsByDefinition(any());
  }
}
