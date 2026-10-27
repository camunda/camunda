/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.read.domain.WaitStateStatisticsDbQuery;
import io.camunda.db.rdbms.sql.WaitStateMapper;
import io.camunda.search.entities.WaitStateStatisticsEntity;
import io.camunda.search.filter.WaitStateStatisticsFilter;
import io.camunda.search.query.WaitStateStatisticsQuery;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.AuthorizationCheck;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.security.core.authz.TenantCheck;
import java.util.List;
import org.junit.jupiter.api.Test;

class WaitStateStatisticsDbReaderTest {
  private final WaitStateMapper waitStateMapper = mock(WaitStateMapper.class);
  private final WaitStateStatisticsDbReader reader =
      new WaitStateStatisticsDbReader(waitStateMapper, AbstractEntityReaderTest.TEST_CONFIG);

  @Test
  void shouldReturnEmptyListWhenAuthorizedResourceIdsIsNull() {
    final WaitStateStatisticsQuery query =
        new WaitStateStatisticsQuery(new WaitStateStatisticsFilter(123L));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                RequiredAuthorization.of(a -> a.processDefinition().readProcessInstance())),
            TenantCheck.disabled());

    final var result = reader.aggregate(query, resourceAccessChecks);

    assertThat(result).isEmpty();
    verifyNoInteractions(waitStateMapper);
  }

  @Test
  void shouldReturnEmptyListWhenAuthorizedTenantIdsIsEmpty() {
    final WaitStateStatisticsQuery query =
        new WaitStateStatisticsQuery(new WaitStateStatisticsFilter(123L));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of()));

    final var result = reader.aggregate(query, resourceAccessChecks);

    assertThat(result).isEmpty();
    verifyNoInteractions(waitStateMapper);
  }

  @Test
  void shouldPassAuthorizationFiltersToMapper() {
    final WaitStateStatisticsQuery query =
        new WaitStateStatisticsQuery(new WaitStateStatisticsFilter(123L));
    final var authorizedResourceIds = List.of("process-definition-1", "process-definition-2");
    final var authorizedTenantIds = List.of("tenant-1", "tenant-2");
    final var dbQuery =
        new WaitStateStatisticsDbQuery(query.filter(), authorizedResourceIds, authorizedTenantIds);
    final var expected =
        List.of(
            new WaitStateStatisticsEntity("task-a", 2L),
            new WaitStateStatisticsEntity("task-b", 1L));
    when(waitStateMapper.waitStateStatistics(dbQuery)).thenReturn(expected);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                RequiredAuthorization.of(
                    a ->
                        a.processDefinition()
                            .readProcessInstance()
                            .resourceIds(authorizedResourceIds))),
            TenantCheck.enabled(authorizedTenantIds));

    final var result = reader.aggregate(query, resourceAccessChecks);

    verify(waitStateMapper).waitStateStatistics(dbQuery);
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void shouldReturnEmptyListWhenNotAuthorizedForResource() {
    final WaitStateStatisticsQuery query =
        new WaitStateStatisticsQuery(new WaitStateStatisticsFilter(123L));
    final var authorizedResourceIds = List.of("unauthorized-process-definition");
    final var authorizedTenantIds = List.of("tenant-1");
    final var dbQuery =
        new WaitStateStatisticsDbQuery(query.filter(), authorizedResourceIds, authorizedTenantIds);
    when(waitStateMapper.waitStateStatistics(dbQuery)).thenReturn(List.of());
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                RequiredAuthorization.of(
                    a ->
                        a.processDefinition()
                            .readProcessInstance()
                            .resourceIds(authorizedResourceIds))),
            TenantCheck.enabled(authorizedTenantIds));

    final var result = reader.aggregate(query, resourceAccessChecks);

    verify(waitStateMapper).waitStateStatistics(dbQuery);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyListWhenNotAuthorizedForTenant() {
    final WaitStateStatisticsQuery query =
        new WaitStateStatisticsQuery(new WaitStateStatisticsFilter(123L));
    final var authorizedResourceIds = List.of("process-definition-1");
    final var authorizedTenantIds = List.of("unauthorized-tenant");
    final var dbQuery =
        new WaitStateStatisticsDbQuery(query.filter(), authorizedResourceIds, authorizedTenantIds);
    when(waitStateMapper.waitStateStatistics(dbQuery)).thenReturn(List.of());
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                RequiredAuthorization.of(
                    a ->
                        a.processDefinition()
                            .readProcessInstance()
                            .resourceIds(authorizedResourceIds))),
            TenantCheck.enabled(authorizedTenantIds));

    final var result = reader.aggregate(query, resourceAccessChecks);

    verify(waitStateMapper).waitStateStatistics(dbQuery);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnStatistics() {
    final WaitStateStatisticsQuery query =
        new WaitStateStatisticsQuery(new WaitStateStatisticsFilter(123L));
    final var dbQuery = new WaitStateStatisticsDbQuery(query.filter(), List.of(), List.of());
    final var expected =
        List.of(
            new WaitStateStatisticsEntity("task-a", 2L),
            new WaitStateStatisticsEntity("task-b", 1L));
    when(waitStateMapper.waitStateStatistics(dbQuery)).thenReturn(expected);
    final ResourceAccessChecks resourceAccessChecks = ResourceAccessChecks.disabled();

    final var result = reader.aggregate(query, resourceAccessChecks);

    verify(waitStateMapper).waitStateStatistics(dbQuery);
    assertThat(result).isEqualTo(expected);
  }
}
