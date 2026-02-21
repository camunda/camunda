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
import org.springaicommunity.mcp.method.tool.utils.JsonParser;

/**
 * Utility class for configuring the ObjectMapper used for MCP tool method parameter and result
 * deserialization/serialization.
 *
 * <p>This class registers Camunda-specific mix-ins to ensure that the correct concrete classes are
 * used when deserializing interfaces defined in the protocol model. This is necessary for proper
 * integration with Spring AI's MCP tool method handling, which relies on Jackson for JSON parsing.
 *
 * <p>This utility uses Spring AI's JsonParser to obtain the ObjectMapper instance, ensuring that it
 * is configured consistently with Spring AI's expectations and any customizations that may be
 * applied there. This way, it is used by Spring AI's MCP tool method callbacks without needing to
 * create a separate ObjectMapper instance, which could lead to configuration inconsistencies.
 *
 * <p>Until Spring AI provides a more flexible way to configure the ObjectMapper (e.g., through a
 * customizer or conditional bean), this utility serves as a centralized place to manage the
 * necessary mix-ins for Camunda's MCP integration.
 */
public final class McpObjectMapperUtilities {

  private static final ObjectMapper OBJECT_MAPPER = JsonParser.getObjectMapper();

  static {
    OBJECT_MAPPER
        .addMixIn(IncidentFilter.class, McpIncidentFilter.class)
        .addMixIn(ProcessDefinitionFilter.class, McpProcessDefinitionFilter.class)
        .addMixIn(
            ProcessInstanceCreationInstruction.class, McpProcessInstanceCreationInstruction.class)
        .addMixIn(ProcessInstanceFilter.class, McpProcessInstanceFilter.class)
        .addMixIn(UserTaskAssignmentRequest.class, McpUserTaskAssignmentRequest.class)
        .addMixIn(UserTaskFilter.class, McpUserTaskFilter.class)
        .addMixIn(VariableFilter.class, McpVariableFilter.class);
  }

  private McpObjectMapperUtilities() {}

  /**
   * @return the configured ObjectMapper instance with Camunda-specific mix-ins
   */
  public static ObjectMapper getObjectMapper() {
    return OBJECT_MAPPER;
  }
}
