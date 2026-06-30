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
}
