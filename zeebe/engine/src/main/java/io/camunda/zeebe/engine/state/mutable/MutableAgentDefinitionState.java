/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.AgentDefinitionState;
import io.camunda.zeebe.protocol.impl.record.value.agentdefinition.AgentDefinitionRecord;

public interface MutableAgentDefinitionState extends AgentDefinitionState {

  /**
   * Inserts the mapping from {@code record}'s process definition key and element id to {@code
   * agentDefinitionKey}.
   */
  void insert(long agentDefinitionKey, AgentDefinitionRecord record);

  /**
   * Removes {@code record}'s entries from both {@code
   * AGENT_DEFINITION_KEY_BY_PROCESS_DEFINITION_KEY_AND_ELEMENT_ID} and {@code
   * AGENT_DEFINITION_BY_KEY}. Idempotent: safe to call more than once for the same key (e.g. on a
   * redistributed/replayed {@code AgentDefinition:DELETED}), since an already-removed entry is a
   * no-op rather than an error.
   */
  void delete(AgentDefinitionRecord record);
}
