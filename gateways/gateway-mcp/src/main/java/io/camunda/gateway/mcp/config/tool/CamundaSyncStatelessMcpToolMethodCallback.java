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
import jakarta.validation.ConstraintViolationException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springaicommunity.mcp.annotation.McpMeta;
import org.springaicommunity.mcp.annotation.McpProgressToken;
import org.springaicommunity.mcp.context.McpAsyncRequestContext;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import org.springaicommunity.mcp.method.tool.AbstractSyncMcpToolMethodCallback;
import org.springaicommunity.mcp.method.tool.ReturnMode;
import org.springaicommunity.mcp.method.tool.SyncStatelessMcpToolMethodCallback;

/**
 * Camunda-specific callback for synchronous stateless MCP tool methods.
 *
 * <p>Extends the abstract callback to support {@link McpToolParams} annotation, which allows method
 * parameters annotated with {@code @McpToolParams} to be deserialized from the entire tool input
 * arguments map.
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
    } catch (final ConstraintViolationException cve) {
      return createSyncErrorResult(
          new IllegalArgumentException(normalizeConstraintViolationMessage(cve)));
    } catch (final Exception e) {
      if (e.getCause() instanceof final ConstraintViolationException cve) {
        return createSyncErrorResult(
            new IllegalArgumentException(normalizeConstraintViolationMessage(cve)));
      }

      if (toolCallExceptionClass.isInstance(e)) {
        return createSyncErrorResult(e);
      }

      throw e;
    }
  }

  @Override
  protected Object[] buildMethodArguments(
      final McpTransportContext exchangeOrContext,
      final Map<String, Object> toolInputArguments,
      final CallToolRequest request) {

    return Stream.of(toolMethod.getParameters())
        .map(
            parameter -> {
              // Check for @McpToolParams - deserialize entire input map into DTO
              if (parameter.isAnnotationPresent(McpToolParams.class)) {
                // Use parent's buildTypedArgument to convert the entire arguments map into the DTO
                // type
                // Spring's @Validated proxy will validate this if the parameter has @Valid
                return buildTypedArgument(toolInputArguments, parameter.getParameterizedType());
              }

              // Check for context types
              if (McpSyncRequestContext.class.isAssignableFrom(parameter.getType())
                  || McpAsyncRequestContext.class.isAssignableFrom(parameter.getType())) {
                return createRequestContext(exchangeOrContext, request);
              }

              // Check for @McpProgressToken
              if (parameter.isAnnotationPresent(McpProgressToken.class)) {
                return request != null ? request.progressToken() : null;
              }

              // Check for McpMeta
              if (McpMeta.class.isAssignableFrom(parameter.getType())) {
                return request != null ? new McpMeta(request.meta()) : new McpMeta(null);
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
              final Object rawArgument = toolInputArguments.get(parameter.getName());
              return buildTypedArgument(rawArgument, parameter.getParameterizedType());
            })
        .toArray();
  }

  @Override
  protected McpSyncRequestContext createRequestContext(
      final McpTransportContext exchange, final CallToolRequest request) {
    throw new UnsupportedOperationException(
        "Stateless tool methods do not support McpSyncRequestContext parameter.");
  }

  private String normalizeConstraintViolationMessage(
      final ConstraintViolationException constraintViolationException) {
    return constraintViolationException.getConstraintViolations().stream()
        .map(
            violation ->
                normalizeViolationPropertyPath(violation.getPropertyPath().toString())
                    + ": "
                    + violation.getMessage())
        .collect(Collectors.joining(", "));
  }

  /**
   * Cleans up validation property paths by removing internal method and parameter names.
   *
   * <p>Uses actual method metadata to determine if a parameter has @McpToolParams annotation,
   * making this approach robust regardless of parameter naming or nesting depth.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>"createTask.request.taskName" → "taskName" (if param at index 1 has @McpToolParams)
   *   <li>"createTask.request.a.b.c.d" → "a.b.c.d" (deep nesting works)
   *   <li>"createTask.myDto.field" → "field" (any param name works)
   *   <li>"createTask.instruction.key" → "instruction.key" (standard param without @McpToolParams)
   * </ul>
   *
   * @param propertyPath the validation property path to clean
   * @return cleaned property path suitable for user-facing error messages
   */
  private String normalizeViolationPropertyPath(final String propertyPath) {
    final String[] parts = propertyPath.split("\\.");

    if (parts.length <= 1) {
      return propertyPath; // No dots, return as-is
    }

    // parts[0] = method name
    // parts[1] = parameter name (might be arg0, arg1, etc. or actual name if -parameters flag is
    // set)
    // parts[2+] = nested field path (if any)

    final String paramName = parts[1];

    // Check all parameters to see if any has @McpToolParams
    // We can't rely on parameter name matching since Java doesn't always preserve names
    // Instead, check if ANY parameter has @McpToolParams and assume the property path refers to it
    // This is safe because validation paths for @McpToolParams will have 3+ parts
    // (method.param.field)
    final boolean hasRequestBodyParam =
        Stream.of(toolMethod.getParameters())
            .anyMatch(p -> p.isAnnotationPresent(McpToolParams.class));

    if (hasRequestBodyParam && parts.length >= 3) {
      // If method has @McpToolParams parameter and path has 3+ parts, assume this is it
      // Remove method name AND parameter name
      // "methodName.paramName.field..." → "field..."
      return String.join(".", Arrays.copyOfRange(parts, 2, parts.length));
    } else {
      // Standard parameter or simple property path
      // Remove only method name
      // "methodName.paramName..." → "paramName..."
      return String.join(".", Arrays.copyOfRange(parts, 1, parts.length));
    }
  }
}
