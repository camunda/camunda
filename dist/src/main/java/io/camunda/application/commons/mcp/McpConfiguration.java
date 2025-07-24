/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.mcp;

import io.camunda.zeebe.gateway.mcp.ConditionalOnMcpGatewayEnabled;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "io.camunda.zeebe.gateway.mcp")
@ConditionalOnMcpGatewayEnabled
public class McpConfiguration {
  /**
   * Enables automatic context propagation in Reactor, which allows the ThreadLocals to be restored
   * automatically for each new subscription. We need this to ensure that the Spring Authentication
   * context is shared with all MCP tools registered as they are using Reactor behind the scene to
   * orchestrate tool calls.
   */
  @PostConstruct
  void enableContextPropagation() {
    // after this, every new subscription restores the ThreadLocals that
    // were present when the chain was assembled
    reactor.core.publisher.Hooks.enableAutomaticContextPropagation();
  }
}
