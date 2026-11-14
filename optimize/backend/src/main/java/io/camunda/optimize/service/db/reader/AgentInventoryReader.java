/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.dto.optimize.rest.agentic.AgentInventoryDto.AgentInventoryEntry;
import java.time.OffsetDateTime;
import java.util.List;

public interface AgentInventoryReader {

  /**
   * Agents from the agent-definition index, plus agents seen only on runs, with metrics over runs
   * of completed process instances that ended at or after {@code endedFrom} (all runs when null),
   * restricted to one process when {@code processDefinitionKey} is set. Process definition names
   * are left empty for the caller to resolve.
   */
  List<AgentInventoryEntry> getAgentInventory(
      OffsetDateTime endedFrom, String processDefinitionKey);
}
