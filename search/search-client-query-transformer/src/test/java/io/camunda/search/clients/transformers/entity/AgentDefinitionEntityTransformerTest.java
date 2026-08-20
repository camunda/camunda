/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.AgentDefinitionEntity.AgentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AgentDefinitionEntityTransformerTest {

  private final AgentDefinitionEntityTransformer transformer =
      new AgentDefinitionEntityTransformer();

  private io.camunda.webapps.schema.entities.agentdefinition.AgentDefinitionEntity buildSource() {
    return new io.camunda.webapps.schema.entities.agentdefinition.AgentDefinitionEntity()
        .setKey(100L)
        .setAgentType(
            io.camunda.webapps.schema.entities.agentdefinition.AgentDefinitionType.AI_AGENT_TASK)
        .setName("myAgent")
        .setElementId("Task_1")
        .setBpmnProcessId("myProcess")
        .setProcessDefinitionKey(400L)
        .setProcessDefinitionVersion(2)
        .setProcessDefinitionVersionTag("v2")
        .setTenantId("<default>");
  }

  @Test
  void shouldMapAllFields() {
    final var result = transformer.apply(buildSource());

    assertThat(result.agentDefinitionKey()).isEqualTo(100L);
    assertThat(result.agentType()).isEqualTo(AgentType.AI_AGENT_TASK);
    assertThat(result.name()).isEqualTo("myAgent");
    assertThat(result.elementId()).isEqualTo("Task_1");
    assertThat(result.processDefinitionId()).isEqualTo("myProcess");
    assertThat(result.processDefinitionKey()).isEqualTo(400L);
    assertThat(result.processDefinitionVersion()).isEqualTo(2);
    assertThat(result.processDefinitionVersionTag()).isEqualTo("v2");
    assertThat(result.tenantId()).isEqualTo("<default>");
  }

  @ParameterizedTest
  @EnumSource(io.camunda.webapps.schema.entities.agentdefinition.AgentDefinitionType.class)
  void shouldMapEveryAgentType(
      final io.camunda.webapps.schema.entities.agentdefinition.AgentDefinitionType webappsType) {
    final var source = buildSource().setAgentType(webappsType);

    final var result = transformer.apply(source);

    assertThat(result.agentType()).isNotNull();
    assertThat(result.agentType().name()).isEqualTo(webappsType.name());
  }
}
