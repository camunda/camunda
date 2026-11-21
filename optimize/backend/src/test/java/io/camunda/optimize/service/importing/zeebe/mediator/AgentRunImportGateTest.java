/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.mediator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceRecordDto;
import io.camunda.optimize.service.importing.PositionBasedImportIndexHandler;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class AgentRunImportGateTest {

  private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-03T10:00:00Z");
  private static final Duration REFRESH = Duration.ofSeconds(1);

  private final PositionBasedImportIndexHandler processInstances =
      mock(PositionBasedImportIndexHandler.class);
  private final PositionBasedImportIndexHandler incidents =
      mock(PositionBasedImportIndexHandler.class);
  private final AgentRunImportGate underTest =
      new AgentRunImportGate(() -> List.of(processInstances, incidents), () -> REFRESH);

  @Test
  void shouldHoldBackRecordsBeyondThePositionBothImportsPersisted() {
    // given
    persisted(processInstances, 30);
    persisted(incidents, 25);

    // when
    final List<ZeebeAgentInstanceRecordDto> admitted = underTest.admit(records(10, 20, 30), T0);

    // then
    assertThat(positions(admitted)).containsExactly(10L, 20L);
  }

  @Test
  void shouldReleaseHeldBackRecordsOnceBothImportsPassThem() {
    // given
    persisted(processInstances, 15);
    persisted(incidents, 40);
    assertThat(positions(underTest.admit(records(10, 20, 30), T0))).containsExactly(10L);

    // when the process instance import catches up
    persisted(processInstances, 30);

    // then
    assertThat(positions(underTest.admit(records(20, 30), T0.plusSeconds(5))))
        .containsExactly(20L, 30L);
  }

  @Test
  void shouldNotBeBlockedByAnIdleImportWhoseEmptyFetchStartedARefreshIntervalLater() {
    // given the incident import last persisted position 5 and then found nothing new
    persisted(processInstances, 100);
    persisted(incidents, 5);
    underTest.admit(records(10, 20, 30), T0);
    when(incidents.getIdleSince()).thenReturn(Optional.of(T0.plus(REFRESH)));

    // when the agent import fetches the same records again
    final List<ZeebeAgentInstanceRecordDto> admitted =
        underTest.admit(records(10, 20, 30, 40), T0.plusSeconds(3));

    // then everything the first fetch saw is admitted, the newer record waits
    assertThat(positions(admitted)).containsExactly(10L, 20L, 30L);
  }

  @Test
  void shouldStillBlockOnAnIdleImportWhoseEmptyFetchWasTooEarly() {
    // given the empty fetch started less than a refresh interval after the agent fetch
    persisted(processInstances, 100);
    persisted(incidents, 5);
    underTest.admit(records(10, 20, 30), T0);
    when(incidents.getIdleSince()).thenReturn(Optional.of(T0.plusNanos(500_000_000)));

    // when
    final List<ZeebeAgentInstanceRecordDto> admitted =
        underTest.admit(records(10, 20, 30), T0.plusSeconds(3));

    // then
    assertThat(admitted).isEmpty();
  }

  @Test
  void shouldNotGateOnAnImportThePartitionDoesNotHave() {
    // given
    persisted(processInstances, 20);
    final AgentRunImportGate gate =
        new AgentRunImportGate(() -> Arrays.asList(processInstances, null), () -> REFRESH);

    // then
    assertThat(positions(gate.admit(records(10, 20, 30), T0))).containsExactly(10L, 20L);
  }

  private static void persisted(
      final PositionBasedImportIndexHandler handler, final long position) {
    when(handler.getPersistedPositionOfLastEntity()).thenReturn(position);
    when(handler.getIdleSince()).thenReturn(Optional.empty());
  }

  private static List<ZeebeAgentInstanceRecordDto> records(final long... positions) {
    return LongStream.of(positions)
        .mapToObj(
            position -> {
              final ZeebeAgentInstanceRecordDto record = new ZeebeAgentInstanceRecordDto();
              record.setPosition(position);
              return record;
            })
        .toList();
  }

  private static List<Long> positions(final List<ZeebeAgentInstanceRecordDto> records) {
    return records.stream().map(ZeebeAgentInstanceRecordDto::getPosition).toList();
  }
}
