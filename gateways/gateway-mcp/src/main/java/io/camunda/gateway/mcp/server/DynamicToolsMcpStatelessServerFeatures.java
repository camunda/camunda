/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.server;

import io.camunda.gateway.mcp.tool.process.dynamic.DynamicProcessToolProvider;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.util.Assert;
import io.modelcontextprotocol.util.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * MCP stateless server features specification that a particular server can choose to support.
 *
 * @author Dariusz Jędrzejczyk
 * @author Christian Tzolov
 */
public class DynamicToolsMcpStatelessServerFeatures {

  /**
   * Specification of a tool with its asynchronous handler function. Tools are the primary way for
   * MCP servers to expose functionality to AI models. Each tool represents a specific capability.
   *
   * @param tool The tool definition including name, description, and parameter schema
   * @param callHandler The function that implements the tool's logic, receiving a {@link
   *     CallToolRequest} and returning the result.
   */
  public record AsyncToolSpecification(
      McpSchema.Tool tool,
      BiFunction<McpTransportContext, CallToolRequest, Mono<CallToolResult>> callHandler) {

    public static AsyncToolSpecification fromSync(final SyncToolSpecification syncToolSpec) {

      // FIXME: This is temporary, proper validation should be implemented
      if (syncToolSpec == null) {
        return null;
      }

      final BiFunction<McpTransportContext, CallToolRequest, Mono<McpSchema.CallToolResult>>
          callHandler =
              (ctx, req) -> {
                final var toolResult =
                    Mono.fromCallable(() -> syncToolSpec.callHandler().apply(ctx, req));
                return toolResult.subscribeOn(Schedulers.boundedElastic());
              };

      return new AsyncToolSpecification(syncToolSpec.tool(), callHandler);
    }

    /**
     * Creates a new builder instance.
     *
     * @return a new Builder instance
     */
    public static AsyncToolSpecification.Builder builder() {
      return new AsyncToolSpecification.Builder();
    }

    /** Builder for creating AsyncToolSpecification instances. */
    public static class Builder {

      private McpSchema.Tool tool;

      private BiFunction<McpTransportContext, CallToolRequest, Mono<McpSchema.CallToolResult>>
          callHandler;

      /**
       * Sets the tool definition.
       *
       * @param tool The tool definition including name, description, and parameter schema
       * @return this builder instance
       */
      public AsyncToolSpecification.Builder tool(final McpSchema.Tool tool) {
        this.tool = tool;
        return this;
      }

      /**
       * Sets the call tool handler function.
       *
       * @param callHandler The function that implements the tool's logic
       * @return this builder instance
       */
      public AsyncToolSpecification.Builder callHandler(
          final BiFunction<McpTransportContext, CallToolRequest, Mono<McpSchema.CallToolResult>>
              callHandler) {
        this.callHandler = callHandler;
        return this;
      }

      /**
       * Builds the AsyncToolSpecification instance.
       *
       * @return a new AsyncToolSpecification instance
       * @throws IllegalArgumentException if required fields are not set
       */
      public AsyncToolSpecification build() {
        Assert.notNull(tool, "Tool must not be null");
        Assert.notNull(callHandler, "Call handler function must not be null");

        return new AsyncToolSpecification(tool, callHandler);
      }
    }
  }

  /**
   * Specification of a tool with its synchronous handler function. Tools are the primary way for
   * MCP servers to expose functionality to AI models.
   *
   * @param tool The tool definition including name, description, and parameter schema
   * @param callHandler The function that implements the tool's logic, receiving a {@link
   *     CallToolRequest} and returning results.
   */
  public record SyncToolSpecification(
      McpSchema.Tool tool,
      BiFunction<McpTransportContext, CallToolRequest, McpSchema.CallToolResult> callHandler) {

    public static SyncToolSpecification.Builder builder() {
      return new SyncToolSpecification.Builder();
    }

    /** Builder for creating SyncToolSpecification instances. */
    public static class Builder {

      private McpSchema.Tool tool;

      private BiFunction<McpTransportContext, CallToolRequest, McpSchema.CallToolResult>
          callHandler;

      /**
       * Sets the tool definition.
       *
       * @param tool The tool definition including name, description, and parameter schema
       * @return this builder instance
       */
      public SyncToolSpecification.Builder tool(final McpSchema.Tool tool) {
        this.tool = tool;
        return this;
      }

      /**
       * Sets the call tool handler function.
       *
       * @param callHandler The function that implements the tool's logic
       * @return this builder instance
       */
      public SyncToolSpecification.Builder callHandler(
          final BiFunction<McpTransportContext, CallToolRequest, McpSchema.CallToolResult>
              callHandler) {
        this.callHandler = callHandler;
        return this;
      }

      /**
       * Builds the SyncToolSpecification instance.
       *
       * @return a new SyncToolSpecification instance
       * @throws IllegalArgumentException if required fields are not set
       */
      public SyncToolSpecification build() {
        Assert.notNull(tool, "Tool must not be null");
        Assert.notNull(callHandler, "CallTool function must not be null");

        return new SyncToolSpecification(tool, callHandler);
      }
    }
  }

  /**
   * Asynchronous server features specification.
   *
   * @param serverInfo The server implementation details
   * @param serverCapabilities The server capabilities
   * @param tools The list of tool specifications
   * @param toolsProvider The dynamic process tool provider for generating tools
   * @param instructions The server instructions text
   */
  record Async(
      McpSchema.Implementation serverInfo,
      McpSchema.ServerCapabilities serverCapabilities,
      List<AsyncToolSpecification> tools,
      DynamicProcessToolProvider toolsProvider,
      String instructions) {

    /**
     * Create an instance and validate the arguments.
     *
     * @param serverInfo The server implementation details
     * @param serverCapabilities The server capabilities
     * @param tools The list of tool specifications
     * @param toolsProvider The dynamic process tool provider for generating tools
     * @param instructions The server instructions text
     */
    Async(
        final McpSchema.Implementation serverInfo,
        final McpSchema.ServerCapabilities serverCapabilities,
        final List<AsyncToolSpecification> tools,
        final DynamicProcessToolProvider toolsProvider,
        final String instructions) {

      Assert.notNull(serverInfo, "Server info must not be null");

      this.serverInfo = serverInfo;
      this.serverCapabilities =
          (serverCapabilities != null)
              ? serverCapabilities
              : new McpSchema.ServerCapabilities(
                  null, // completions
                  null, // experimental
                  null, // currently statless server doesn't support set logging
                  null,
                  null,
                  !Utils.isEmpty(tools)
                      ? new McpSchema.ServerCapabilities.ToolCapabilities(false)
                      : null);

      this.tools = (tools != null) ? tools : List.of();
      this.toolsProvider = toolsProvider;
      this.instructions = instructions;
    }

    /**
     * Convert a synchronous specification into an asynchronous one and provide blocking code
     * offloading to prevent accidental blocking of the non-blocking transport.
     *
     * @param syncSpec a potentially blocking, synchronous specification.
     * @param immediateExecution when true, do not offload. Do NOT set to true when using a
     *     non-blocking transport.
     * @return a specification which is protected from blocking calls specified by the user.
     */
    static Async fromSync(final Sync syncSpec, final boolean immediateExecution) {
      final List<AsyncToolSpecification> tools = new ArrayList<>();
      for (final var tool : syncSpec.tools()) {
        tools.add(AsyncToolSpecification.fromSync(tool));
      }

      return new Async(
          syncSpec.serverInfo(),
          syncSpec.serverCapabilities(),
          tools,
          syncSpec.toolsProvider(),
          syncSpec.instructions());
    }
  }

  /**
   * Synchronous server features specification.
   *
   * @param serverInfo The server implementation details
   * @param serverCapabilities The server capabilities
   * @param tools The list of tool specifications
   * @param instructions The server instructions text
   */
  record Sync(
      McpSchema.Implementation serverInfo,
      McpSchema.ServerCapabilities serverCapabilities,
      List<SyncToolSpecification> tools,
      DynamicProcessToolProvider toolsProvider,
      String instructions) {

    /**
     * Create an instance and validate the arguments.
     *
     * @param serverInfo The server implementation details
     * @param serverCapabilities The server capabilities
     * @param tools The list of tool specifications
     * @param instructions The server instructions text
     */
    Sync(
        final McpSchema.Implementation serverInfo,
        final McpSchema.ServerCapabilities serverCapabilities,
        final List<SyncToolSpecification> tools,
        final DynamicProcessToolProvider toolsProvider,
        final String instructions) {

      Assert.notNull(serverInfo, "Server info must not be null");

      this.serverInfo = serverInfo;
      this.serverCapabilities =
          (serverCapabilities != null)
              ? serverCapabilities
              : new McpSchema.ServerCapabilities(
                  null, // completions
                  null, // experimental
                  new McpSchema.ServerCapabilities.LoggingCapabilities(), // Enable
                  // logging
                  // by
                  // default
                  null,
                  null,
                  !Utils.isEmpty(tools)
                      ? new McpSchema.ServerCapabilities.ToolCapabilities(false)
                      : null);

      this.tools = (tools != null) ? tools : new ArrayList<>();
      this.toolsProvider = toolsProvider;
      this.instructions = instructions;
    }
  }
}
