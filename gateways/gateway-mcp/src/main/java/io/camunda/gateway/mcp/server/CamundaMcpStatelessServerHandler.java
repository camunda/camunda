/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.server;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessNotificationHandler;
import io.modelcontextprotocol.server.McpStatelessRequestHandler;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class CamundaMcpStatelessServerHandler implements McpStatelessServerHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaMcpStatelessServerHandler.class);

  Map<String, McpStatelessRequestHandler<?>> requestHandlers;

  Map<String, McpStatelessNotificationHandler> notificationHandlers;

  public CamundaMcpStatelessServerHandler(
      final Map<String, McpStatelessRequestHandler<?>> requestHandlers,
      final Map<String, McpStatelessNotificationHandler> notificationHandlers) {
    this.requestHandlers = requestHandlers;
    this.notificationHandlers = notificationHandlers;
  }

  @Override
  public Mono<JSONRPCResponse> handleRequest(
      final McpTransportContext transportContext, final McpSchema.JSONRPCRequest request) {
    final McpStatelessRequestHandler<?> requestHandler = requestHandlers.get(request.method());
    if (requestHandler == null) {
      return Mono.error(new McpError("Missing handler for request type: " + request.method()));
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
              if (t instanceof final McpError mcpError && mcpError.getJsonRpcError() != null) {
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

  @Override
  public Mono<Void> handleNotification(
      final McpTransportContext transportContext,
      final McpSchema.JSONRPCNotification notification) {
    final McpStatelessNotificationHandler notificationHandler =
        notificationHandlers.get(notification.method());
    if (notificationHandler == null) {
      LOGGER.warn("Missing handler for notification type: {}", notification.method());
      return Mono.empty();
    }
    return notificationHandler.handle(transportContext, notification.params());
  }
}
