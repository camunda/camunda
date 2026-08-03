/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import java.util.List;
import java.util.function.LongPredicate;

public interface AgentInstanceState {

  /**
   * @return the stored record, or {@code null} if no record exists for the given key
   */
  AgentInstanceRecord getRecord(long agentInstanceKey);

  /**
   * @return the keys of all agent instances currently associated with the given process instance
   */
  List<Long> getAgentInstanceKeysByProcessInstanceKey(long processInstanceKey);

  /**
   * Visits the keys of agent instances associated with the given process instance, in ascending key
   * order, starting at (or just after, if it no longer exists) {@code startAtAgentInstanceKey}. The
   * visitor controls how many keys are visited by returning {@code false} once it doesn't want to
   * see more (e.g. because a batch limit was reached).
   *
   * @param startAtAgentInstanceKey the key to resume iteration from, or {@code -1} to start from
   *     the beginning
   * @return {@code true} if there are more agent instance keys left beyond what the visitor
   *     accepted, {@code false} if iteration reached the end
   */
  boolean visitAgentInstanceKeysByProcessInstanceKey(
      long processInstanceKey, long startAtAgentInstanceKey, LongPredicate visitor);
}
