/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.agenthistory;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.value.AgentHistoryCommitStatus;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import org.junit.jupiter.api.Test;

final class AgentHistoryRecordTest {

  @Test
  void shouldExposeDefaults() {
    // given
    final AgentHistoryRecord record = new AgentHistoryRecord();

    // then
    assertThat(record.getAgentInstanceKey()).isEqualTo(-1L);
    assertThat(record.getElementInstanceKey()).isEqualTo(-1L);
    assertThat(record.getJobKey()).isEqualTo(-1L);
    assertThat(record.getAttemptNumber()).isEqualTo(0);
    assertThat(record.getIteration()).isEqualTo(0);
    assertThat(record.getRole()).isEqualTo(AgentHistoryRole.UNSPECIFIED);
    assertThat(record.getCommitStatus()).isEqualTo(AgentHistoryCommitStatus.UNSPECIFIED);
    assertThat(record.getProducedAt()).isEqualTo(-1L);
    assertThat(record.getMetadata()).isEmpty();
  }

  @Test
  void shouldRoundTripScalarFieldsViaMsgPack() {
    // given
    final AgentHistoryRecord original =
        new AgentHistoryRecord()
            .setAgentInstanceKey(2251799813685251L)
            .setElementInstanceKey(2251799813685249L)
            .setJobKey(2251799813685252L)
            .setAttemptNumber(2)
            .setIteration(3)
            .setRole(AgentHistoryRole.USER)
            .setCommitStatus(AgentHistoryCommitStatus.DISCARDED)
            .setProducedAt(1717200000000L)
            .setMetadata("{\"source\":\"test\"}");

    // when
    final AgentHistoryRecord copy = new AgentHistoryRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getAgentInstanceKey()).isEqualTo(original.getAgentInstanceKey());
    assertThat(copy.getElementInstanceKey()).isEqualTo(original.getElementInstanceKey());
    assertThat(copy.getJobKey()).isEqualTo(original.getJobKey());
    assertThat(copy.getAttemptNumber()).isEqualTo(original.getAttemptNumber());
    assertThat(copy.getIteration()).isEqualTo(original.getIteration());
    assertThat(copy.getRole()).isEqualTo(original.getRole());
    assertThat(copy.getCommitStatus()).isEqualTo(original.getCommitStatus());
    assertThat(copy.getProducedAt()).isEqualTo(original.getProducedAt());
    assertThat(copy.getMetadata()).isEqualTo(original.getMetadata());
  }

  @Test
  void shouldRoundTripRoleViaMsgPack() {
    // given
    final AgentHistoryRecord original =
        new AgentHistoryRecord().setRole(AgentHistoryRole.ASSISTANT);

    // when
    final AgentHistoryRecord copy = new AgentHistoryRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getRole()).isEqualTo(AgentHistoryRole.ASSISTANT);
  }

  @Test
  void shouldRoundTripCommitStatusViaMsgPack() {
    // given
    final AgentHistoryRecord original =
        new AgentHistoryRecord().setCommitStatus(AgentHistoryCommitStatus.COMMITTED);

    // when
    final AgentHistoryRecord copy = new AgentHistoryRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getCommitStatus()).isEqualTo(AgentHistoryCommitStatus.COMMITTED);
  }
}
