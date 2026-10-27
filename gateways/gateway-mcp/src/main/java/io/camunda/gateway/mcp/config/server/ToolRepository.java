/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config.server;

import io.camunda.zeebe.util.Either;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import org.jspecify.annotations.NonNull;

/** Repository abstraction for resolving sync MCP tools at request time. */
public interface ToolRepository {

  /**
   * Returns the currently available tools for the given request context.
   *
   * <p>Implementations may inspect the transport context to scope tools to the authenticated user
   * or request-specific data.
   */
  @NonNull List<Tool> getTools(@NonNull McpTransportContext transportContext);

  /**
   * Finds the executable tool specification for the given request context and tool name.
   *
   * <p>If a tool exists, it is returned as a {@link io.camunda.zeebe.util.Either.Right}. Otherwise,
   * an error message is returned as a {@link io.camunda.zeebe.util.Either.Left}, with details on
   * the tool that couldn't be found.
   *
   * <p>Internal errors are thrown as runtime exceptions, which are handled by the consumer.
   */
  @NonNull Either<String, SyncToolSpecification> findTool(
      @NonNull McpTransportContext transportContext, @NonNull String toolName);
}
