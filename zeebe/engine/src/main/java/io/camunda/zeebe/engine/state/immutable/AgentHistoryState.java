/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;

public interface AgentHistoryState {

  /**
   * @return the stored record, or {@code null} if no record exists for the given key
   */
  AgentHistoryRecord get(long historyItemKey);

  /**
   * Visits all pending history items associated with the given job key, regardless of lease.
   *
   * @param jobKey the job key to search by
   * @param visitor called once per matching history item
   */
  void visitByJobKey(long jobKey, AgentHistoryVisitor visitor);

  /**
   * Visits all pending history items associated with the given job key and lease. Use this to
   * target only the items produced during a specific job activation.
   *
   * @param jobKey the job key to search by
   * @param jobLease the lease to match
   * @param visitor called once per matching history item
   */
  void visitByJobLease(long jobKey, String jobLease, AgentHistoryVisitor visitor);

  /**
   * @return the {@code agentHistoryKey} of the history item that was committed under this {@code
   *     historyItemId} for this agent instance, or {@code null} if none was committed
   */
  Long getCommittedHistoryItemKey(long agentInstanceKey, String historyItemId);

  /**
   * @return whether metrics were already accumulated for {@code historyItemId} on {@code
   *     agentInstanceKey}, whether or not that item's copy is still pending, committed, or was
   *     discarded
   */
  boolean hasAccumulatedMetrics(long agentInstanceKey, String historyItemId);

  /**
   * Visits committed history-item ids recorded for {@code agentInstanceKey}, in unspecified order,
   * until every id has been visited or {@code visitor} returns {@code false}.
   *
   * @param visitor called once per id; return {@code false} to stop visiting early
   */
  void visitCommittedHistoryItemIds(long agentInstanceKey, HistoryItemIdVisitor visitor);

  /**
   * Visits metrics-accumulated history-item ids recorded for {@code agentInstanceKey}, in
   * unspecified order, until every id has been visited or {@code visitor} returns {@code false}.
   *
   * @param visitor called once per id; return {@code false} to stop visiting early
   */
  void visitMetricsAccumulatedHistoryItemIds(long agentInstanceKey, HistoryItemIdVisitor visitor);

  @FunctionalInterface
  interface AgentHistoryVisitor {
    void visit(AgentHistoryRecord record);
  }

  @FunctionalInterface
  interface HistoryItemIdVisitor {
    /**
     * @return {@code true} to keep visiting, {@code false} to stop early
     */
    boolean visit(String historyItemId);
  }
}
