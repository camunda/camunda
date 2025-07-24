/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.tools.incident;

public record Incident(
    Long key,
    Long processInstanceKey,
    Long processDefinitionKey,
    String type,
    String state,
    String errorMessage,
    String errorType,
    String flowNodeId,
    Long flowNodeInstanceKey,
    String creationTime,
    Long jobKey,
    String tenantId) {}
