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

import io.camunda.db.rdbms.read.domain.ProcessInstanceStatisticsDbQuery;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.filter.ProcessInstanceStatisticsFilter;
import io.camunda.search.query.ProcessInstanceFlowNodeStatisticsQuery;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessInstanceStatisticsDbReaderTest {
  private final ProcessInstanceMapper processInstanceMapper = mock(ProcessInstanceMapper.class);
  private final ProcessInstanceStatisticsDbReader reader =
      new ProcessInstanceStatisticsDbReader(
          processInstanceMapper, AbstractEntityReaderTest.TEST_CONFIG);

  @Test
  void shouldReturnEmptyListWhenAuthorizedResourceIdsIsNull() {
    final ProcessInstanceFlowNodeStatisticsQuery query =
        new ProcessInstanceFlowNodeStatisticsQuery(new ProcessInstanceStatisticsFilter(123L));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                Authorization.of(a -> a.processDefinition().readProcessInstance())),
            TenantCheck.disabled());

    final var result = reader.aggregate(query, resourceAccessChecks);

    assertThat(result).isEmpty();
    verifyNoInteractions(processInstanceMapper);
  }

  @Test
  void shouldReturnEmptyListWhenAuthorizedTenantIdsIsEmpty() {
    final ProcessInstanceFlowNodeStatisticsQuery query =
        new ProcessInstanceFlowNodeStatisticsQuery(new ProcessInstanceStatisticsFilter(123L));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of()));

    final var result = reader.aggregate(query, resourceAccessChecks);

    assertThat(result).isEmpty();
    verifyNoInteractions(processInstanceMapper);
  }

  @Test
  void shouldPassAuthorizationFiltersToMapper() {
    final ProcessInstanceFlowNodeStatisticsQuery query =
        new ProcessInstanceFlowNodeStatisticsQuery(new ProcessInstanceStatisticsFilter(123L));
    final var authorizedResourceIds = List.of("process-definition-1", "process-definition-2");
    final var authorizedTenantIds = List.of("tenant-1", "tenant-2");
    final var dbQuery =
        new ProcessInstanceStatisticsDbQuery(
            query.filter(), authorizedResourceIds, authorizedTenantIds);
    final var expected =
        List.of(
            new ProcessFlowNodeStatisticsEntity("node1", 10L, 5L, 2L, 3L),
            new ProcessFlowNodeStatisticsEntity("node2", 8L, 3L, 1L, 4L));
    when(processInstanceMapper.flowNodeStatistics(dbQuery)).thenReturn(expected);
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                Authorization.of(
                    a ->
                        a.processDefinition()
                            .readProcessInstance()
                            .resourceIds(authorizedResourceIds))),
            TenantCheck.enabled(authorizedTenantIds));

    final var result = reader.aggregate(query, resourceAccessChecks);

    verify(processInstanceMapper).flowNodeStatistics(dbQuery);
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void shouldReturnEmptyListWhenNotAuthorizedForResource() {
    final ProcessInstanceFlowNodeStatisticsQuery query =
        new ProcessInstanceFlowNodeStatisticsQuery(new ProcessInstanceStatisticsFilter(123L));
    final var authorizedResourceIds = List.of("unauthorized-process-definition");
    final var authorizedTenantIds = List.of("tenant-1");
    final var dbQuery =
        new ProcessInstanceStatisticsDbQuery(
            query.filter(), authorizedResourceIds, authorizedTenantIds);
    when(processInstanceMapper.flowNodeStatistics(dbQuery)).thenReturn(List.of());
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                Authorization.of(
                    a ->
                        a.processDefinition()
                            .readProcessInstance()
                            .resourceIds(authorizedResourceIds))),
            TenantCheck.enabled(authorizedTenantIds));

    final var result = reader.aggregate(query, resourceAccessChecks);

    verify(processInstanceMapper).flowNodeStatistics(dbQuery);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyListWhenNotAuthorizedForTenant() {
    final ProcessInstanceFlowNodeStatisticsQuery query =
        new ProcessInstanceFlowNodeStatisticsQuery(new ProcessInstanceStatisticsFilter(123L));
    final var authorizedResourceIds = List.of("process-definition-1");
    final var authorizedTenantIds = List.of("unauthorized-tenant");
    final var dbQuery =
        new ProcessInstanceStatisticsDbQuery(
            query.filter(), authorizedResourceIds, authorizedTenantIds);
    when(processInstanceMapper.flowNodeStatistics(dbQuery)).thenReturn(List.of());
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                Authorization.of(
                    a ->
                        a.processDefinition()
                            .readProcessInstance()
                            .resourceIds(authorizedResourceIds))),
            TenantCheck.enabled(authorizedTenantIds));

    final var result = reader.aggregate(query, resourceAccessChecks);

    verify(processInstanceMapper).flowNodeStatistics(dbQuery);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnStatistics() {
    final ProcessInstanceFlowNodeStatisticsQuery query =
        new ProcessInstanceFlowNodeStatisticsQuery(new ProcessInstanceStatisticsFilter(123L));
    final var dbQuery = new ProcessInstanceStatisticsDbQuery(query.filter(), List.of(), null);
    final var expected =
        List.of(
            new ProcessFlowNodeStatisticsEntity("node1", 10L, 5L, 2L, 3L),
            new ProcessFlowNodeStatisticsEntity("node2", 8L, 3L, 1L, 4L));
    when(processInstanceMapper.flowNodeStatistics(dbQuery)).thenReturn(expected);
    final ResourceAccessChecks resourceAccessChecks = ResourceAccessChecks.disabled();

    final var result = reader.aggregate(query, resourceAccessChecks);

    verify(processInstanceMapper).flowNodeStatistics(dbQuery);
    assertThat(result).isEqualTo(expected);
  }
}
