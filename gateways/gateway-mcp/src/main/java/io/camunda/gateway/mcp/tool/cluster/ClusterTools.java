/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.cluster;

import io.camunda.authentication.pt.PhysicalTenantContext;
import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.service.registry.ServiceRegistry;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class ClusterTools {

  private final ServiceRegistry serviceRegistry;

  public ClusterTools(final ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }

  @CamundaMcpTool(
      description =
          "Checks the health status of the cluster by verifying if there's at least one partition with a healthy leader.",
      annotations = @McpTool.McpAnnotations(readOnlyHint = true))
  public CallToolResult getClusterStatus() {
    return CallToolResultMapper.fromPrimitive(
        serviceRegistry.topologyServices(PhysicalTenantContext.current()).getStatus(), Enum::name);
  }

  @CamundaMcpTool(
      description = "Obtains the current topology of the cluster the gateway is part of.",
      annotations = @McpTool.McpAnnotations(readOnlyHint = true))
  public CallToolResult getTopology() {
    return CallToolResultMapper.from(
        serviceRegistry.topologyServices(PhysicalTenantContext.current()).getTopology(),
        ResponseMapper::toTopologyResponse);
  }
}
