/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.tool.cluster;

import io.camunda.zeebe.gateway.protocol.rest.TopologyResponse;
import io.camunda.zeebe.gateway.rest.controller.StatusController;
import io.camunda.zeebe.gateway.rest.controller.TopologyController;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ClusterTools {

  private final StatusController statusController;
  private final TopologyController topologyController;

  public ClusterTools(
      final StatusController statusController, final TopologyController topologyController) {
    this.statusController = statusController;
    this.topologyController = topologyController;
  }

  @McpTool(
      description =
          "Checks the health status of the cluster by verifying if there's at least one partition with a healthy leader.",
      annotations = @McpTool.McpAnnotations(readOnlyHint = true))
  public String getClusterStatus() {
    final var statusResponse = statusController.getStatus();
    return switch (statusResponse.getStatusCode()) {
      case HttpStatus.NO_CONTENT -> "HEALTHY";
      case HttpStatus.SERVICE_UNAVAILABLE -> "UNHEALTHY";
      default -> "UNKNOWN";
    };
  }

  @McpTool(
      description = "Obtains the current topology of the cluster the gateway is part of.",
      annotations = @McpTool.McpAnnotations(readOnlyHint = true))
  public TopologyResponse getTopology() {
    return topologyController.get();
  }
}
