/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config.tool;

import io.camunda.gateway.mcp.config.schema.CamundaJsonSchemaGenerator;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import io.modelcontextprotocol.util.Utils;
import jakarta.validation.Valid;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Comparator;
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
import org.springaicommunity.mcp.provider.tool.SyncMcpToolProvider;
import org.springframework.aop.support.AopUtils;

/**
 * Camunda-specific provider for synchronous stateless MCP tool methods.
 *
 * <p>This provider scans for {@link CamundaMcpTool} annotations (not Spring AI's {@link McpTool})
 * and uses {@link CamundaJsonSchemaGenerator} for generating JSON schemas. This allows Camunda to
 * control tool registration independently without relying on Spring AI's autoconfig exclusions.
 *
 * <p>Original Spring AI implementation: {@link SyncMcpToolProvider}
 */
public class CamundaSyncStatelessMcpToolProvider extends AbstractMcpToolProvider {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaSyncStatelessMcpToolProvider.class);

  private final CamundaJsonSchemaGenerator jsonSchemaGenerator;

  /**
   * Create a new CamundaSyncStatelessMcpToolProvider.
   *
   * @param toolObjects the objects containing methods annotated with {@link CamundaMcpTool}
   */
  public CamundaSyncStatelessMcpToolProvider(
      final List<Object> toolObjects, final CamundaJsonSchemaGenerator jsonSchemaGenerator) {
    super(toolObjects);
    this.jsonSchemaGenerator = jsonSchemaGenerator;
  }

  /**
   * Get the stateless tool specifications using our custom schema generator.
   *
   * @return the list of stateless tool specifications
   */
  public List<SyncToolSpecification> getToolSpecifications() {
    final List<SyncToolSpecification> toolSpecs =
        toolObjects.stream()
            .map(
                toolObject ->
                    Stream.of(doGetClassMethods(toolObject))
                        .filter(method -> method.isAnnotationPresent(CamundaMcpTool.class))
                        .filter(McpPredicates.filterReactiveReturnTypeMethod())
                        .filter(McpPredicates.filterMethodWithBidirectionalParameters())
                        .sorted(Comparator.comparing(Method::getName))
                        .map(
                            mcpToolMethod -> createSyncToolSpecification(toolObject, mcpToolMethod))
                        .toList())
            .flatMap(List::stream)
            .toList();

    if (toolSpecs.isEmpty()) {
      LOGGER.warn("No tool methods found in the provided tool objects: {}", toolObjects);
    }

    return toolSpecs;
  }

  @Override
  protected Method[] doGetClassMethods(final Object bean) {
    // Unwrap CGLIB/JDK proxies to get the actual target class for annotation
    // detection
    final Class<?> targetClass =
        AopUtils.isAopProxy(bean) ? AopUtils.getTargetClass(bean) : bean.getClass();

    return targetClass.getDeclaredMethods();
  }

  private SyncToolSpecification createSyncToolSpecification(
      final Object toolObject, final Method mcpToolMethod) {
    validateMcpToolParamsUsage(mcpToolMethod);

    final var toolAnnotation = mcpToolMethod.getAnnotation(CamundaMcpTool.class);

    final String toolName =
        Utils.hasText(toolAnnotation.name()) ? toolAnnotation.name() : mcpToolMethod.getName();
    final String toolDescription = toolAnnotation.description();
    String toolTitle = toolAnnotation.title();

    final String inputSchema = jsonSchemaGenerator.generateForMethodInput(mcpToolMethod);

    final var toolBuilder =
        McpSchema.Tool.builder()
            .name(toolName)
            .description(toolDescription)
            .inputSchema(getJsonMapper(), inputSchema);

    if (toolAnnotation.annotations() != null) {
      final var toolAnnotations = toolAnnotation.annotations();
      toolBuilder.annotations(
          new ToolAnnotations(
              toolAnnotations.title(),
              toolAnnotations.readOnlyHint(),
              toolAnnotations.destructiveHint(),
              toolAnnotations.idempotentHint(),
              toolAnnotations.openWorldHint(),
              null));

      if (!Utils.hasText(toolTitle)) {
        toolTitle = toolAnnotations.title();
      }
    }

    if (!Utils.hasText(toolTitle)) {
      toolTitle = toolName;
    }

    toolBuilder.title(toolTitle);

    // Generate Output Schema from the method return type.
    final Class<?> methodReturnType = mcpToolMethod.getReturnType();
    if (toolAnnotation.generateOutputSchema()
        && methodReturnType != CallToolResult.class
        && methodReturnType != Void.class
        && methodReturnType != void.class
        && !ClassUtils.isPrimitiveOrWrapper(methodReturnType)
        && !ClassUtils.isSimpleValueType(methodReturnType)) {

      toolBuilder.outputSchema(
          getJsonMapper(),
          jsonSchemaGenerator.generateFromType(mcpToolMethod.getGenericReturnType()));
    }

    final var tool = toolBuilder.build();

    final boolean useStructuredOutput = tool.outputSchema() != null;
    final ReturnMode returnMode =
        useStructuredOutput
            ? ReturnMode.STRUCTURED
            : (methodReturnType == Void.TYPE || methodReturnType == void.class
                ? ReturnMode.VOID
                : ReturnMode.TEXT);

    final BiFunction<McpTransportContext, CallToolRequest, CallToolResult> methodCallback =
        new CamundaSyncStatelessMcpToolMethodCallback(
            returnMode, mcpToolMethod, toolObject, doGetToolCallException());

    return SyncToolSpecification.builder().tool(tool).callHandler(methodCallback).build();
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
  private void validateMcpToolParamsUsage(final Method method) {
    final Parameter[] params = method.getParameters();

    final Parameter mcpToolParamsParam =
        Arrays.stream(params)
            .filter(param -> param.isAnnotationPresent(McpToolParams.class))
            .findFirst()
            .orElse(null);

    // no @McpToolParams parameter, nothing to validate
    if (mcpToolParamsParam == null) {
      return;
    }

    for (final Parameter param : params) {
      if (param == mcpToolParamsParam) {
        continue;
      }

      final Class<?> paramType = param.getType();

      // MCP framework types are allowed
      if (McpSyncRequestContext.class.isAssignableFrom(paramType)
          || McpAsyncRequestContext.class.isAssignableFrom(paramType)
          || CallToolRequest.class.isAssignableFrom(paramType)
          || McpTransportContext.class.isAssignableFrom(paramType)
          || param.isAnnotationPresent(McpProgressToken.class)
          || McpMeta.class.isAssignableFrom(paramType)) {
        continue;
      }

      final boolean isComplexObject =
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
