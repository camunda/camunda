/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.callback;

import io.camunda.gateway.mcp.annotation.McpRequestBody;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import org.springaicommunity.mcp.method.tool.AbstractSyncMcpToolMethodCallback;
import org.springaicommunity.mcp.method.tool.ReturnMode;
import org.springaicommunity.mcp.method.tool.utils.JsonParser;

/**
 * Camunda-specific callback for synchronous stateless MCP tool methods.
 *
 * <p>Extends the abstract callback to support {@link McpRequestBody} annotation, which allows
 * method parameters annotated with {@code @McpRequestBody} to be deserialized from the entire tool
 * input arguments map.
 */
public class CamundaSyncStatelessMcpToolMethodCallback
    extends AbstractSyncMcpToolMethodCallback<McpTransportContext, McpSyncRequestContext>
    implements BiFunction<McpTransportContext, CallToolRequest, CallToolResult> {

  public CamundaSyncStatelessMcpToolMethodCallback(
      ReturnMode returnMode, java.lang.reflect.Method toolMethod, Object toolObject) {
    super(returnMode, toolMethod, toolObject, Exception.class);
  }

  public CamundaSyncStatelessMcpToolMethodCallback(
      ReturnMode returnMode,
      java.lang.reflect.Method toolMethod,
      Object toolObject,
      Class<? extends Throwable> toolCallExceptionClass) {
    super(returnMode, toolMethod, toolObject, toolCallExceptionClass);
  }

  @Override
  protected boolean isExchangeOrContextType(Class<?> paramType) {
    return McpTransportContext.class.isAssignableFrom(paramType)
        || McpSyncRequestContext.class.isAssignableFrom(paramType);
  }

  @Override
  protected McpSyncRequestContext createRequestContext(
      McpTransportContext exchange, CallToolRequest request) {
    throw new UnsupportedOperationException(
        "Stateless tool methods do not support McpSyncRequestContext parameter.");
  }

  @Override
  public CallToolResult apply(McpTransportContext mcpTransportContext, CallToolRequest callToolRequest) {
    validateSyncRequest(callToolRequest);

    try {
      // Build arguments for the method call
      Object[] args =
          this.buildMethodArguments(
              mcpTransportContext, callToolRequest.arguments(), callToolRequest);

      // Invoke the method
      Object result = this.callMethod(args);

      // Return the processed result
      return this.processResult(result);
    } catch (Exception e) {
      if (this.toolCallExceptionClass.isInstance(e)) {
        return this.createSyncErrorResult(e);
      }
      throw e;
    }
  }

  @Override
  protected Object[] buildMethodArguments(
      McpTransportContext exchangeOrContext,
      Map<String, Object> toolInputArguments,
      CallToolRequest request) {

    return Stream.of(this.toolMethod.getParameters())
        .map(
            parameter -> {
              // Check for @McpRequestBody - deserialize entire input map into DTO
              if (parameter.isAnnotationPresent(McpRequestBody.class)) {
                return JsonParser.toTypedObject(toolInputArguments, parameter.getType());
              }

              // Check for context types
              if (org.springaicommunity.mcp.context.McpSyncRequestContext.class.isAssignableFrom(
                      parameter.getType())
                  || org.springaicommunity.mcp.context.McpAsyncRequestContext.class
                      .isAssignableFrom(parameter.getType())) {
                return this.createRequestContext(exchangeOrContext, request);
              }

              // Check for @McpProgressToken
              if (parameter.isAnnotationPresent(
                  org.springaicommunity.mcp.annotation.McpProgressToken.class)) {
                return request != null ? request.progressToken() : null;
              }

              // Check for McpMeta
              if (org.springaicommunity.mcp.annotation.McpMeta.class.isAssignableFrom(
                  parameter.getType())) {
                return request != null
                    ? new org.springaicommunity.mcp.annotation.McpMeta(request.meta())
                    : new org.springaicommunity.mcp.annotation.McpMeta(null);
              }

              // Check for CallToolRequest
              if (CallToolRequest.class.isAssignableFrom(parameter.getType())) {
                return request;
              }

              // Check for exchange/context types
              if (isExchangeOrContextType(parameter.getType())) {
                return exchangeOrContext;
              }

              // Standard parameter - look up by name and deserialize
              Object rawArgument = toolInputArguments.get(parameter.getName());
              return buildTypedArgument(rawArgument, parameter.getParameterizedType());
            })
        .toArray();
  }
}
