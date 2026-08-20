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

  @FunctionalInterface
  interface AgentHistoryVisitor {
    void visit(AgentHistoryRecord record);
  }
}
