/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto.AgentToolValueDto;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class ZeebeAgentInstanceDataDtoTest {

  // ── changedAttributes deserialization ─────────────────────────────────────

  @Test
  void shouldDeserializeRecordWithPopulatedChangedAttributes() throws Exception {
    // given — a record where changedAttributes is non-empty (e.g. an UPDATED record)
    final ObjectMapper objectMapper = new ObjectMapper();
    final String json =
        "{\"agentInstanceKey\":1,\"status\":\"THINKING\",\"changedAttributes\":[\"status\",\"metrics\"]}";

    // when — deserialization must not fail mutating an immutable list
    final ZeebeAgentInstanceDataDto dto =
        objectMapper.readValue(json, ZeebeAgentInstanceDataDto.class);

    // then
    assertThat(dto.getChangedAttributes()).containsExactly("status", "metrics");
  }

  @Test
  void shouldReturnEmptyListWhenChangedAttributesIsNull() {
    // given
    final ZeebeAgentInstanceDataDto dto = new ZeebeAgentInstanceDataDto();
    dto.setChangedAttributes(null);

    // when / then
    assertThat(dto.getChangedAttributes()).isNotNull().isEmpty();
  }

  // ── getTools() null-safety ────────────────────────────────────────────────

  @Test
  void shouldReturnEmptyListWhenToolsIsNull() {
    // given
    final ZeebeAgentInstanceDataDto dto = new ZeebeAgentInstanceDataDto();
    dto.setTools(null);

    // when
    final var result = dto.getTools();

    // then
    assertThat(result).isNotNull().isEmpty();
  }

  @Test
  void shouldReturnCopyOfToolsWhenToolsIsPopulated() {
    // given
    final AgentToolValueDto tool = new AgentToolValueDto();
    tool.setName("my-tool");
    tool.setDescription("does something");
    final ZeebeAgentInstanceDataDto dto = new ZeebeAgentInstanceDataDto();
    dto.setTools(List.of(tool));

    // when
    final var result = dto.getTools();

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("my-tool");
  }

  @Test
  void shouldReturnEmptyListWhenToolsIsEmpty() {
    // given
    final ZeebeAgentInstanceDataDto dto = new ZeebeAgentInstanceDataDto();
    dto.setTools(List.of());

    // when / then
    assertThat(dto.getTools()).isNotNull().isEmpty();
  }

  @Test
  void shouldReturnDefensiveCopyFromGetTools() {
    // given — modifying the returned list must not affect the DTO
    final AgentToolValueDto tool = new AgentToolValueDto();
    tool.setName("tool-a");
    final ZeebeAgentInstanceDataDto dto = new ZeebeAgentInstanceDataDto();
    dto.setTools(List.of(tool));

    // when
    dto.getTools().clear();

    // then — original list is unaffected
    assertThat(dto.getTools()).hasSize(1);
  }

  // ── equals() / hashCode() ─────────────────────────────────────────────────

  @Test
  void shouldBeEqualWhenAllFieldsMatch() {
    // given
    final ZeebeAgentInstanceDataDto a = buildDto(1L, List.of(10L, 20L));
    final ZeebeAgentInstanceDataDto b = buildDto(1L, List.of(10L, 20L));

    // then
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
  }

  @Test
  void shouldNotBeEqualWhenElementInstanceKeysDiffer() {
    // given — same agentInstanceKey but different migration history
    final ZeebeAgentInstanceDataDto a = buildDto(1L, List.of(10L, 20L));
    final ZeebeAgentInstanceDataDto b = buildDto(1L, List.of(10L, 30L));

    // then
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void shouldNotBeEqualWhenAgentInstanceKeyDiffers() {
    // given
    final ZeebeAgentInstanceDataDto a = buildDto(1L, List.of(10L));
    final ZeebeAgentInstanceDataDto b = buildDto(2L, List.of(10L));

    // then
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void shouldHandleNullToolsInEqualsWithoutNpe() {
    // given
    final ZeebeAgentInstanceDataDto a = buildDto(1L, List.of());
    final ZeebeAgentInstanceDataDto b = buildDto(1L, List.of());
    a.setTools(null);
    b.setTools(null);

    // then — must not throw NPE
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
  }

  @Test
  void shouldHandleNullDefinitionInEqualsWithoutNpe() {
    // given
    final ZeebeAgentInstanceDataDto a = buildDto(1L, List.of());
    final ZeebeAgentInstanceDataDto b = buildDto(1L, List.of());
    a.setDefinition(null);
    b.setDefinition(null);

    // then — must not throw NPE
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
  }

  @Test
  void shouldHandleNullMetricsInEqualsWithoutNpe() {
    // given
    final ZeebeAgentInstanceDataDto a = buildDto(1L, List.of());
    final ZeebeAgentInstanceDataDto b = buildDto(1L, List.of());
    a.setMetrics(null);
    b.setMetrics(null);

    // then — must not throw NPE
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private ZeebeAgentInstanceDataDto buildDto(
      final long agentInstanceKey, final List<Long> elementInstanceKeys) {
    final ZeebeAgentInstanceDataDto dto = new ZeebeAgentInstanceDataDto();
    dto.setAgentInstanceKey(agentInstanceKey);
    dto.setElementInstanceKey(99L);
    dto.setElementInstanceKeys(elementInstanceKeys);
    dto.setProcessInstanceKey(100L);
    dto.setBpmnProcessId("myProcess");
    dto.setStatus(AgentInstanceStatus.COMPLETED);
    return dto;
  }
}
