/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AgentIdsTest {

  // Elasticsearch and OpenSearch reject a document ID longer than 512 bytes.
  private static final int MAX_DOCUMENT_ID_BYTES = 512;

  @Test
  void shouldKeepDocumentIdWithinDatabaseLimitForLongAndMultibyteIds() {
    // given Zeebe accepts IDs far longer than the database accepts as a document ID
    final String longProcessId = "p".repeat(300);
    final String multibyteElementId = "ä".repeat(300);

    // when
    final String documentId =
        AgentIds.agentDefinitionDocumentId("<default>", longProcessId, multibyteElementId);

    // then
    assertThat(documentId.getBytes(StandardCharsets.UTF_8).length)
        .isLessThanOrEqualTo(MAX_DOCUMENT_ID_BYTES);
  }

  @Test
  void shouldGiveTheSameAgentTheSameDocumentId() {
    // when
    final String first = AgentIds.agentDefinitionDocumentId("tenant", "loan", "review");
    final String second = AgentIds.agentDefinitionDocumentId("tenant", "loan", "review");

    // then
    assertThat(first).isEqualTo(second);
  }

  @Test
  void shouldGiveAgentsOfOtherTenantsProcessesOrElementsOtherDocumentIds() {
    // given
    final String agent = AgentIds.agentDefinitionDocumentId("tenant", "loan", "review");

    // then
    assertThat(agent)
        .isNotEqualTo(AgentIds.agentDefinitionDocumentId("other", "loan", "review"))
        .isNotEqualTo(AgentIds.agentDefinitionDocumentId("tenant", "other", "review"))
        .isNotEqualTo(AgentIds.agentDefinitionDocumentId("tenant", "loan", "other"));
  }
}
