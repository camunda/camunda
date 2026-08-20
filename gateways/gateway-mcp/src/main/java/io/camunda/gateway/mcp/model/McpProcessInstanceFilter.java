/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.gateway.protocol.model.simple.ProcessInstanceFilter;

/**
 * MCP-specific process instance filter modifying the {@link ProcessInstanceFilter} to hide fields
 * from MCP clients to avoid unnecessary context bloat.
 */
@JsonIgnoreProperties({
  "batchOperationId",
  "batchOperationKey",
  "elementId",
  "elementInstanceState",
  "errorMessage",
  "hasElementInstanceIncident",
  "hasRetriesLeft",
  "incidentErrorHashCode",
  "$or",
  "parentProcessInstanceKey",
  "parentElementInstanceKey",
  "processDefinitionVersionTag",
  "tenantId"
})
public interface McpProcessInstanceFilter {}
