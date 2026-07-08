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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.sql.AgentHistoryMapper;
import io.camunda.db.rdbms.write.domain.AgentHistoryDbModel;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryCommitStatus;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryRole;
import io.camunda.search.query.AgentInstanceHistoryQuery;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.AuthorizationCheck;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.security.core.authz.TenantCheck;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentHistoryDbReaderTest {

  private final AgentHistoryMapper agentHistoryMapper = mock(AgentHistoryMapper.class);
  private final AgentHistoryDbReader agentHistoryDbReader =
      new AgentHistoryDbReader(agentHistoryMapper, AbstractEntityReaderTest.TEST_CONFIG);

  // ===== Authorization early-exit =====

  @Test
  void shouldReturnEmptyResultWhenAuthorizedResourceIdsIsNull() {
    final var result =
        agentHistoryDbReader.search(
            AgentInstanceHistoryQuery.of(b -> b),
            ResourceAccessChecks.of(
                AuthorizationCheck.enabled(
                    RequiredAuthorization.of(a -> a.processDefinition().readProcessInstance())),
                TenantCheck.disabled()));

    assertThat(result.items()).isEmpty();
    verify(agentHistoryMapper, times(0)).search(any());
    verify(agentHistoryMapper, times(0)).count(any());
  }

  @Test
  void shouldReturnEmptyResultWhenAuthorizedTenantIdsIsEmpty() {
    final var result =
        agentHistoryDbReader.search(
            AgentInstanceHistoryQuery.of(b -> b),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of())));

    assertThat(result.items()).isEmpty();
    verify(agentHistoryMapper, times(0)).search(any());
    verify(agentHistoryMapper, times(0)).count(any());
  }

  @Test
  void shouldInjectAuthorizedProcessDefinitionIdsIntoQuery() {
    final var authorizedIds = List.of("process-a", "process-b");
    when(agentHistoryMapper.count(any())).thenReturn(1L);
    when(agentHistoryMapper.search(any())).thenReturn(List.of(buildModel(1L)));

    agentHistoryDbReader.search(
        AgentInstanceHistoryQuery.of(b -> b),
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                RequiredAuthorization.of(
                    a -> a.processDefinition().readProcessInstance().resourceIds(authorizedIds))),
            TenantCheck.disabled()));

    verify(agentHistoryMapper)
        .search(argThat(q -> q.authorizedResourceIds().equals(authorizedIds)));
  }

  @Test
  void shouldInjectAuthorizedTenantIdsIntoQuery() {
    final var tenantIds = List.of("tenant-1", "tenant-2");
    when(agentHistoryMapper.count(any())).thenReturn(1L);
    when(agentHistoryMapper.search(any())).thenReturn(List.of(buildModel(1L)));

    agentHistoryDbReader.search(
        AgentInstanceHistoryQuery.of(b -> b),
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(tenantIds)));

    verify(agentHistoryMapper).search(argThat(q -> q.authorizedTenantIds().equals(tenantIds)));
  }

  // ===== Page-size edge case =====

  @Test
  void shouldReturnEmptyItemsButCorrectTotalWhenPageSizeIsZero() {
    when(agentHistoryMapper.count(any())).thenReturn(5L);

    final var result =
        agentHistoryDbReader.search(
            AgentInstanceHistoryQuery.of(b -> b.page(p -> p.size(0))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    assertThat(result.total()).isEqualTo(5L);
    assertThat(result.items()).isEmpty();
    verify(agentHistoryMapper, times(0)).search(any());
  }

  // ===== Filter pass-through =====

  @Test
  void shouldPassAgentInstanceKeyFilterToMapper() {
    when(agentHistoryMapper.count(any())).thenReturn(1L);
    when(agentHistoryMapper.search(any())).thenReturn(List.of(buildModel(1L)));

    agentHistoryDbReader.search(
        AgentInstanceHistoryQuery.of(b -> b.filter(f -> f.agentInstanceKeys(42L))),
        ResourceAccessChecks.disabled());

    verify(agentHistoryMapper)
        .search(
            argThat(
                q ->
                    q.filter().agentInstanceKeyOperations().stream()
                        .anyMatch(op -> op.value().equals(42L))));
  }

  @Test
  void shouldPassHistoryItemKeyFilterToMapper() {
    when(agentHistoryMapper.count(any())).thenReturn(1L);
    when(agentHistoryMapper.search(any())).thenReturn(List.of(buildModel(1L)));

    agentHistoryDbReader.search(
        AgentInstanceHistoryQuery.of(b -> b.filter(f -> f.historyItemKeys(99L))),
        ResourceAccessChecks.disabled());

    verify(agentHistoryMapper)
        .search(
            argThat(
                q ->
                    q.filter().historyItemKeyOperations().stream()
                        .anyMatch(op -> op.value().equals(99L))));
  }

  @Test
  void shouldReturnMappedEntitiesFromSearch() {
    when(agentHistoryMapper.count(any())).thenReturn(2L);
    when(agentHistoryMapper.search(any())).thenReturn(List.of(buildModel(1L), buildModel(2L)));

    final var result =
        agentHistoryDbReader.search(
            AgentInstanceHistoryQuery.of(b -> b), ResourceAccessChecks.disabled());

    assertThat(result.total()).isEqualTo(2L);
    assertThat(result.items()).hasSize(2);
    assertThat(result.items()).extracting(e -> e.historyItemKey()).containsExactly(1L, 2L);
  }

  // ===== Helpers =====

  private AgentHistoryDbModel buildModel(final long key) {
    return new AgentHistoryDbModel.Builder()
        .agentHistoryKey(key)
        .agentInstanceKey(10L)
        .elementInstanceKey(20L)
        .processInstanceKey(30L)
        .rootProcessInstanceKey(40L)
        .processDefinitionKey(50L)
        .processDefinitionId("myProcess")
        .tenantId("<default>")
        .partitionId(1)
        .jobKey(60L)
        .jobLease("lease-" + key)
        .loopIteration(1)
        .role(AgentInstanceHistoryRole.USER)
        .commitStatus(AgentInstanceHistoryCommitStatus.PENDING)
        .producedAt(OffsetDateTime.parse("2024-01-01T00:00:00Z"))
        .inputTokens(100L)
        .outputTokens(50L)
        .durationMs(200L)
        .contentItems(List.of())
        .toolCallValues(List.of())
        .build();
  }
}
