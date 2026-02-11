/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.server;

import io.camunda.gateway.mcp.server.DynamicToolsMcpStatelessServerFeatures.AsyncToolSpecification;
import io.camunda.gateway.mcp.tool.process.dynamic.DynamicProcessToolProvider;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.server.McpStatelessRequestHandler;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult.CompleteCompletion;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import io.modelcontextprotocol.util.Assert;
import io.modelcontextprotocol.util.Utils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DynamicToolsMcpStatelessAsyncServer {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DynamicToolsMcpStatelessAsyncServer.class);
  private static final Mono<McpSchema.CompleteResult> EMPTY_COMPLETION_RESULT =
      Mono.just(new McpSchema.CompleteResult(new CompleteCompletion(List.of(), 0, false)));
  private final McpStatelessServerTransport mcpTransportProvider;
  private final McpJsonMapper jsonMapper;
  private final McpSchema.ServerCapabilities serverCapabilities;
  private final McpSchema.Implementation serverInfo;
  private final String instructions;
  private final CopyOnWriteArrayList<AsyncToolSpecification> tools = new CopyOnWriteArrayList<>();
  private final DynamicProcessToolProvider toolsProvider;
  private List<String> protocolVersions;
  private final JsonSchemaValidator jsonSchemaValidator;

  DynamicToolsMcpStatelessAsyncServer(
      final McpStatelessServerTransport mcpTransport,
      final McpJsonMapper jsonMapper,
      final DynamicToolsMcpStatelessServerFeatures.Async features,
      final Duration requestTimeout,
      final JsonSchemaValidator jsonSchemaValidator) {
    mcpTransportProvider = mcpTransport;
    this.jsonMapper = jsonMapper;
    serverInfo = features.serverInfo();
    serverCapabilities = features.serverCapabilities();
    instructions = features.instructions();
    tools.addAll(withStructuredOutputHandling(jsonSchemaValidator, features.tools()));
    toolsProvider = features.toolsProvider();
    this.jsonSchemaValidator = jsonSchemaValidator;

    final Map<String, McpStatelessRequestHandler<?>> requestHandlers = new HashMap<>();

    // Initialize request handlers for standard MCP methods

    // Ping MUST respond with an empty data, but not NULL response.
    requestHandlers.put(McpSchema.METHOD_PING, (ctx, params) -> Mono.just(Map.of()));

    requestHandlers.put(McpSchema.METHOD_INITIALIZE, asyncInitializeRequestHandler());

    // Add tools API handlers if the tool capability is enabled
    if (serverCapabilities.tools() != null) {
      requestHandlers.put(McpSchema.METHOD_TOOLS_LIST, toolsListRequestHandler());
      requestHandlers.put(McpSchema.METHOD_TOOLS_CALL, toolsCallRequestHandler());
    }

    protocolVersions = new ArrayList<>(mcpTransport.protocolVersions());

    final McpStatelessServerHandler handler =
        new CamundaMcpStatelessServerHandler(requestHandlers, Map.of());
    mcpTransport.setMcpHandler(handler);
  }

  // ---------------------------------------
  // Lifecycle Management
  // ---------------------------------------
  private McpStatelessRequestHandler<McpSchema.InitializeResult> asyncInitializeRequestHandler() {
    return (ctx, req) ->
        Mono.defer(
            () -> {
              final McpSchema.InitializeRequest initializeRequest =
                  jsonMapper.convertValue(req, McpSchema.InitializeRequest.class);

              LOGGER.info(
                  "Client initialize request - Protocol: {}, Capabilities: {}, Info: {}",
                  initializeRequest.protocolVersion(),
                  initializeRequest.capabilities(),
                  initializeRequest.clientInfo());

              // The server MUST respond with the highest protocol version it supports
              // if
              // it does not support the requested (e.g. Client) version.
              String serverProtocolVersion = protocolVersions.getLast();

              if (protocolVersions.contains(initializeRequest.protocolVersion())) {
                // If the server supports the requested protocol version, it MUST
                // respond
                // with the same version.
                serverProtocolVersion = initializeRequest.protocolVersion();
              } else {
                LOGGER.warn(
                    "Client requested unsupported protocol version: {}, so the server will suggest the {} version instead",
                    initializeRequest.protocolVersion(),
                    serverProtocolVersion);
              }

              return Mono.just(
                  new McpSchema.InitializeResult(
                      serverProtocolVersion, serverCapabilities, serverInfo, instructions));
            });
  }

  /**
   * Get the server capabilities that define the supported features and functionality.
   *
   * @return The server capabilities
   */
  public McpSchema.ServerCapabilities getServerCapabilities() {
    return serverCapabilities;
  }

  /**
   * Get the server implementation information.
   *
   * @return The server implementation details
   */
  public McpSchema.Implementation getServerInfo() {
    return serverInfo;
  }

  /**
   * Gracefully closes the server, allowing any in-progress operations to complete.
   *
   * @return A Mono that completes when the server has been closed
   */
  public Mono<Void> closeGracefully() {
    return mcpTransportProvider.closeGracefully();
  }

  // ---------------------------------------
  // Tool Management
  // ---------------------------------------

  /** Close the server immediately. */
  public void close() {
    mcpTransportProvider.close();
  }

  private static List<DynamicToolsMcpStatelessServerFeatures.AsyncToolSpecification>
      withStructuredOutputHandling(
          final JsonSchemaValidator jsonSchemaValidator,
          final List<DynamicToolsMcpStatelessServerFeatures.AsyncToolSpecification> tools) {

    if (Utils.isEmpty(tools)) {
      return tools;
    }

    return tools.stream()
        .map(tool -> withStructuredOutputHandling(jsonSchemaValidator, tool))
        .toList();
  }

  private static DynamicToolsMcpStatelessServerFeatures.AsyncToolSpecification
      withStructuredOutputHandling(
          final JsonSchemaValidator jsonSchemaValidator,
          final DynamicToolsMcpStatelessServerFeatures.AsyncToolSpecification toolSpecification) {

    if (toolSpecification.callHandler()
        instanceof DynamicToolsMcpStatelessAsyncServer.StructuredOutputCallToolHandler) {
      // If the tool is already wrapped, return it as is
      return toolSpecification;
    }

    if (toolSpecification.tool().outputSchema() == null) {
      // If the tool does not have an output schema, return it as is
      return toolSpecification;
    }

    return new DynamicToolsMcpStatelessServerFeatures.AsyncToolSpecification(
        toolSpecification.tool(),
        new DynamicToolsMcpStatelessAsyncServer.StructuredOutputCallToolHandler(
            jsonSchemaValidator,
            toolSpecification.tool().outputSchema(),
            toolSpecification.callHandler()));
  }

  /**
   * Add a new tool specification at runtime.
   *
   * @param toolSpecification The tool specification to add
   * @return Mono that completes when clients have been notified of the change
   */
  public Mono<Void> addTool(
      final DynamicToolsMcpStatelessServerFeatures.AsyncToolSpecification toolSpecification) {
    if (toolSpecification == null) {
      return Mono.error(new IllegalArgumentException("Tool specification must not be null"));
    }
    if (toolSpecification.tool() == null) {
      return Mono.error(new IllegalArgumentException("Tool must not be null"));
    }
    if (toolSpecification.callHandler() == null) {
      return Mono.error(new IllegalArgumentException("Tool call handler must not be null"));
    }
    if (serverCapabilities.tools() == null) {
      return Mono.error(
          new IllegalStateException("Server must be configured with tool capabilities"));
    }

    final var wrappedToolSpecification =
        withStructuredOutputHandling(jsonSchemaValidator, toolSpecification);

    return Mono.defer(
        () -> {
          // Remove tools with duplicate tool names first
          if (tools.removeIf(
              th -> th.tool().name().equals(wrappedToolSpecification.tool().name()))) {
            LOGGER.warn(
                "Replace existing Tool with name '{}'", wrappedToolSpecification.tool().name());
          }

          tools.add(wrappedToolSpecification);
          LOGGER.debug("Added tool handler: {}", wrappedToolSpecification.tool().name());

          return Mono.empty();
        });
  }

  /**
   * List all registered tools.
   *
   * @return A Flux stream of all registered tools
   */
  public Flux<Tool> listTools() {
    return Flux.fromIterable(toolsProvider.getToolSpecifications())
        .concatWith(Flux.fromIterable(tools))
        .map(DynamicToolsMcpStatelessServerFeatures.AsyncToolSpecification::tool);
  }

  /**
   * Remove a tool handler at runtime.
   *
   * @param toolName The name of the tool handler to remove
   * @return Mono that completes when clients have been notified of the change
   */
  public Mono<Void> removeTool(final String toolName) {
    if (toolName == null) {
      return Mono.error(new IllegalArgumentException("Tool name must not be null"));
    }
    if (serverCapabilities.tools() == null) {
      return Mono.error(
          new IllegalStateException("Server must be configured with tool capabilities"));
    }

    return Mono.defer(
        () -> {
          if (tools.removeIf(
              toolSpecification -> toolSpecification.tool().name().equals(toolName))) {

            LOGGER.debug("Removed tool handler: {}", toolName);
          } else {
            LOGGER.warn("Ignore as a Tool with name '{}' not found", toolName);
          }

          return Mono.empty();
        });
  }

  private McpStatelessRequestHandler<McpSchema.ListToolsResult> toolsListRequestHandler() {
    return (ctx, params) -> {
      final List<Tool> tools =
          Stream.concat(this.tools.stream(), toolsProvider.getToolSpecifications().stream())
              .map(DynamicToolsMcpStatelessServerFeatures.AsyncToolSpecification::tool)
              .toList();
      return Mono.just(new McpSchema.ListToolsResult(tools, null));
    };
  }

  private McpStatelessRequestHandler<CallToolResult> toolsCallRequestHandler() {
    return (ctx, params) -> {
      final McpSchema.CallToolRequest callToolRequest =
          jsonMapper.convertValue(params, new TypeRef<>() {});

      final Optional<AsyncToolSpecification> toolSpecification =
          Stream.concat(tools.stream(), toolsProvider.getToolSpecifications().stream())
              .filter(tr -> callToolRequest.name().equals(tr.tool().name()))
              .findAny();

      if (toolSpecification.isEmpty()) {
        return Mono.error(
            McpError.builder(McpSchema.ErrorCodes.INVALID_PARAMS)
                .message("Unknown tool: invalid_tool_name")
                .data("Tool not found: " + callToolRequest.name())
                .build());
      }

      return toolSpecification.get().callHandler().apply(ctx, callToolRequest);
    };
  }

  /**
   * This method is package-private and used for test only. Should not be called by user code.
   *
   * @param protocolVersions the Client supported protocol versions.
   */
  void setProtocolVersions(final List<String> protocolVersions) {
    this.protocolVersions = protocolVersions;
  }

  private static class StructuredOutputCallToolHandler
      implements BiFunction<McpTransportContext, CallToolRequest, Mono<CallToolResult>> {

    private final BiFunction<
            McpTransportContext, McpSchema.CallToolRequest, Mono<McpSchema.CallToolResult>>
        delegateHandler;

    private final JsonSchemaValidator jsonSchemaValidator;

    private final Map<String, Object> outputSchema;

    public StructuredOutputCallToolHandler(
        final JsonSchemaValidator jsonSchemaValidator,
        final Map<String, Object> outputSchema,
        final BiFunction<
                McpTransportContext, McpSchema.CallToolRequest, Mono<McpSchema.CallToolResult>>
            delegateHandler) {

      Assert.notNull(jsonSchemaValidator, "JsonSchemaValidator must not be null");
      Assert.notNull(delegateHandler, "Delegate call tool result handler must not be null");

      this.delegateHandler = delegateHandler;
      this.outputSchema = outputSchema;
      this.jsonSchemaValidator = jsonSchemaValidator;
    }

    @Override
    public Mono<CallToolResult> apply(
        final McpTransportContext transportContext, final McpSchema.CallToolRequest request) {

      return delegateHandler
          .apply(transportContext, request)
          .map(
              result -> {
                if (Boolean.TRUE.equals(result.isError())) {
                  // If the tool call resulted in an error, skip further validation
                  return result;
                }

                if (outputSchema == null) {
                  if (result.structuredContent() != null) {
                    LOGGER.warn(
                        "Tool call with no outputSchema is not expected to have a result with structured content, but got: {}",
                        result.structuredContent());
                  }
                  // Pass through. No validation is required if no output schema is
                  // provided.
                  return result;
                }

                // If an output schema is provided, servers MUST provide structured
                // results that conform to this schema.
                // https://modelcontextprotocol.io/specification/2025-06-18/server/tools#output-schema
                if (result.structuredContent() == null) {
                  final String content =
                      "Response missing structured content which is expected when calling tool with non-empty outputSchema";
                  LOGGER.warn(content);
                  return CallToolResult.builder()
                      .content(List.of(new McpSchema.TextContent(content)))
                      .isError(true)
                      .build();
                }

                // Validate the result against the output schema
                final var validation =
                    jsonSchemaValidator.validate(outputSchema, result.structuredContent());

                if (!validation.valid()) {
                  LOGGER.warn("Tool call result validation failed: {}", validation.errorMessage());
                  return CallToolResult.builder()
                      .content(List.of(new McpSchema.TextContent(validation.errorMessage())))
                      .isError(true)
                      .build();
                }

                if (Utils.isEmpty(result.content())) {
                  // For backwards compatibility, a tool that returns structured
                  // content SHOULD also return functionally equivalent unstructured
                  // content. (For example, serialized JSON can be returned in a
                  // TextContent block.)
                  // https://modelcontextprotocol.io/specification/2025-06-18/server/tools#structured-content

                  return CallToolResult.builder()
                      .content(
                          List.of(new McpSchema.TextContent(validation.jsonStructuredOutput())))
                      .isError(result.isError())
                      .structuredContent(result.structuredContent())
                      .build();
                }

                return result;
              });
    }
  }
}
