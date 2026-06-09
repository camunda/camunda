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

import io.camunda.db.rdbms.sql.AgentInstanceMapper;
import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceStatus;
import io.camunda.search.query.AgentInstanceQuery;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.AuthorizationCheck;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.security.core.authz.TenantCheck;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentInstanceDbReaderTest {

  private final AgentInstanceMapper agentInstanceMapper = mock(AgentInstanceMapper.class);
  private final AgentInstanceDbReader agentInstanceDbReader =
      new AgentInstanceDbReader(agentInstanceMapper, AbstractEntityReaderTest.TEST_CONFIG);

  // ===== Authorization early-exit =====

  @Test
  void shouldReturnEmptyResultWhenAuthorizedResourceIdsIsNull() {
    // Auth enabled but no resource IDs granted → reader must short-circuit before hitting DB
    final var result =
        agentInstanceDbReader.search(
            AgentInstanceQuery.of(b -> b),
            ResourceAccessChecks.of(
                AuthorizationCheck.enabled(
                    RequiredAuthorization.of(a -> a.processDefinition().readProcessInstance())),
                TenantCheck.disabled()));

    assertThat(result.items()).isEmpty();
    verify(agentInstanceMapper, times(0)).search(any());
    verify(agentInstanceMapper, times(0)).count(any());
  }

  @Test
  void shouldInjectAuthorizedProcessDefinitionIdsIntoQuery() {
    final var authorizedIds = List.of("process-a", "process-b");
    when(agentInstanceMapper.count(any())).thenReturn(1L);
    when(agentInstanceMapper.search(any())).thenReturn(List.of(buildModel(1L)));

    agentInstanceDbReader.search(
        AgentInstanceQuery.of(b -> b),
        ResourceAccessChecks.of(
            AuthorizationCheck.enabled(
                RequiredAuthorization.of(
                    a -> a.processDefinition().readProcessInstance().resourceIds(authorizedIds))),
            TenantCheck.disabled()));

    verify(agentInstanceMapper)
        .search(argThat(q -> q.authorizedResourceIds().equals(authorizedIds)));
  }

  @Test
  void shouldInjectAuthorizedTenantIdsIntoQuery() {
    final var tenantIds = List.of("tenant-1", "tenant-2");
    when(agentInstanceMapper.count(any())).thenReturn(1L);
    when(agentInstanceMapper.search(any())).thenReturn(List.of(buildModel(1L)));

    agentInstanceDbReader.search(
        AgentInstanceQuery.of(b -> b),
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(tenantIds)));

    verify(agentInstanceMapper).search(argThat(q -> q.authorizedTenantIds().equals(tenantIds)));
  }

  @Test
  void shouldReturnEmptyResultWhenAuthorizedTenantIdsIsEmpty() {
    final var result =
        agentInstanceDbReader.search(
            AgentInstanceQuery.of(b -> b),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of())));

    assertThat(result.items()).isEmpty();
    verify(agentInstanceMapper, times(0)).search(any());
    verify(agentInstanceMapper, times(0)).count(any());
  }

  // ===== Page-size edge case =====

  @Test
  void shouldReturnEmptyItemsButCorrectTotalWhenPageSizeIsZero() {
    when(agentInstanceMapper.count(any())).thenReturn(42L);

    final var result =
        agentInstanceDbReader.search(
            AgentInstanceQuery.of(b -> b.page(p -> p.size(0))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    assertThat(result.total()).isEqualTo(42L);
    assertThat(result.items()).isEmpty();
    verify(agentInstanceMapper, times(0)).search(any());
  }

  // ===== getByKey =====

  @Test
  void shouldReturnEntityForGetByKey() {
    when(agentInstanceMapper.count(any())).thenReturn(1L);
    when(agentInstanceMapper.search(any())).thenReturn(List.of(buildModel(42L)));

    final var entity = agentInstanceDbReader.getByKey(42L, ResourceAccessChecks.disabled());

    assertThat(entity).isNotNull();
    assertThat(entity.agentInstanceKey()).isEqualTo(42L);
    verify(agentInstanceMapper)
        .search(
            argThat(
                q ->
                    q.filter().agentInstanceKeyOperations().stream()
                        .anyMatch(op -> op.value().equals(42L))));
  }

  @Test
  void shouldReturnNullForGetByKeyWhenNotFound() {
    when(agentInstanceMapper.count(any())).thenReturn(0L);
    when(agentInstanceMapper.search(any())).thenReturn(List.of());

    final var entity = agentInstanceDbReader.getByKey(99L, ResourceAccessChecks.disabled());

    assertThat(entity).isNull();
  }

  // ===== Filter pass-through =====

  @Test
  void shouldPassAgentInstanceKeyFilterToMapper() {
    when(agentInstanceMapper.count(any())).thenReturn(1L);
    when(agentInstanceMapper.search(any())).thenReturn(List.of(buildModel(1L)));

    agentInstanceDbReader.search(
        AgentInstanceQuery.of(b -> b.filter(f -> f.agentInstanceKeys(1L))),
        ResourceAccessChecks.disabled());

    verify(agentInstanceMapper)
        .search(
            argThat(
                q ->
                    q.filter().agentInstanceKeyOperations().stream()
                        .anyMatch(op -> op.value().equals(1L))));
  }

  @Test
  void shouldPassStatusFilterToMapper() {
    when(agentInstanceMapper.count(any())).thenReturn(1L);
    when(agentInstanceMapper.search(any())).thenReturn(List.of(buildModel(1L)));

    agentInstanceDbReader.search(
        AgentInstanceQuery.of(b -> b.filter(f -> f.statuses("THINKING"))),
        ResourceAccessChecks.disabled());

    verify(agentInstanceMapper)
        .search(
            argThat(
                q ->
                    q.filter().statusOperations().stream()
                        .anyMatch(op -> "THINKING".equals(op.value()))));
  }

  @Test
  void shouldPassProcessInstanceKeyFilterToMapper() {
    when(agentInstanceMapper.count(any())).thenReturn(1L);
    when(agentInstanceMapper.search(any())).thenReturn(List.of(buildModel(1L)));

    agentInstanceDbReader.search(
        AgentInstanceQuery.of(b -> b.filter(f -> f.processInstanceKeys(200L))),
        ResourceAccessChecks.disabled());

    verify(agentInstanceMapper)
        .search(
            argThat(
                q ->
                    q.filter().processInstanceKeyOperations().stream()
                        .anyMatch(op -> op.value().equals(200L))));
  }

  @Test
  void shouldPassTenantIdFilterToMapper() {
    when(agentInstanceMapper.count(any())).thenReturn(1L);
    when(agentInstanceMapper.search(any())).thenReturn(List.of(buildModel(1L)));

    agentInstanceDbReader.search(
        AgentInstanceQuery.of(b -> b.filter(f -> f.tenantIds("my-tenant"))),
        ResourceAccessChecks.disabled());

    verify(agentInstanceMapper)
        .search(
            argThat(
                q ->
                    q.filter().tenantIdOperations().stream()
                        .anyMatch(op -> "my-tenant".equals(op.value()))));
  }

  @Test
  void shouldReturnMappedEntitiesFromSearch() {
    when(agentInstanceMapper.count(any())).thenReturn(2L);
    when(agentInstanceMapper.search(any())).thenReturn(List.of(buildModel(1L), buildModel(2L)));

    final var result =
        agentInstanceDbReader.search(
            AgentInstanceQuery.of(b -> b), ResourceAccessChecks.disabled());

    assertThat(result.total()).isEqualTo(2L);
    assertThat(result.items()).hasSize(2);
    assertThat(result.items()).extracting(e -> e.agentInstanceKey()).containsExactly(1L, 2L);
  }

  // ===== Helpers =====

  private AgentInstanceDbModel buildModel(final long key) {
    return new AgentInstanceDbModel.Builder()
        .agentInstanceKey(key)
        .elementId("element-" + key)
        .processInstanceKey(100L)
        .rootProcessInstanceKey(-1L)
        .processDefinitionId("myProcess")
        .processDefinitionKey(10L)
        .processDefinitionVersion(1)
        .tenantId("<default>")
        .partitionId(1)
        .status(AgentInstanceStatus.IDLE)
        .model("gpt-4o")
        .provider("openai")
        .systemPrompt("You are an assistant.")
        .maxTokens(10000L)
        .maxModelCalls(100)
        .maxToolCalls(50)
        .inputTokens(0L)
        .outputTokens(0L)
        .modelCalls(0)
        .toolCalls(0)
        .creationDate(OffsetDateTime.parse("2024-01-01T00:00:00Z"))
        .lastUpdatedDate(OffsetDateTime.parse("2024-01-01T00:00:00Z"))
        .elementInstanceKeys(List.of())
        .build();
  }
}
