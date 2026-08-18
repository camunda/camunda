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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.sql.AgentDefinitionMapper;
import io.camunda.db.rdbms.write.domain.AgentDefinitionDbModel.AgentDefinitionDbModelBuilder;
import io.camunda.search.entities.AgentDefinitionEntity;
import io.camunda.search.entities.AgentDefinitionEntity.AgentType;
import io.camunda.search.query.AgentDefinitionQuery;
import io.camunda.security.core.authz.ResourceAccessChecks;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentDefinitionDbReaderTest {

  private final AgentDefinitionMapper agentDefinitionMapper = mock(AgentDefinitionMapper.class);
  private final AgentDefinitionDbReader agentDefinitionDbReader =
      new AgentDefinitionDbReader(agentDefinitionMapper, AbstractEntityReaderTest.TEST_CONFIG);

  // ===== Entity mapping =====

  @Test
  void shouldReturnMappedEntitiesFromSearch() {
    // given
    when(agentDefinitionMapper.count(any())).thenReturn(2L);
    when(agentDefinitionMapper.search(any()))
        .thenReturn(
            List.of(
                new AgentDefinitionDbModelBuilder()
                    .agentDefinitionKey(1L)
                    .agentType(AgentType.AI_AGENT_TASK)
                    .name("agent-1")
                    .elementId("element-1")
                    .processDefinitionId("myProcess")
                    .processDefinitionKey(10L)
                    .processDefinitionVersion(1)
                    .processDefinitionVersionTag("v1")
                    .tenantId("<default>")
                    .build(),
                new AgentDefinitionDbModelBuilder()
                    .agentDefinitionKey(2L)
                    .agentType(AgentType.AI_AGENT_TASK)
                    .name("agent-2")
                    .elementId("element-2")
                    .processDefinitionId("myProcess")
                    .processDefinitionKey(10L)
                    .processDefinitionVersion(1)
                    .processDefinitionVersionTag("v1")
                    .tenantId("<default>")
                    .build()));

    // when
    final var result =
        agentDefinitionDbReader.search(
            AgentDefinitionQuery.of(b -> b), ResourceAccessChecks.disabled());

    // then
    assertThat(result.total()).isEqualTo(2L);
    assertThat(result.items()).hasSize(2);
    assertThat(result.items())
        .extracting(AgentDefinitionEntity::agentDefinitionKey)
        .containsExactly(1L, 2L);
  }

  // ===== getByKey =====

  @Test
  void shouldReturnEntityForGetByKey() {
    // given
    when(agentDefinitionMapper.count(any())).thenReturn(1L);
    when(agentDefinitionMapper.search(any()))
        .thenReturn(
            List.of(
                new AgentDefinitionDbModelBuilder()
                    .agentDefinitionKey(42L)
                    .agentType(AgentType.AI_AGENT_TASK)
                    .name("agent-42")
                    .elementId("element-42")
                    .processDefinitionId("myProcess")
                    .processDefinitionKey(10L)
                    .processDefinitionVersion(1)
                    .processDefinitionVersionTag("v1")
                    .tenantId("<default>")
                    .build()));

    // when
    final var entity = agentDefinitionDbReader.getByKey(42L, ResourceAccessChecks.disabled());

    // then
    assertThat(entity).isNotNull();
    assertThat(entity.agentDefinitionKey()).isEqualTo(42L);
    verify(agentDefinitionMapper)
        .search(
            argThat(
                q ->
                    q.filter().agentDefinitionKeyOperations().stream()
                        .anyMatch(op -> op.value().equals(42L))));
  }

  @Test
  void shouldReturnNullForGetByKeyWhenNotFound() {
    // given
    when(agentDefinitionMapper.count(any())).thenReturn(0L);
    when(agentDefinitionMapper.search(any())).thenReturn(List.of());

    // when
    final var entity = agentDefinitionDbReader.getByKey(99L, ResourceAccessChecks.disabled());

    // then
    assertThat(entity).isNull();
  }
}
