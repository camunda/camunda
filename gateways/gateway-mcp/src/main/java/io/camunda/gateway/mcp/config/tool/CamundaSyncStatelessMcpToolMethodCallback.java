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
import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpProgressToken;
import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.mcp.annotation.method.tool.AbstractSyncMcpToolMethodCallback;
import org.springframework.ai.mcp.annotation.method.tool.ReturnMode;

/**
 * Camunda-specific callback for synchronous stateless MCP tool methods.
 *
 * <p>Extends the abstract callback to support {@link McpToolParamsUnwrapped} annotation, which
 * allows method parameters annotated with {@code @McpToolParamsUnwrapped} to be deserialized from
 * the entire tool input arguments map.
 *
 * <p>Original Spring AI implementation: {@link
 * org.springframework.ai.mcp.annotation.method.tool.SyncStatelessMcpToolMethodCallback}
 */
public class CamundaSyncStatelessMcpToolMethodCallback
    extends AbstractSyncMcpToolMethodCallback<McpTransportContext, McpSyncRequestContext>
    implements BiFunction<McpTransportContext, CallToolRequest, CallToolResult> {

  private final boolean hasWrapperParameter;

  public CamundaSyncStatelessMcpToolMethodCallback(
      final ReturnMode returnMode,
      final Method toolMethod,
      final Object toolObject,
      final Class<? extends Throwable> toolCallExceptionClass) {
    super(returnMode, toolMethod, toolObject, toolCallExceptionClass);
    hasWrapperParameter =
        Stream.of(toolMethod.getParameters())
            .anyMatch(p -> p.isAnnotationPresent(McpToolParamsUnwrapped.class));
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
      // build arguments for the method call
      final Object[] methodArguments =
          buildMethodArguments(mcpTransportContext, callToolRequest.arguments(), callToolRequest);

      // invoke the method
      final Object result = callMethod(methodArguments);

      return processResult(result);
    } catch (final Exception e) {
      if (toolCallExceptionClass.isInstance(e)) {
        // check if this exception or its cause is a ConstraintViolationException
        final ConstraintViolationException cve = findConstraintViolationException(e);
        if (cve != null) {
          return createConstraintViolationSyncErrorResult(cve);
        }

        return createSyncErrorResult(e);
      }

      throw e;
    }
  }

  private ConstraintViolationException findConstraintViolationException(final Throwable e) {
    Throwable current = e;
    while (current != null) {
      if (current instanceof ConstraintViolationException cve) {
        return cve;
      }
      current = current.getCause() != current ? current.getCause() : null;
    }
    return null;
  }

  private CallToolResult createConstraintViolationSyncErrorResult(
      final ConstraintViolationException cve) {
    return CallToolResult.builder()
        .isError(true)
        .addTextContent(normalizeConstraintViolationMessage(cve))
        .build();
  }

  @Override
  protected Object[] buildMethodArguments(
      final McpTransportContext exchangeOrContext,
      final Map<String, Object> toolInputArguments,
      final CallToolRequest request) {

    return Stream.of(toolMethod.getParameters())
        .map(
            parameter -> {
              // if @McpToolParamsUnwrapped is present, deserialize the entire input map into
              // wrapper DTO
              if (parameter.isAnnotationPresent(McpToolParamsUnwrapped.class)) {
                // Spring's @Validated proxy will validate this the parameter based on validation
                // annotations
                return buildTypedArgument(toolInputArguments, parameter.getParameterizedType());
              }

              if (McpSyncRequestContext.class.isAssignableFrom(parameter.getType())
                  || McpAsyncRequestContext.class.isAssignableFrom(parameter.getType())) {
                return createRequestContext(exchangeOrContext, request);
              }

              if (parameter.isAnnotationPresent(McpProgressToken.class)) {
                return request != null ? request.progressToken() : null;
              }

              if (McpMeta.class.isAssignableFrom(parameter.getType())) {
                return request != null ? new McpMeta(request.meta()) : new McpMeta(null);
              }

              if (CallToolRequest.class.isAssignableFrom(parameter.getType())) {
                return request;
              }

              if (isExchangeOrContextType(parameter.getType())) {
                return exchangeOrContext;
              }

              // standard parameter - look up by name and deserialize
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

  @Override
  protected McpTransportContext resolveTransportContext(final McpTransportContext exchange) {
    throw new UnsupportedOperationException(
        "Stateless tool methods do not support McpTransportContext parameter.");
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
   * <p>When the method uses {@code @McpToolParamsUnwrapped}, the wrapper parameter name is an
   * internal detail and must be stripped. Since {@code @McpToolParamsUnwrapped} is mutually
   * exclusive with individual parameters (enforced at registration time), the mode is unambiguous.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>Wrapper mode: "createTask.request.taskName" → "taskName"
   *   <li>Wrapper mode: "createTask.request.a.b.c" → "a.b.c"
   *   <li>Individual mode: "createTask.key" → "key"
   * </ul>
   *
   * @param propertyPath the validation property path to clean
   * @return cleaned property path suitable for user-facing error messages
   */
  private String normalizeViolationPropertyPath(final String propertyPath) {
    final String[] parts = propertyPath.split("\\.");

    if (parts.length <= 1) {
      return propertyPath;
    }

    // parts[0] = method name (always strip)
    // parts[1] = parameter name (strip additionally in wrapper mode)
    final int prefixLength = hasWrapperParameter ? 2 : 1;
    if (parts.length <= prefixLength) {
      return propertyPath;
    }

    return String.join(".", Arrays.copyOfRange(parts, prefixLength, parts.length));
  }
}
