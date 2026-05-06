/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.mapper.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceCallHierarchyEntry;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class SearchQueryResponseMapperTest {

  @Test
  void shouldMapCallHierarchyEntryUsingProcessDefinitionNameWhenPresent() {
    // given
    final var entity = callHierarchyEntity("demoProcess", "Demo Process");

    // when
    final ProcessInstanceCallHierarchyEntry entry =
        SearchQueryResponseMapper.toProcessInstanceCallHierarchyEntry(entity);

    // then
    assertThat(entry.getProcessInstanceKey()).isEqualTo("123");
    assertThat(entry.getProcessDefinitionKey()).isEqualTo("456");
    assertThat(entry.getProcessDefinitionName()).isEqualTo("Demo Process");
  }

  @Test
  void shouldMapCallHierarchyEntryFallingBackToProcessDefinitionIdWhenNameIsNull() {
    // given - regression coverage for #50011: BPMN deployed without a name
    // attribute leaves processDefinitionName null and previously NPE'd here
    final var entity = callHierarchyEntity("demoProcess", null);

    // when
    final ProcessInstanceCallHierarchyEntry entry =
        SearchQueryResponseMapper.toProcessInstanceCallHierarchyEntry(entity);

    // then
    assertThat(entry.getProcessDefinitionName()).isEqualTo("demoProcess");
  }

  @Test
  void shouldMapCallHierarchyEntryFallingBackToProcessDefinitionIdWhenNameIsBlank() {
    // given
    final var entity = callHierarchyEntity("demoProcess", "   ");

    // when
    final ProcessInstanceCallHierarchyEntry entry =
        SearchQueryResponseMapper.toProcessInstanceCallHierarchyEntry(entity);

    // then
    assertThat(entry.getProcessDefinitionName()).isEqualTo("demoProcess");
  }

  private static ProcessInstanceEntity callHierarchyEntity(
      final String processDefinitionId, final String processDefinitionName) {
    return new ProcessInstanceEntity(
        123L, // processInstanceKey
        processDefinitionId,
        processDefinitionName,
        1, // processDefinitionVersion
        null, // processDefinitionVersionTag
        456L, // processDefinitionKey
        null, // parentProcessInstanceKey
        null, // parentFlowNodeInstanceKey
        OffsetDateTime.now(),
        null, // endDate
        ProcessInstanceState.ACTIVE,
        false, // hasIncident
        "tenant",
        null, // treePath
        null); // tags
  }
}
