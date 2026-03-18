/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.mcp.model.McpIncidentFilter;
import io.camunda.gateway.mcp.model.McpProcessDefinitionFilter;
import io.camunda.gateway.mcp.model.McpProcessInstanceCreationInstruction;
import io.camunda.gateway.mcp.model.McpProcessInstanceFilter;
import io.camunda.gateway.mcp.model.McpUserTaskAssignmentRequest;
import io.camunda.gateway.mcp.model.McpUserTaskFilter;
import io.camunda.gateway.mcp.model.McpVariableFilter;
import io.camunda.gateway.protocol.model.simple.IncidentFilter;
import io.camunda.gateway.protocol.model.simple.ProcessDefinitionFilter;
import io.camunda.gateway.protocol.model.simple.ProcessInstanceCreationInstruction;
import io.camunda.gateway.protocol.model.simple.ProcessInstanceFilter;
import io.camunda.gateway.protocol.model.simple.UserTaskAssignmentRequest;
import io.camunda.gateway.protocol.model.simple.UserTaskFilter;
import io.camunda.gateway.protocol.model.simple.VariableFilter;
import java.util.Map;

/**
 * Single source of truth for MCP mixin registrations.
 *
 * <p>Mixins hide internal fields (e.g., {@code tenantId}) from MCP tool schemas and
 * deserialization. They must be registered with both Jackson 2 (for victools schema generation) and
 * Jackson 3 (for Spring AI's {@code JsonParser} tool parameter handling).
 *
 * @see CamundaMcpJackson3Module
 */
public final class McpMixinRegistry {

  static final Map<Class<?>, Class<?>> MIXINS =
      Map.of(
          IncidentFilter.class, McpIncidentFilter.class,
          ProcessDefinitionFilter.class, McpProcessDefinitionFilter.class,
          ProcessInstanceCreationInstruction.class, McpProcessInstanceCreationInstruction.class,
          ProcessInstanceFilter.class, McpProcessInstanceFilter.class,
          UserTaskAssignmentRequest.class, McpUserTaskAssignmentRequest.class,
          UserTaskFilter.class, McpUserTaskFilter.class,
          VariableFilter.class, McpVariableFilter.class);

  private McpMixinRegistry() {}

  /** Register all MCP mixins on a Jackson 2 {@link ObjectMapper} (used for schema generation). */
  public static void registerMixins(final ObjectMapper objectMapper) {
    MIXINS.forEach(objectMapper::addMixIn);
  }

  /**
   * Register all MCP mixins on a Jackson 3 {@link tools.jackson.databind.module.SimpleModule} (used
   * for SPI-based registration with Spring AI's JsonParser).
   */
  public static void registerMixins(final tools.jackson.databind.module.SimpleModule module) {
    MIXINS.forEach(module::setMixInAnnotation);
  }
}
