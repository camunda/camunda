/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.cluster;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.TopologyServices;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
public class ClusterTools {

  private final TopologyServices topologyServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ClusterTools(
      final TopologyServices topologyServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.topologyServices = topologyServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaMcpTool(
      description =
          "Checks the health status of the cluster by verifying if there's at least one partition with a healthy leader.",
      annotations = @McpTool.McpAnnotations(readOnlyHint = true))
  public CallToolResult getClusterStatus() {
    return CallToolResultMapper.fromPrimitive(
        topologyServices
            .withAuthentication(authenticationProvider.getCamundaAuthentication())
            .getStatus(),
        Enum::name);
  }

  @CamundaMcpTool(
      description = "Obtains the current topology of the cluster the gateway is part of.",
      annotations = @McpTool.McpAnnotations(readOnlyHint = true))
  public CallToolResult getTopology() {
    return CallToolResultMapper.from(
        topologyServices
            .withAuthentication(authenticationProvider.getCamundaAuthentication())
            .getTopology(),
        ResponseMapper::toTopologyResponse);
  }
}
