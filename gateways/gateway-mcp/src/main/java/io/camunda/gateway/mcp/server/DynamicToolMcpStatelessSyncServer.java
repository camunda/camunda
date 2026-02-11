/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.server;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import reactor.core.publisher.Mono;

public class DynamicToolMcpStatelessSyncServer {

  private final DynamicToolsMcpStatelessAsyncServer asyncServer;

  DynamicToolMcpStatelessSyncServer(final DynamicToolsMcpStatelessAsyncServer asyncServer) {
    this.asyncServer = asyncServer;
  }

  /**
   * Get the server capabilities that define the supported features and functionality.
   *
   * @return The server capabilities
   */
  public McpSchema.ServerCapabilities getServerCapabilities() {
    return asyncServer.getServerCapabilities();
  }

  /**
   * Get the server implementation information.
   *
   * @return The server implementation details
   */
  public McpSchema.Implementation getServerInfo() {
    return asyncServer.getServerInfo();
  }

  /**
   * Gracefully closes the server, allowing any in-progress operations to complete.
   *
   * @return A Mono that completes when the server has been closed
   */
  public Mono<Void> closeGracefully() {
    return asyncServer.closeGracefully();
  }

  /** Close the server immediately. */
  public void close() {
    asyncServer.close();
  }

  /**
   * List all registered tools.
   *
   * @return A list of all registered tools
   */
  public List<Tool> listTools() {
    return asyncServer.listTools().collectList().block();
  }

  /**
   * This method is package-private and used for test only. Should not be called by user code.
   *
   * @param protocolVersions the Client supported protocol versions.
   */
  void setProtocolVersions(final List<String> protocolVersions) {
    asyncServer.setProtocolVersions(protocolVersions);
  }
}
