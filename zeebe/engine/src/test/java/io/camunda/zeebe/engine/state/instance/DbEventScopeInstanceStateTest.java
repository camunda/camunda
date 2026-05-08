/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class DbEventScopeInstanceStateTest {

  private static final long EVENT_SCOPE_KEY = 123L;

  @SuppressWarnings("unused") // injected by the extension
  private MutableProcessingState processingState;

  @SuppressWarnings("unused") // injected by the extension
  private ZeebeDb<ZbColumnFamilies> zeebeDb;

  @SuppressWarnings("unused") // injected by the extension
  private TransactionContext transactionContext;

  private final DbLong eventScopeKey = new DbLong();

  private MutableEventScopeInstanceState state;
  private ColumnFamily<DbLong, PersistedEventTriggerKeys> eventTriggerKeysByScopeKeyColumnFamily;

  @BeforeEach
  void setUp() {
    state = processingState.getEventScopeInstanceState();
    eventTriggerKeysByScopeKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.EVENT_TRIGGER_KEYS_BY_SCOPE_KEY,
            transactionContext,
            eventScopeKey,
            new PersistedEventTriggerKeys());
  }

  @Test
  void shouldTrackEventTriggerKeysPerScopeAndDeleteThemOnDeleteInstance() {
    // given
    state.createInstance(EVENT_SCOPE_KEY, Collections.emptySet(), Collections.emptySet());
    triggerEvent(EVENT_SCOPE_KEY, 1L);
    triggerEvent(EVENT_SCOPE_KEY, 2L);
    triggerEvent(EVENT_SCOPE_KEY, 3L);

    assertThat(getTrackedEventTriggerKeys(EVENT_SCOPE_KEY)).containsExactly(1L, 2L, 3L);

    // when
    state.deleteInstance(EVENT_SCOPE_KEY);

    // then
    assertThat(getTrackedEventTriggerKeys(EVENT_SCOPE_KEY)).isEmpty();
  }

  @Test
  void shouldDeleteEventTriggersWhenTrackedKeysAreMissing() {
    // given
    state.createInstance(EVENT_SCOPE_KEY, Collections.emptySet(), Collections.emptySet());
    triggerEvent(EVENT_SCOPE_KEY, 1L);
    triggerEvent(EVENT_SCOPE_KEY, 2L);
    deleteTrackedEventTriggerKeys(EVENT_SCOPE_KEY);

    // when
    state.deleteInstance(EVENT_SCOPE_KEY);

    // then
    assertThat(state.peekEventTrigger(EVENT_SCOPE_KEY)).isNull();
  }

  @Test
  void shouldRemoveTrackedEventTriggerKeyWhenPollingTrigger() {
    // given
    state.createInstance(EVENT_SCOPE_KEY, Collections.emptySet(), Collections.emptySet());
    triggerEvent(EVENT_SCOPE_KEY, 1L);
    triggerEvent(EVENT_SCOPE_KEY, 2L);

    // when
    state.pollEventTrigger(EVENT_SCOPE_KEY);

    // then
    assertThat(getTrackedEventTriggerKeys(EVENT_SCOPE_KEY)).containsExactly(2L);
  }

  private void triggerEvent(final long eventScopeKey, final long eventKey) {
    state.triggerEvent(
        eventScopeKey, eventKey, wrapString("event-" + eventKey), wrapString("{}"), -1L);
  }

  private List<Long> getTrackedEventTriggerKeys(final long eventScopeKey) {
    this.eventScopeKey.wrapLong(eventScopeKey);

    final var trackedEventTriggerKeys =
        eventTriggerKeysByScopeKeyColumnFamily.get(
            this.eventScopeKey, PersistedEventTriggerKeys::new);

    if (trackedEventTriggerKeys == null) {
      return List.of();
    }

    return trackedEventTriggerKeys.getEventTriggerKeys();
  }

  private void deleteTrackedEventTriggerKeys(final long eventScopeKey) {
    this.eventScopeKey.wrapLong(eventScopeKey);
    eventTriggerKeysByScopeKeyColumnFamily.deleteIfExists(this.eventScopeKey);
  }
}
