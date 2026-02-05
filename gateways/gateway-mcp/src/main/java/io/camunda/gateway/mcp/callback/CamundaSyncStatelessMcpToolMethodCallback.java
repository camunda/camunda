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
      ReturnMode returnMode, Method toolMethod, Object toolObject) {
    super(returnMode, toolMethod, toolObject, Exception.class);
  }

  public CamundaSyncStatelessMcpToolMethodCallback(
      ReturnMode returnMode,
      Method toolMethod,
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

  /**
   * Cleans up validation property paths by removing internal method and parameter names.
   *
   * <p>Uses actual method metadata to determine if a parameter has @McpRequestBody annotation,
   * making this approach robust regardless of parameter naming or nesting depth.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>"createTask.request.taskName" → "taskName" (if param at index 1 has @McpRequestBody)
   *   <li>"createTask.request.a.b.c.d" → "a.b.c.d" (deep nesting works)
   *   <li>"createTask.myDto.field" → "field" (any param name works)
   *   <li>"createTask.instruction.key" → "instruction.key" (standard param without @McpRequestBody)
   * </ul>
   *
   * @param propertyPath the validation property path to clean
   * @return cleaned property path suitable for user-facing error messages
   */
  private String cleanPropertyPath(String propertyPath) {
    String[] parts = propertyPath.split("\\.");

    if (parts.length <= 1) {
      return propertyPath; // No dots, return as-is
    }

    // parts[0] = method name
    // parts[1] = parameter name (might be arg0, arg1, etc. or actual name if -parameters flag is
    // set)
    // parts[2+] = nested field path (if any)

    String paramName = parts[1];

    // Check all parameters to see if any has @McpRequestBody
    // We can't rely on parameter name matching since Java doesn't always preserve names
    // Instead, check if ANY parameter has @McpRequestBody and assume the property path refers to it
    // This is safe because validation paths for @McpRequestBody will have 3+ parts
    // (method.param.field)
    boolean hasRequestBodyParam =
        Stream.of(toolMethod.getParameters())
            .anyMatch(p -> p.isAnnotationPresent(McpRequestBody.class));

    if (hasRequestBodyParam && parts.length >= 3) {
      // If method has @McpRequestBody parameter and path has 3+ parts, assume this is it
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

  @Override
  public CallToolResult apply(
      McpTransportContext mcpTransportContext, CallToolRequest callToolRequest) {
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
    } catch (ConstraintViolationException e) {
      // Reformat validation error messages to remove internal parameter names
      String reformattedMessage =
          e.getConstraintViolations().stream()
              .map(
                  violation ->
                      cleanPropertyPath(violation.getPropertyPath().toString())
                          + ": "
                          + violation.getMessage())
              .collect(Collectors.joining(", "));

      return this.createSyncErrorResult(new IllegalArgumentException(reformattedMessage));
    } catch (Exception e) {
      // Check if the exception wraps a ConstraintViolationException
      Throwable cause = e.getCause();
      if (cause instanceof ConstraintViolationException cve) {
        // Reformat validation error messages to remove internal parameter names
        String reformattedMessage =
            cve.getConstraintViolations().stream()
                .map(
                    violation ->
                        cleanPropertyPath(violation.getPropertyPath().toString())
                            + ": "
                            + violation.getMessage())
                .collect(Collectors.joining(", "));

        return this.createSyncErrorResult(new IllegalArgumentException(reformattedMessage));
      }

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
                // Use parent's buildTypedArgument to convert the entire arguments map into the DTO
                // type
                // Spring's @Validated proxy will validate this if the parameter has @Valid
                return buildTypedArgument(toolInputArguments, parameter.getParameterizedType());
              }

              // Check for context types
              if (McpSyncRequestContext.class.isAssignableFrom(parameter.getType())
                  || McpAsyncRequestContext.class.isAssignableFrom(parameter.getType())) {
                return this.createRequestContext(exchangeOrContext, request);
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
              Object rawArgument = toolInputArguments.get(parameter.getName());
              return buildTypedArgument(rawArgument, parameter.getParameterizedType());
            })
        .toArray();
  }
}
