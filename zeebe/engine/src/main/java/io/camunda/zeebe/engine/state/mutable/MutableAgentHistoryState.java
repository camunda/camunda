/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.AgentHistoryState;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;

public interface MutableAgentHistoryState extends AgentHistoryState {

  /** Inserts a new history item stored under {@code historyItemKey}. */
  void insert(long historyItemKey, AgentHistoryRecord record);

  /**
   * Deletes the history item stored under {@code historyItemKey}, including the secondary index.
   */
  void delete(long historyItemKey);

  /**
   * Deletes the history item stored under {@code historyItemKey} using the provided {@code record}
   * to locate the secondary index entry, avoiding an extra state lookup.
   */
  void delete(long historyItemKey, AgentHistoryRecord record);

  /**
   * Records that {@code historyItemId} was committed under {@code agentHistoryKey} for {@code
   * agentInstanceKey}, so a later job resending this id can still be recognized as a duplicate once
   * the pending item itself is gone.
   */
  void putCommittedHistoryItemKey(
      long agentInstanceKey, String historyItemId, long agentHistoryKey);

  /**
   * Deletes every committed-id entry recorded for {@code agentInstanceKey}. Called once, when the
   * agent instance completes.
   */
  void deleteCommittedHistoryItemKeys(long agentInstanceKey);

  /**
   * Records that metrics were accumulated for {@code historyItemId} on {@code agentInstanceKey}, so
   * a later resend of the same id — even one whose earlier copy was discarded — does not accumulate
   * its metrics again.
   */
  void markMetricsAccumulated(long agentInstanceKey, String historyItemId);
}
