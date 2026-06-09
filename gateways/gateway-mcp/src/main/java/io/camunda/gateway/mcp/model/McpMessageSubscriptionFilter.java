/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.gateway.protocol.model.simple.MessageSubscriptionFilter;

/**
 * MCP-specific MessageSubscription filter modifying the {@link MessageSubscriptionFilter} to hide
 * fields from MCP clients that are not relevant in the MCP context.
 *
 * <p>{@code partitionId} is hidden because the MCP tool always restricts results to partition 1.
 * {@code tenantId} is hidden to avoid unnecessary context bloat.
 */
@JsonIgnoreProperties({"partitionId", "tenantId"})
public interface McpMessageSubscriptionFilter {}
