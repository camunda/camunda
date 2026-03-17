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

import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
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
        new ProcessInstanceFlowNodeStatisticsQuery(
            new io.camunda.search.filter.ProcessInstanceStatisticsFilter(123L));
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
        new ProcessInstanceFlowNodeStatisticsQuery(
            new io.camunda.search.filter.ProcessInstanceStatisticsFilter(123L));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of()));

    final var result = reader.aggregate(query, resourceAccessChecks);

    assertThat(result).isEmpty();
    verifyNoInteractions(processInstanceMapper);
  }

  @Test
  void shouldPassAuthorizationFiltersToMapper() {
    final var authorizedResourceIds = List.of("process-definition-1", "process-definition-2");
    final var authorizedTenantIds = List.of("tenant-1", "tenant-2");
    final var expected =
        List.of(
            new ProcessFlowNodeStatisticsEntity("node1", 10L, 5L, 2L, 3L),
            new ProcessFlowNodeStatisticsEntity("node2", 8L, 3L, 1L, 4L));
    when(processInstanceMapper.flowNodeStatistics(123L, authorizedResourceIds, authorizedTenantIds))
        .thenReturn(expected);

    final ProcessInstanceFlowNodeStatisticsQuery query =
        new ProcessInstanceFlowNodeStatisticsQuery(
            new io.camunda.search.filter.ProcessInstanceStatisticsFilter(123L));
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

    verify(processInstanceMapper)
        .flowNodeStatistics(123L, authorizedResourceIds, authorizedTenantIds);
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void shouldReturnEmptyListWhenNotAuthorizedForResource() {
    final var authorizedResourceIds = List.of("unauthorized-process-definition");
    final var authorizedTenantIds = List.of("tenant-1");
    when(processInstanceMapper.flowNodeStatistics(123L, authorizedResourceIds, authorizedTenantIds))
        .thenReturn(List.of());

    final ProcessInstanceFlowNodeStatisticsQuery query =
        new ProcessInstanceFlowNodeStatisticsQuery(
            new io.camunda.search.filter.ProcessInstanceStatisticsFilter(123L));
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

    verify(processInstanceMapper)
        .flowNodeStatistics(123L, authorizedResourceIds, authorizedTenantIds);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyListWhenNotAuthorizedForTenant() {
    final var authorizedResourceIds = List.of("process-definition-1");
    final var authorizedTenantIds = List.of("unauthorized-tenant");
    when(processInstanceMapper.flowNodeStatistics(123L, authorizedResourceIds, authorizedTenantIds))
        .thenReturn(List.of());

    final ProcessInstanceFlowNodeStatisticsQuery query =
        new ProcessInstanceFlowNodeStatisticsQuery(
            new io.camunda.search.filter.ProcessInstanceStatisticsFilter(123L));
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

    verify(processInstanceMapper)
        .flowNodeStatistics(123L, authorizedResourceIds, authorizedTenantIds);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnStatistics() {
    final var expected =
        List.of(
            new ProcessFlowNodeStatisticsEntity("node1", 10L, 5L, 2L, 3L),
            new ProcessFlowNodeStatisticsEntity("node2", 8L, 3L, 1L, 4L));
    when(processInstanceMapper.flowNodeStatistics(123L, List.of(), null)).thenReturn(expected);

    final ProcessInstanceFlowNodeStatisticsQuery query =
        new ProcessInstanceFlowNodeStatisticsQuery(
            new io.camunda.search.filter.ProcessInstanceStatisticsFilter(123L));
    final ResourceAccessChecks resourceAccessChecks = ResourceAccessChecks.disabled();

    final var result = reader.aggregate(query, resourceAccessChecks);

    verify(processInstanceMapper).flowNodeStatistics(123L, List.of(), null);
    assertThat(result).isEqualTo(expected);
  }
}
