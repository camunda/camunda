/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config.tool;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import java.lang.reflect.Parameter;
import java.util.Set;
import org.springaicommunity.mcp.annotation.McpMeta;
import org.springaicommunity.mcp.annotation.McpProgressToken;
import org.springaicommunity.mcp.context.McpAsyncRequestContext;
import org.springaicommunity.mcp.context.McpSyncRequestContext;

public final class McpToolUtils {

  private static final Set<Class<?>> MCP_FRAMEWORK_TYPES =
      Set.of(
          CallToolRequest.class,
          McpSyncRequestContext.class,
          McpAsyncRequestContext.class,
          McpSyncServerExchange.class,
          McpAsyncServerExchange.class,
          McpTransportContext.class,
          McpMeta.class);

  private McpToolUtils() {}

  public static boolean isFrameworkParameter(final Parameter parameter) {
    if (parameter.isAnnotationPresent(McpProgressToken.class)) {
      return true;
    }

    final Class<?> type = parameter.getType();
    return MCP_FRAMEWORK_TYPES.stream()
        .anyMatch(frameworkType -> frameworkType.isAssignableFrom(type));
  }
}
