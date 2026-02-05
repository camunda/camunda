/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.provider;

import io.camunda.gateway.mcp.schema.CamundaJsonSchemaGenerator;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.util.Utils;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.McpPredicates;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.method.tool.ReturnMode;
import org.springaicommunity.mcp.method.tool.utils.ClassUtils;
import org.springaicommunity.mcp.provider.tool.AbstractMcpToolProvider;

/**
 * Camunda-specific provider for synchronous stateless MCP tool methods.
 *
 * <p>This provider extends the standard MCP tool provider to use {@link CamundaJsonSchemaGenerator}
 * for generating JSON schemas. This allows Camunda to customize schema generation (e.g., inline
 * schemas without $defs) while maintaining compatibility with the MCP specification.
 */
public class CamundaSyncStatelessMcpToolProvider extends AbstractMcpToolProvider {

  private static final Logger logger =
      LoggerFactory.getLogger(CamundaSyncStatelessMcpToolProvider.class);

  /**
   * Create a new CamundaSyncStatelessMcpToolProvider.
   *
   * @param toolObjects the objects containing methods annotated with {@link McpTool}
   */
  public CamundaSyncStatelessMcpToolProvider(List<Object> toolObjects) {
    super(toolObjects);
  }

  /**
   * Get the stateless tool specifications using Camunda's custom schema generator.
   *
   * @return the list of stateless tool specifications
   */
  public List<SyncToolSpecification> getToolSpecifications() {

    List<SyncToolSpecification> toolSpecs =
        this.toolObjects.stream()
            .map(
                toolObject ->
                    Stream.of(this.doGetClassMethods(toolObject))
                        .filter(method -> method.isAnnotationPresent(McpTool.class))
                        .filter(McpPredicates.filterReactiveReturnTypeMethod())
                        .filter(McpPredicates.filterMethodWithBidirectionalParameters())
                        .sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
                        .map(
                            mcpToolMethod -> {
                              var toolJavaAnnotation = this.doGetMcpToolAnnotation(mcpToolMethod);

                              String toolName =
                                  Utils.hasText(toolJavaAnnotation.name())
                                      ? toolJavaAnnotation.name()
                                      : mcpToolMethod.getName();

                              String toolDescrption = toolJavaAnnotation.description();

                              // Use CamundaJsonSchemaGenerator instead of default
                              String inputSchema =
                                  CamundaJsonSchemaGenerator.generateForMethodInput(mcpToolMethod);

                              var toolBuilder =
                                  McpSchema.Tool.builder()
                                      .name(toolName)
                                      .description(toolDescrption)
                                      .inputSchema(this.getJsonMapper(), inputSchema);

                              var title = toolJavaAnnotation.title();

                              // Tool annotations
                              if (toolJavaAnnotation.annotations() != null) {
                                var toolAnnotations = toolJavaAnnotation.annotations();
                                toolBuilder.annotations(
                                    new McpSchema.ToolAnnotations(
                                        toolAnnotations.title(),
                                        toolAnnotations.readOnlyHint(),
                                        toolAnnotations.destructiveHint(),
                                        toolAnnotations.idempotentHint(),
                                        toolAnnotations.openWorldHint(),
                                        null));

                                if (!Utils.hasText(title)) {
                                  title = toolAnnotations.title();
                                }
                              }

                              if (!Utils.hasText(title)) {
                                title = toolName;
                              }
                              toolBuilder.title(title);

                              // Generate Output Schema from the method return type.
                              Class<?> methodReturnType = mcpToolMethod.getReturnType();
                              if (toolJavaAnnotation.generateOutputSchema()
                                  && methodReturnType != null
                                  && methodReturnType != CallToolResult.class
                                  && methodReturnType != Void.class
                                  && methodReturnType != void.class
                                  && !ClassUtils.isPrimitiveOrWrapper(methodReturnType)
                                  && !ClassUtils.isSimpleValueType(methodReturnType)) {

                                // Use CamundaJsonSchemaGenerator for output schema
                                toolBuilder.outputSchema(
                                    this.getJsonMapper(),
                                    CamundaJsonSchemaGenerator.generateFromType(
                                        mcpToolMethod.getGenericReturnType()));
                              }

                              var tool = toolBuilder.build();

                              boolean useStructuredOtput = tool.outputSchema() != null;

                              ReturnMode returnMode =
                                  useStructuredOtput
                                      ? ReturnMode.STRUCTURED
                                      : (methodReturnType == Void.TYPE
                                              || methodReturnType == void.class
                                          ? ReturnMode.VOID
                                          : ReturnMode.TEXT);

                              BiFunction<McpTransportContext, CallToolRequest, CallToolResult>
                                  methodCallback =
                                      new io.camunda.gateway.mcp.callback
                                          .CamundaSyncStatelessMcpToolMethodCallback(
                                          returnMode,
                                          mcpToolMethod,
                                          toolObject,
                                          this.doGetToolCallException());

                              var toolSpec =
                                  SyncToolSpecification.builder()
                                      .tool(tool)
                                      .callHandler(methodCallback)
                                      .build();

                              return toolSpec;
                            })
                        .toList())
            .flatMap(List::stream)
            .toList();

    if (toolSpecs.isEmpty()) {
      logger.warn("No tool methods found in the provided tool objects: {}", this.toolObjects);
    }

    return toolSpecs;
  }
}
