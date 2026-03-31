/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config;

import io.camunda.gateway.mcp.model.McpProcessInstanceCreationInstruction;
import io.camunda.gateway.mcp.model.McpUserTaskAssignmentRequest;
import io.camunda.gateway.protocol.model.simple.ProcessInstanceCreationInstruction;
import io.camunda.gateway.protocol.model.simple.UserTaskAssignmentRequest;
import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson 3 module that registers MCP-specific mixins to hide internal fields (e.g., {@code
 * tenantId}) from tool schemas and parameter deserialization.
 *
 * <p>This module is the single source of truth for MCP mixin registrations. It is auto-discovered
 * by Spring AI's {@code JsonParser} via the service loader mechanism ({@code
 * META-INF/services/tools.jackson.databind.JacksonModule}), and the same mapper is used by the
 * schema generator (victools).
 */
public class CamundaMcpJackson3Module extends SimpleModule {

  private static final String MODULE_NAME = "CamundaMcpJackson3Module";

  public CamundaMcpJackson3Module() {
    super(MODULE_NAME);
    setMixInAnnotation(
        ProcessInstanceCreationInstruction.class, McpProcessInstanceCreationInstruction.class);
    setMixInAnnotation(UserTaskAssignmentRequest.class, McpUserTaskAssignmentRequest.class);
  }
}
