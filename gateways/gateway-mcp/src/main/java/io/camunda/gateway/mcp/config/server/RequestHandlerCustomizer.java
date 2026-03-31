/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config.server;

import io.camunda.zeebe.util.Either;
import io.modelcontextprotocol.server.McpStatelessRequestHandler;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ErrorCodes;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import java.util.Map;
import java.util.Optional;
import org.springframework.util.ReflectionUtils;
import reactor.core.publisher.Mono;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Replaces the MCP core {@code tools/list} and {@code tools/call} request handlers so tools are
 * resolved from a {@link ToolRepository} for each request.
 */
public final class RequestHandlerCustomizer {

  private static final String MCP_HANDLER_FIELD = "mcpHandler";
  private static final String REQUEST_HANDLERS_FIELD = "requestHandlers";

  private RequestHandlerCustomizer() {}

  public static void replaceToolHandlers(
      final McpStatelessServerTransport transport,
      final JsonMapper jsonMapper,
      final ToolRepository toolRepository) {
    try {
      // extract the modifiable request handler map from the transport
      final var requestHandlers = getRequestHandlers(transport);
      // replace the handlers for tool list and call with repository-based ones
      requestHandlers.put(McpSchema.METHOD_TOOLS_LIST, toolsListRequestHandler(toolRepository));
      requestHandlers.put(
          McpSchema.METHOD_TOOLS_CALL, toolsCallRequestHandler(toolRepository, jsonMapper));
    } catch (final Exception e) {
      throw new IllegalStateException("Cannot replace request handlers in MCP transport", e);
    }
  }

  private static McpStatelessRequestHandler<McpSchema.ListToolsResult> toolsListRequestHandler(
      final ToolRepository toolRepository) {
    // load tools via repository and wrap in callable to pass exceptions in a non-blocking way
    return (ctx, params) ->
        Mono.fromCallable(() -> new McpSchema.ListToolsResult(toolRepository.getTools(ctx), null));
  }

  private static McpStatelessRequestHandler<CallToolResult> toolsCallRequestHandler(
      final ToolRepository toolRepository, final JsonMapper jsonMapper) {
    return (ctx, params) -> {
      // convert the tool call parameters
      final McpSchema.CallToolRequest callToolRequest;
      try {
        callToolRequest = jsonMapper.convertValue(params, new TypeReference<>() {});
      } catch (final Exception e) {
        return Mono.error(
            McpError.builder(McpSchema.ErrorCodes.INVALID_PARAMS)
                .message("Invalid parameters for tool call")
                .data(e.getMessage())
                .build());
      }

      // find the respective tool spec in the repository
      final Either<String, SyncToolSpecification> toolSpecification;
      try {
        toolSpecification = toolRepository.findTool(ctx, callToolRequest.name());
      } catch (final Exception e) {
        // internal error in the tool finding
        return Mono.error(
            McpError.builder(ErrorCodes.INTERNAL_ERROR)
                .message("Error on finding tool")
                .data(e.getMessage())
                .build());
      }

      // invoke the identified tool spec, if found
      return toolSpecification.fold(
          // no tool found, further details in the message
          errorMessage ->
              Mono.error(
                  McpError.builder(McpSchema.ErrorCodes.INVALID_PARAMS)
                      .message("Unknown tool: invalid_tool_name")
                      .data(errorMessage)
                      .build()),
          // no tool found, no further details
          spec -> {
            if (spec == null) {
              return Mono.error(
                  McpError.builder(McpSchema.ErrorCodes.INVALID_PARAMS)
                      .message("Unknown tool: invalid_tool_name")
                      .data("Tool not found: " + callToolRequest.name())
                      .build());
            }
            // tool found, execute it
            return Mono.fromCallable(() -> spec.callHandler().apply(ctx, callToolRequest));
          });
    };
  }

  @SuppressWarnings("unchecked")
  private static Map<String, McpStatelessRequestHandler<?>> getRequestHandlers(
      final McpStatelessServerTransport transport) {
    final McpStatelessServerHandler mcpHandler =
        fieldValue(transport, MCP_HANDLER_FIELD, McpStatelessServerHandler.class);
    return (Map<String, McpStatelessRequestHandler<?>>)
        fieldValue(mcpHandler, REQUEST_HANDLERS_FIELD, Map.class);
  }

  private static <T> T fieldValue(
      final Object target, final String fieldName, final Class<T> expectedType) {
    try {
      return Optional.ofNullable(
              ReflectionUtils.findField(target.getClass(), fieldName, expectedType))
          .map(
              field -> {
                ReflectionUtils.makeAccessible(field);
                final var fieldValue = ReflectionUtils.getField(field, target);
                return expectedType.cast(fieldValue);
              })
          .orElseThrow(() -> new IllegalStateException("Field '" + fieldName + "' not found"));
    } catch (final Exception ex) {
      throw new IllegalStateException("Field '" + fieldName + "' cannot be fetched");
    }
  }
}
