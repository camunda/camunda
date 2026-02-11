/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.server;

import io.camunda.gateway.mcp.server.DynamicToolsMcpStatelessServerFeatures.SyncToolSpecification;
import io.camunda.gateway.mcp.tool.process.dynamic.DynamicProcessToolProvider;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import io.modelcontextprotocol.util.Assert;
import io.modelcontextprotocol.util.DefaultMcpUriTemplateManagerFactory;
import io.modelcontextprotocol.util.McpUriTemplateManagerFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Factory for creating MCP servers with dynamic tool generation support.
 *
 * <p>This creates an {@link McpStatelessSyncServer} instance that combines static tools (defined at
 * initialization) with dynamic tools (generated per-request by a provider function). The dynamic
 * tools are regenerated on each request, allowing tools to be filtered based on the current user's
 * authentication context and permissions.
 *
 * <p>Note: The current MCP SDK does not natively support dynamic tool generation. This
 * implementation creates a standard static server and relies on the tool provider being called at
 * server creation time. For truly per-request dynamic tools, the SDK would need to provide hooks
 * for dynamic tool providers.
 */
public interface DynamicToolsMcpServer extends McpServer {

  static DynamicToolsStatelessSyncSpecification sync(final McpStatelessServerTransport transport) {
    return new DynamicToolsStatelessSyncSpecification(transport);
  }

  class DynamicToolsStatelessSyncSpecification {

    boolean immediateExecution = false;
    McpUriTemplateManagerFactory uriTemplateManagerFactory =
        new DefaultMcpUriTemplateManagerFactory();
    McpJsonMapper jsonMapper;
    McpSchema.Implementation serverInfo = DEFAULT_SERVER_INFO;
    McpSchema.ServerCapabilities serverCapabilities;
    JsonSchemaValidator jsonSchemaValidator;
    String instructions;
    DynamicProcessToolProvider toolsProvider;

    /**
     * The Model Context Protocol (MCP) allows servers to expose tools that can be invoked by
     * language models. Tools enable models to interact with external systems, such as querying
     * databases, calling APIs, or performing computations. Each tool is uniquely identified by a
     * name and includes metadata describing its schema.
     */
    final List<SyncToolSpecification> tools = new ArrayList<>();

    Duration requestTimeout = Duration.ofSeconds(10); // Default timeout
    private final McpStatelessServerTransport transport;

    public DynamicToolsStatelessSyncSpecification(final McpStatelessServerTransport transport) {
      this.transport = transport;
    }

    /**
     * Sets the URI template manager factory to use for creating URI templates. This allows for
     * custom URI template parsing and variable extraction.
     *
     * @param uriTemplateManagerFactory The factory to use. Must not be null.
     * @return This builder instance for method chaining
     * @throws IllegalArgumentException if uriTemplateManagerFactory is null
     */
    public DynamicToolsStatelessSyncSpecification uriTemplateManagerFactory(
        final McpUriTemplateManagerFactory uriTemplateManagerFactory) {
      Assert.notNull(uriTemplateManagerFactory, "URI template manager factory must not be null");
      this.uriTemplateManagerFactory = uriTemplateManagerFactory;
      return this;
    }

    /**
     * Sets the duration to wait for server responses before timing out requests. This timeout
     * applies to all requests made through the client, including tool calls, resource access, and
     * prompt operations.
     *
     * @param requestTimeout The duration to wait before timing out requests. Must not be null.
     * @return This builder instance for method chaining
     * @throws IllegalArgumentException if requestTimeout is null
     */
    public DynamicToolsStatelessSyncSpecification requestTimeout(final Duration requestTimeout) {
      Assert.notNull(requestTimeout, "Request timeout must not be null");
      this.requestTimeout = requestTimeout;
      return this;
    }

    /**
     * Sets the server implementation information that will be shared with clients during connection
     * initialization. This helps with version compatibility, debugging, and server identification.
     *
     * @param serverInfo The server implementation details including name and version. Must not be
     *     null.
     * @return This builder instance for method chaining
     * @throws IllegalArgumentException if serverInfo is null
     */
    public DynamicToolsStatelessSyncSpecification serverInfo(
        final McpSchema.Implementation serverInfo) {
      Assert.notNull(serverInfo, "Server info must not be null");
      this.serverInfo = serverInfo;
      return this;
    }

    /**
     * Sets the server implementation information using name and version strings. This is a
     * convenience method alternative to {@link #serverInfo(McpSchema.Implementation)}.
     *
     * @param name The server name. Must not be null or empty.
     * @param version The server version. Must not be null or empty.
     * @return This builder instance for method chaining
     * @throws IllegalArgumentException if name or version is null or empty
     * @see #serverInfo(McpSchema.Implementation)
     */
    public DynamicToolsStatelessSyncSpecification serverInfo(
        final String name, final String version) {
      Assert.hasText(name, "Name must not be null or empty");
      Assert.hasText(version, "Version must not be null or empty");
      serverInfo = new McpSchema.Implementation(name, version);
      return this;
    }

    /**
     * Sets the server instructions that will be shared with clients during connection
     * initialization. These instructions provide guidance to the client on how to interact with
     * this server.
     *
     * @param instructions The instructions text. Can be null or empty.
     * @return This builder instance for method chaining
     */
    public DynamicToolsStatelessSyncSpecification instructions(final String instructions) {
      this.instructions = instructions;
      return this;
    }

    /**
     * Sets the server capabilities that will be advertised to clients during connection
     * initialization. Capabilities define what features the server supports, such as:
     *
     * <ul>
     *   <li>Tool execution
     *   <li>Resource access
     *   <li>Prompt handling
     * </ul>
     *
     * @param serverCapabilities The server capabilities configuration. Must not be null.
     * @return This builder instance for method chaining
     * @throws IllegalArgumentException if serverCapabilities is null
     */
    public DynamicToolsStatelessSyncSpecification capabilities(
        final McpSchema.ServerCapabilities serverCapabilities) {
      Assert.notNull(serverCapabilities, "Server capabilities must not be null");
      this.serverCapabilities = serverCapabilities;
      return this;
    }

    /**
     * Adds a single tool with its implementation handler to the server. This is a convenience
     * method for registering individual tools without creating a {@link
     * McpServerFeatures.SyncToolSpecification} explicitly.
     *
     * @param tool The tool definition including name, description, and schema. Must not be null.
     * @param callHandler The function that implements the tool's logic. Must not be null. The
     *     function's first argument is an {@link McpSyncServerExchange} upon which the server can
     *     interact with the connected client. The second argument is the {@link
     *     McpSchema.CallToolRequest} object containing the tool call
     * @return This builder instance for method chaining
     * @throws IllegalArgumentException if tool or handler is null
     */
    public DynamicToolsStatelessSyncSpecification toolCall(
        final McpSchema.Tool tool,
        final BiFunction<McpTransportContext, CallToolRequest, CallToolResult> callHandler) {

      Assert.notNull(tool, "Tool must not be null");
      Assert.notNull(callHandler, "Handler must not be null");
      assertNoDuplicateTool(tool.name());

      tools.add(
          new DynamicToolsMcpStatelessServerFeatures.SyncToolSpecification(tool, callHandler));

      return this;
    }

    /**
     * Adds multiple tools with their handlers to the server using a List. This method is useful
     * when tools are dynamically generated or loaded from a configuration source.
     *
     * @param toolSpecifications The list of tool specifications to add. Must not be null.
     * @return This builder instance for method chaining
     * @throws IllegalArgumentException if toolSpecifications is null
     * @see #tools(DynamicToolsMcpStatelessServerFeatures.SyncToolSpecification...)
     */
    public DynamicToolsStatelessSyncSpecification tools(
        final List<DynamicToolsMcpStatelessServerFeatures.SyncToolSpecification>
            toolSpecifications) {
      Assert.notNull(toolSpecifications, "Tool handlers list must not be null");

      for (final var tool : toolSpecifications) {
        assertNoDuplicateTool(tool.tool().name());
        tools.add(tool);
      }

      return this;
    }

    /**
     * Adds multiple tools with their handlers to the server using varargs. This method provides a
     * convenient way to register multiple tools inline.
     *
     * <p>Example usage:
     *
     * <pre>{@code
     * .tools(
     *     McpServerFeatures.SyncToolSpecification.builder().tool(calculatorTool).callTool(calculatorHandler).build(),
     *     McpServerFeatures.SyncToolSpecification.builder().tool(weatherTool).callTool(weatherHandler).build(),
     *     McpServerFeatures.SyncToolSpecification.builder().tool(fileManagerTool).callTool(fileManagerHandler).build()
     * )
     * }</pre>
     *
     * @param toolSpecifications The tool specifications to add. Must not be null.
     * @return This builder instance for method chaining
     * @throws IllegalArgumentException if toolSpecifications is null
     */
    public DynamicToolsStatelessSyncSpecification tools(
        final DynamicToolsMcpStatelessServerFeatures.SyncToolSpecification... toolSpecifications) {
      Assert.notNull(toolSpecifications, "Tool handlers list must not be null");

      for (final var tool : toolSpecifications) {
        assertNoDuplicateTool(tool.tool().name());
        tools.add(tool);
      }
      return this;
    }

    private void assertNoDuplicateTool(final String toolName) {
      if (tools.stream().anyMatch(toolSpec -> toolSpec.tool().name().equals(toolName))) {
        throw new IllegalArgumentException(
            "Tool with name '" + toolName + "' is already registered.");
      }
    }

    public DynamicToolsStatelessSyncSpecification toolsProvider(
        final DynamicProcessToolProvider toolsProvider) {
      Assert.notNull(toolsProvider, "Tools provider must not be null");
      this.toolsProvider = toolsProvider;
      return this;
    }

    /**
     * Sets the JsonMapper to use for serializing and deserializing JSON messages.
     *
     * @param jsonMapper the mapper to use. Must not be null.
     * @return This builder instance for method chaining.
     * @throws IllegalArgumentException if jsonMapper is null
     */
    public DynamicToolsStatelessSyncSpecification jsonMapper(final McpJsonMapper jsonMapper) {
      Assert.notNull(jsonMapper, "JsonMapper must not be null");
      this.jsonMapper = jsonMapper;
      return this;
    }

    /**
     * Sets the JSON schema validator to use for validating tool and resource schemas. This ensures
     * that the server's tools and resources conform to the expected schema definitions.
     *
     * @param jsonSchemaValidator The validator to use. Must not be null.
     * @return This builder instance for method chaining
     * @throws IllegalArgumentException if jsonSchemaValidator is null
     */
    public DynamicToolsStatelessSyncSpecification jsonSchemaValidator(
        final JsonSchemaValidator jsonSchemaValidator) {
      Assert.notNull(jsonSchemaValidator, "JsonSchemaValidator must not be null");
      this.jsonSchemaValidator = jsonSchemaValidator;
      return this;
    }

    public DynamicToolMcpStatelessSyncServer build() {
      final var syncFeatures =
          new DynamicToolsMcpStatelessServerFeatures.Sync(
              serverInfo, serverCapabilities, tools, toolsProvider, instructions);
      final var asyncFeatures =
          DynamicToolsMcpStatelessServerFeatures.Async.fromSync(syncFeatures, immediateExecution);
      final var asyncServer =
          new DynamicToolsMcpStatelessAsyncServer(
              transport,
              jsonMapper == null ? McpJsonMapper.getDefault() : jsonMapper,
              asyncFeatures,
              requestTimeout,
              jsonSchemaValidator != null ? jsonSchemaValidator : JsonSchemaValidator.getDefault());
      return new DynamicToolMcpStatelessSyncServer(asyncServer);
    }
  }
}
