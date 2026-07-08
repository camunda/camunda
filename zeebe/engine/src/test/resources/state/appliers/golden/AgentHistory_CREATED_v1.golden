/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableAgentHistoryState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;

public final class AgentHistoryCreatedApplier
    implements TypedEventApplier<AgentHistoryIntent, AgentHistoryRecord> {

  private final MutableAgentHistoryState agentHistoryState;

  public AgentHistoryCreatedApplier(final MutableProcessingState processingState) {
    agentHistoryState = processingState.getAgentHistoryState();
  }

  @Override
  public void applyState(final long key, final AgentHistoryRecord value) {
    // content/toolCalls/metrics/producedAt already reached secondary storage via the CREATED event
    // itself — primary storage only needs identity fields to later match and delete the item.
    agentHistoryState.insert(key, value.onlyIdentityFields());
  }
}
