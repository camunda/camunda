/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.util.Utils;
import jakarta.validation.Valid;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.McpPredicates;
import org.springaicommunity.mcp.annotation.McpMeta;
import org.springaicommunity.mcp.annotation.McpProgressToken;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.context.McpAsyncRequestContext;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import org.springaicommunity.mcp.method.tool.ReturnMode;
import org.springaicommunity.mcp.method.tool.utils.ClassUtils;
import org.springaicommunity.mcp.provider.tool.AbstractMcpToolProvider;
import org.springframework.aop.support.AopUtils;

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
                toolObject -> {
                  // Unwrap CGLIB/JDK proxies to get the actual target class for annotation
                  // detection
                  Class<?> targetClass =
                      AopUtils.isAopProxy(toolObject)
                          ? AopUtils.getTargetClass(toolObject)
                          : toolObject.getClass();

                  return Stream.of(targetClass.getDeclaredMethods())
                      .filter(method -> method.isAnnotationPresent(McpTool.class))
                      .filter(McpPredicates.filterReactiveReturnTypeMethod())
                      .filter(McpPredicates.filterMethodWithBidirectionalParameters())
                      .sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
                      .map(
                          mcpToolMethod -> {
                            // Validate @McpToolParams usage
                            validateMcpToolParamsUsage(mcpToolMethod);

                            var toolJavaAnnotation = this.doGetMcpToolAnnotation(mcpToolMethod);

                            String toolName =
                                Utils.hasText(toolJavaAnnotation.name())
                                    ? toolJavaAnnotation.name()
                                    : mcpToolMethod.getName();

                            String toolDescription = toolJavaAnnotation.description();

                            // Use CamundaJsonSchemaGenerator instead of default
                            String inputSchema =
                                CamundaJsonSchemaGenerator.generateForMethodInput(mcpToolMethod);

                            var toolBuilder =
                                McpSchema.Tool.builder()
                                    .name(toolName)
                                    .description(toolDescription)
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

                            boolean useStructuredOutput = tool.outputSchema() != null;

                            ReturnMode returnMode =
                                useStructuredOutput
                                    ? ReturnMode.STRUCTURED
                                    : (methodReturnType == Void.TYPE
                                            || methodReturnType == void.class
                                        ? ReturnMode.VOID
                                        : ReturnMode.TEXT);

                            BiFunction<McpTransportContext, CallToolRequest, CallToolResult>
                                methodCallback =
                                    new CamundaSyncStatelessMcpToolMethodCallback(
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
                      .toList();
                })
            .flatMap(List::stream)
            .toList();

    if (toolSpecs.isEmpty()) {
      logger.warn("No tool methods found in the provided tool objects: {}", this.toolObjects);
    }

    return toolSpecs;
  }

  /**
   * Validates that @McpToolParams is used correctly.
   *
   * <p>Enforces the constraint: a method can have EITHER individual @McpToolParam parameters OR a
   * single @McpToolParams parameter, but not both complex types.
   *
   * <p>Allowed patterns:
   *
   * <ul>
   *   <li>✅ Single @McpToolParams parameter (optionally with MCP framework types)
   *   <li>✅ Multiple individual parameters without @McpToolParams
   *   <li>❌ @McpToolParams mixed with @Valid object parameters
   * </ul>
   *
   * @param method the tool method to validate
   * @throws IllegalStateException if @McpToolParams is mixed with other complex parameters
   */
  private void validateMcpToolParamsUsage(Method method) {
    Parameter[] params = method.getParameters();

    // Find @McpToolParams parameter if any
    Parameter requestBodyParam = null;
    for (Parameter param : params) {
      if (param.isAnnotationPresent(McpToolParams.class)) {
        requestBodyParam = param;
        break;
      }
    }

    // If no @McpToolParams, no validation needed
    if (requestBodyParam == null) {
      return;
    }

    // If @McpToolParams exists, check other parameters
    for (Parameter param : params) {
      if (param == requestBodyParam) {
        continue; // Skip the @McpToolParams parameter itself
      }

      Class<?> paramType = param.getType();

      // Allow MCP framework types
      if (McpSyncRequestContext.class.isAssignableFrom(paramType)
          || McpAsyncRequestContext.class.isAssignableFrom(paramType)
          || CallToolRequest.class.isAssignableFrom(paramType)
          || McpTransportContext.class.isAssignableFrom(paramType)
          || param.isAnnotationPresent(McpProgressToken.class)
          || McpMeta.class.isAssignableFrom(paramType)) {
        continue; // MCP framework types are allowed
      }

      // Check if this is a complex object (has @Valid or is not a simple type)
      boolean isComplexObject =
          param.isAnnotationPresent(Valid.class)
              || (!ClassUtils.isSimpleValueType(paramType)
                  && !paramType.isPrimitive()
                  && !paramType.equals(String.class));

      if (isComplexObject) {
        throw new IllegalStateException(
            String.format(
                "Method '%s.%s' mixes @McpToolParams with complex parameter '%s' (type: %s). "
                    + "When using @McpToolParams, other parameters must be simple types (primitives, String) "
                    + "or MCP framework types (McpSyncRequestContext, CallToolRequest, etc.). "
                    + "Use either individual @McpToolParam parameters OR a single @McpToolParams, not both.",
                method.getDeclaringClass().getSimpleName(),
                method.getName(),
                param.getName(),
                paramType.getSimpleName()));
      }
    }
  }
}
