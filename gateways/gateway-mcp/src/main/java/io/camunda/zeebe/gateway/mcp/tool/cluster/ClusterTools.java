/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.tool.cluster;

import io.camunda.service.TopologyServices;
import io.camunda.zeebe.gateway.mcp.mapper.CallToolResultMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
public class ClusterTools {

  private final TopologyServices topologyServices;

  public ClusterTools(final TopologyServices topologyServices) {
    this.topologyServices = topologyServices;
  }

  @McpTool(
      description =
          "Checks the health status of the cluster by verifying if there's at least one partition with a healthy leader.",
      annotations = @McpTool.McpAnnotations(readOnlyHint = true))
  public CallToolResult getClusterStatus() {
    return CallToolResultMapper.fromPrimitive(topologyServices.getStatus(), Enum::name);
  }

  @McpTool(
      description = "Obtains the current topology of the cluster the gateway is part of.",
      annotations = @McpTool.McpAnnotations(readOnlyHint = true))
  public CallToolResult getTopology() {
    return CallToolResultMapper.from(topologyServices.getTopology(), t -> t);
  }
}
