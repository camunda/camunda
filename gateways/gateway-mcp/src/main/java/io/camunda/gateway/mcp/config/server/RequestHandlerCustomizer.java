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
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import java.lang.reflect.Field;
import java.util.Map;
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
    final var requestHandlers = getRequestHandlers(transport);

    requestHandlers.put(McpSchema.METHOD_TOOLS_LIST, toolsListRequestHandler(toolRepository));
    requestHandlers.put(
        McpSchema.METHOD_TOOLS_CALL, toolsCallRequestHandler(toolRepository, jsonMapper));
  }

  private static McpStatelessRequestHandler<McpSchema.ListToolsResult> toolsListRequestHandler(
      final ToolRepository toolRepository) {
    return (ctx, params) ->
        Mono.just(new McpSchema.ListToolsResult(toolRepository.getTools(ctx), null));
  }

  private static McpStatelessRequestHandler<CallToolResult> toolsCallRequestHandler(
      final ToolRepository toolRepository, final JsonMapper jsonMapper) {
    return (ctx, params) -> {
      final McpSchema.CallToolRequest callToolRequest =
          jsonMapper.convertValue(params, new TypeReference<>() {});

      final Either<String, SyncToolSpecification> toolSpecification =
          toolRepository.findTool(ctx, callToolRequest.name());

      return toolSpecification.fold(
          errorMessage ->
              Mono.error(
                  McpError.builder(McpSchema.ErrorCodes.INVALID_PARAMS)
                      .message("Unknown tool: invalid_tool_name")
                      .data(errorMessage)
                      .build()),
          spec -> {
            if (spec == null) {
              return Mono.error(
                  McpError.builder(McpSchema.ErrorCodes.INVALID_PARAMS)
                      .message("Unknown tool: invalid_tool_name")
                      .data("Tool not found: " + callToolRequest.name())
                      .build());
            }
            return Mono.fromCallable(() -> spec.callHandler().apply(ctx, callToolRequest));
          });
    };
  }

  @SuppressWarnings("unchecked")
  private static Map<String, McpStatelessRequestHandler<?>> getRequestHandlers(
      final McpStatelessServerTransport transport) {
    try {
      final Object mcpHandler = fieldValue(transport, MCP_HANDLER_FIELD);
      return (Map<String, McpStatelessRequestHandler<?>>)
          fieldValue(mcpHandler, REQUEST_HANDLERS_FIELD);
    } catch (final ReflectiveOperationException e) {
      throw new IllegalStateException(
          "Failed to replace MCP tool request handlers via reflection", e);
    }
  }

  private static Object fieldValue(final Object target, final String fieldName)
      throws ReflectiveOperationException {
    final Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(target);
  }
}
