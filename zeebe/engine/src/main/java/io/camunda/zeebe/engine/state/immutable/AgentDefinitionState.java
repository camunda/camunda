/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.impl.record.value.agentdefinition.AgentDefinitionRecord;
import java.util.function.LongConsumer;
import org.agrona.DirectBuffer;

public interface AgentDefinitionState {

  /**
   * @return the agent definition key generated for the given process definition and element, or
   *     {@code null} if no agent definition has been created for it
   */
  Long getAgentDefinitionKey(long processDefinitionKey, DirectBuffer elementId);

  /**
   * @return the fully-populated agent definition record stored for the given agent definition key,
   *     or {@code null} if no such agent definition exists
   */
  AgentDefinitionRecord getAgentDefinition(long agentDefinitionKey);

  /**
   * Invokes {@code callback} with the agent definition key of each agent definition created for the
   * given process definition.
   */
  void forEachAgentDefinitionKey(long processDefinitionKey, LongConsumer callback);
}
