/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config.tool;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.lang.reflect.Method;
import java.util.function.BiFunction;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import org.springaicommunity.mcp.method.tool.AbstractSyncMcpToolMethodCallback;
import org.springaicommunity.mcp.method.tool.ReturnMode;
import org.springaicommunity.mcp.method.tool.SyncStatelessMcpToolMethodCallback;

/**
 * Camunda-specific callback for synchronous stateless MCP tool methods.
 *
 * <p>Original Spring AI implementation: {@link SyncStatelessMcpToolMethodCallback}
 */
public class CamundaSyncStatelessMcpToolMethodCallback
    extends AbstractSyncMcpToolMethodCallback<McpTransportContext, McpSyncRequestContext>
    implements BiFunction<McpTransportContext, CallToolRequest, CallToolResult> {

  public CamundaSyncStatelessMcpToolMethodCallback(
      final ReturnMode returnMode,
      final Method toolMethod,
      final Object toolObject,
      final Class<? extends Throwable> toolCallExceptionClass) {
    super(returnMode, toolMethod, toolObject, toolCallExceptionClass);
  }

  @Override
  protected boolean isExchangeOrContextType(final Class<?> paramType) {
    return McpTransportContext.class.isAssignableFrom(paramType)
        || McpSyncRequestContext.class.isAssignableFrom(paramType);
  }

  @Override
  public CallToolResult apply(
      final McpTransportContext mcpTransportContext, final CallToolRequest callToolRequest) {
    validateSyncRequest(callToolRequest);

    try {
      // Build arguments for the method call
      final Object[] args =
          buildMethodArguments(mcpTransportContext, callToolRequest.arguments(), callToolRequest);

      // Invoke the method
      final Object result = callMethod(args);

      return processResult(result);
    } catch (final Exception e) {
      if (toolCallExceptionClass.isInstance(e)) {
        return createSyncErrorResult(e);
      }

      throw e;
    }
  }

  @Override
  protected McpSyncRequestContext createRequestContext(
      final McpTransportContext exchange, final CallToolRequest request) {
    throw new UnsupportedOperationException(
        "Stateless tool methods do not support McpSyncRequestContext parameter.");
  }
}
