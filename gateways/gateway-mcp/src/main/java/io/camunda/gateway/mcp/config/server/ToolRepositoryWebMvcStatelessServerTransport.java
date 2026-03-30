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
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessRequestHandler;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ErrorCodes;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCNotification;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCRequest;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import java.util.Map;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import reactor.core.publisher.Mono;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Implementation of a WebMVC based {@link McpStatelessServerTransport} that supports using a
 * dynamic tool repository to list and call tools.
 *
 * <p>This is a dynamic version of the {@link WebMvcStatelessServerTransport}.
 */
public final class ToolRepositoryWebMvcStatelessServerTransport
    implements McpStatelessServerTransport {

  private final JsonMapper jsonMapper;

  private final WebMvcStatelessServerTransport delegate;
  private final ToolRepository toolRepository;

  public ToolRepositoryWebMvcStatelessServerTransport(
      final JsonMapper jsonMapper, final ToolRepository toolRepository, final String endpoint) {
    this.jsonMapper = jsonMapper;
    this.toolRepository = toolRepository;
    delegate =
        WebMvcStatelessServerTransport.builder()
            .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
            .messageEndpoint(endpoint)
            .build();
  }

  @Override
  public void setMcpHandler(final McpStatelessServerHandler mcpHandler) {
    delegate.setMcpHandler(
        new McpStatelessServerHandler() {

          private final Map<String, McpStatelessRequestHandler<?>> requestHandlers =
              Map.of(
                  McpSchema.METHOD_TOOLS_LIST,
                  toolsListRequestHandler(toolRepository),
                  McpSchema.METHOD_TOOLS_CALL,
                  toolsCallRequestHandler(toolRepository, jsonMapper));

          @Override
          public Mono<JSONRPCResponse> handleRequest(
              final McpTransportContext transportContext, final JSONRPCRequest request) {
            return switch (request.method()) {
              case McpSchema.METHOD_TOOLS_LIST, McpSchema.METHOD_TOOLS_CALL ->
                  handleRequestInternal(transportContext, request);
              default -> mcpHandler.handleRequest(transportContext, request);
            };
          }

          @Override
          public Mono<Void> handleNotification(
              final McpTransportContext transportContext, final JSONRPCNotification notification) {
            return mcpHandler.handleNotification(transportContext, notification);
          }

          private Mono<McpSchema.JSONRPCResponse> handleRequestInternal(
              final McpTransportContext transportContext, final McpSchema.JSONRPCRequest request) {
            final McpStatelessRequestHandler<?> requestHandler =
                requestHandlers.get(request.method());
            if (requestHandler == null) {
              return Mono.error(
                  McpError.builder(McpSchema.ErrorCodes.METHOD_NOT_FOUND)
                      .message("Missing handler for request type: " + request.method())
                      .build());
            }
            return requestHandler
                .handle(transportContext, request.params())
                .map(
                    result ->
                        new McpSchema.JSONRPCResponse(
                            McpSchema.JSONRPC_VERSION, request.id(), result, null))
                .onErrorResume(
                    t -> {
                      final McpSchema.JSONRPCResponse.JSONRPCError error;
                      if (t instanceof final McpError mcpError
                          && mcpError.getJsonRpcError() != null) {
                        error = mcpError.getJsonRpcError();
                      } else {
                        error =
                            new McpSchema.JSONRPCResponse.JSONRPCError(
                                McpSchema.ErrorCodes.INTERNAL_ERROR, t.getMessage(), null);
                      }
                      return Mono.just(
                          new McpSchema.JSONRPCResponse(
                              McpSchema.JSONRPC_VERSION, request.id(), null, error));
                    });
          }
        });
  }

  @Override
  public Mono<Void> closeGracefully() {
    return delegate.closeGracefully();
  }

  private static McpStatelessRequestHandler<ListToolsResult> toolsListRequestHandler(
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

  public RouterFunction<ServerResponse> getRouterFunction() {
    return delegate.getRouterFunction();
  }
}
