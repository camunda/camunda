/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.AgentDefinitionEntity;
import io.camunda.search.entities.AgentDefinitionEntity.AgentType;
import io.camunda.search.query.AgentDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import org.junit.jupiter.api.Test;

class AgentDefinitionDocumentReaderTest {

  @Test
  void shouldGetByKey() {
    // given
    final var executor = mock(SearchClientBasedQueryExecutor.class);
    final var indexDescriptor = mock(IndexDescriptor.class);
    final var reader = new AgentDefinitionDocumentReader(executor, indexDescriptor);

    final var entity =
        new AgentDefinitionEntity(
            1L,
            AgentType.AI_AGENT_TASK,
            "name",
            "elementId",
            "processDefinitionId",
            2L,
            3,
            "versionTag",
            "tenantId");

    when(indexDescriptor.getFullQualifiedName()).thenReturn("agent-definition-index");
    when(executor.getById(
            eq("1"),
            eq(io.camunda.webapps.schema.entities.agentdefinition.AgentDefinitionEntity.class),
            eq("agent-definition-index")))
        .thenReturn(entity);

    // when
    final var result = reader.getByKey(1L, ResourceAccessChecks.disabled());

    // then
    assertThat(result).isEqualTo(entity);
    verify(executor)
        .getById(
            "1",
            io.camunda.webapps.schema.entities.agentdefinition.AgentDefinitionEntity.class,
            "agent-definition-index");
  }

  @Test
  void shouldSearch() {
    // given
    final var executor = mock(SearchClientBasedQueryExecutor.class);
    final var indexDescriptor = mock(IndexDescriptor.class);
    final var reader = new AgentDefinitionDocumentReader(executor, indexDescriptor);

    final var entity =
        new AgentDefinitionEntity(
            1L,
            AgentType.AI_AGENT_TASK,
            "name",
            "elementId",
            "processDefinitionId",
            2L,
            3,
            "versionTag",
            "tenantId");

    final var query = AgentDefinitionQuery.of(b -> b);
    when(executor.search(
            eq(query),
            eq(io.camunda.webapps.schema.entities.agentdefinition.AgentDefinitionEntity.class),
            any(ResourceAccessChecks.class)))
        .thenReturn(SearchQueryResult.of(entity));

    // when
    final var result = reader.search(query, ResourceAccessChecks.disabled());

    // then
    assertThat(result.items()).containsExactly(entity);
  }
}
