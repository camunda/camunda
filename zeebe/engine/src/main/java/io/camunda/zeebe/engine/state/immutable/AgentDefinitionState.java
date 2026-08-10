/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import org.agrona.DirectBuffer;

public interface AgentDefinitionState {

  /**
   * @return the agent definition key minted for the given process definition and element, or {@code
   *     null} if no agent definition has been created for it
   */
  Long getAgentDefinitionKey(long processDefinitionKey, DirectBuffer elementId);
}
