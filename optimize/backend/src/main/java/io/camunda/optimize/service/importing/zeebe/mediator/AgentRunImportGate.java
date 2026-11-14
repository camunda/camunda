/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.mediator;

import io.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import io.camunda.optimize.service.importing.PositionBasedImportIndexHandler;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Holds agent instance records back until the process instance and incident imports of the same
 * partition have stored everything before them.
 *
 * <p>Zeebe writes the {@code COMPLETED} record of an agent run only when its process instance
 * completes or after it was terminated ({@code ProcessProcessor#onComplete} and {@code
 * #finalizeTermination} call {@code
 * AgentInstanceBehavior#completeAgentInstancesOfProcessInstance}), so every element and incident
 * record of that process instance, and the process's own end record, has a lower position. All
 * records of a process instance, call activity children included, are on one partition, where
 * positions only increase. So once both imports have persisted a position, the process instance of
 * every agent record up to it is complete in Optimize and can be read once.
 *
 * <p>An import without new records never persists a higher position. It counts as past every record
 * the agent import had already fetched one index refresh interval before the import's last empty
 * fetch started: those records were visible then, and so was every lower-position record exported
 * with or before them. Without that rule a partition without new incidents would block forever.
 *
 * <p>Nothing here survives a restart; every mediator continues from its stored position.
 */
final class AgentRunImportGate {

  // Plenty for the fetches made within one idle backoff of the gating imports.
  private static final int MAX_REMEMBERED_FETCHES = 1_000;

  private final Supplier<List<PositionBasedImportIndexHandler>> gatingHandlers;
  private final Supplier<Duration> refreshInterval;
  private final Deque<Fetch> fetches = new ArrayDeque<>();

  AgentRunImportGate(
      final Supplier<List<PositionBasedImportIndexHandler>> gatingHandlers,
      final Supplier<Duration> refreshInterval) {
    this.gatingHandlers = gatingHandlers;
    this.refreshInterval = refreshInterval;
  }

  /** The records of the page, in order, up to the first one the gating imports haven't passed. */
  <T extends ZeebeRecordDto<?, ?>> List<T> admit(
      final List<T> page, final OffsetDateTime fetchStartedAt) {
    if (page.isEmpty()) {
      return page;
    }
    remember(fetchStartedAt, page.getLast().getPosition());
    final long limit = limit();
    int admitted = 0;
    while (admitted < page.size() && page.get(admitted).getPosition() <= limit) {
      admitted++;
    }
    return page.subList(0, admitted);
  }

  /** The highest position both gating imports have passed. */
  long limit() {
    return gatingHandlers.get().stream()
        .filter(Objects::nonNull)
        .mapToLong(this::passed)
        .min()
        .orElse(Long.MAX_VALUE);
  }

  private long passed(final PositionBasedImportIndexHandler handler) {
    final long persisted = handler.getPersistedPositionOfLastEntity();
    return handler
        .getIdleSince()
        .map(
            idleSince -> Math.max(persisted, visibleBefore(idleSince.minus(refreshInterval.get()))))
        .orElse(persisted);
  }

  private synchronized long visibleBefore(final OffsetDateTime cutoff) {
    return fetches.stream()
        .filter(fetch -> fetch.startedAt() != null && !fetch.startedAt().isAfter(cutoff))
        .mapToLong(Fetch::lastPosition)
        .max()
        .orElse(Long.MIN_VALUE);
  }

  private synchronized void remember(final OffsetDateTime startedAt, final long lastPosition) {
    fetches.addLast(new Fetch(startedAt, lastPosition));
    while (fetches.size() > MAX_REMEMBERED_FETCHES) {
      fetches.removeFirst();
    }
  }

  private record Fetch(OffsetDateTime startedAt, long lastPosition) {}
}
