/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config.tool;

import static io.camunda.gateway.mcp.config.tool.McpToolUtils.isFrameworkParameter;

import io.camunda.gateway.mcp.config.schema.CamundaJsonSchemaGenerator;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import io.modelcontextprotocol.util.Utils;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.McpPredicates;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.method.tool.ReturnMode;
import org.springaicommunity.mcp.method.tool.utils.ClassUtils;
import org.springaicommunity.mcp.provider.tool.AbstractMcpToolProvider;
import org.springaicommunity.mcp.provider.tool.SyncMcpToolProvider;
import org.springframework.aop.support.AopUtils;

/**
 * Camunda-specific provider for synchronous stateless MCP tool methods.
 *
 * <p>This provider scans for {@link CamundaMcpTool} annotations (not Spring AI's {@link McpTool})
 * and uses {@link CamundaJsonSchemaGenerator} for generating JSON schemas. This allows us to
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
    validateMcpToolParamsUnwrappedUsage(mcpToolMethod);

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

      // apply tool annotations only if they deviate from the default value to reduce schema payload
      // see https://modelcontextprotocol.io/specification/2025-11-25/schema#toolannotations
      toolBuilder.annotations(
          new ToolAnnotations(
              Optional.ofNullable(toolAnnotations.title()).filter(t -> !t.isBlank()).orElse(null),
              applyIfNotDefaultValue(toolAnnotations.readOnlyHint(), false),
              applyIfNotDefaultValue(toolAnnotations.destructiveHint(), true),
              applyIfNotDefaultValue(toolAnnotations.idempotentHint(), false),
              applyIfNotDefaultValue(toolAnnotations.openWorldHint(), true),
              null));

      if (!Utils.hasText(toolTitle)) {
        toolTitle = toolAnnotations.title();
      }
    }

    if (!Utils.hasText(toolTitle)) {
      toolTitle = toolName;
    }

    toolBuilder.title(toolTitle);

    // generate output schema from the method return type
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
            : methodReturnType == Void.TYPE ? ReturnMode.VOID : ReturnMode.TEXT;

    final BiFunction<McpTransportContext, CallToolRequest, CallToolResult> methodCallback =
        new CamundaSyncStatelessMcpToolMethodCallback(
            returnMode, mcpToolMethod, toolObject, doGetToolCallException());

    return SyncToolSpecification.builder().tool(tool).callHandler(methodCallback).build();
  }

  /**
   * Validates that @{@link McpToolParamsUnwrapped} is used correctly.
   *
   * <p>Enforces mutual exclusivity: a method can have EITHER individual tool parameters OR a single
   * {@code @McpToolParamsUnwrapped} wrapper parameter, but not both. The only parameters allowed
   * alongside {@code @McpToolParamsUnwrapped} are MCP framework types (context, exchange, progress
   * token, meta, request).
   *
   * @param method the tool method to validate
   * @throws IllegalStateException if @McpToolParams is used incorrectly
   */
  private void validateMcpToolParamsUnwrappedUsage(final Method method) {
    final Parameter[] parameters = method.getParameters();

    final long annotatedParametersCount =
        Arrays.stream(parameters)
            .filter(parameter -> parameter.isAnnotationPresent(McpToolParamsUnwrapped.class))
            .count();

    if (annotatedParametersCount == 0) {
      return;
    }

    if (annotatedParametersCount > 1) {
      throw new IllegalStateException(
          String.format(
              "Method '%s.%s' has multiple @McpToolParamsUnwrapped parameters. "
                  + "Only a single @McpToolParamsUnwrapped parameter is allowed per method.",
              method.getDeclaringClass().getSimpleName(), method.getName()));
    }

    Arrays.stream(parameters)
        .filter(
            parameter ->
                !parameter.isAnnotationPresent(McpToolParamsUnwrapped.class)
                    && !isFrameworkParameter(parameter))
        .findFirst()
        .ifPresent(
            parameter -> {
              throw new IllegalStateException(
                  String.format(
                      "Method '%s.%s' mixes @McpToolParamsUnwrapped with individual parameter '%s' (type: %s). "
                          + "Use either individual parameters OR a single @McpToolParamsUnwrapped wrapper, not both.",
                      method.getDeclaringClass().getSimpleName(),
                      method.getName(),
                      parameter.getName(),
                      parameter.getType().getSimpleName()));
            });
  }

  private Boolean applyIfNotDefaultValue(final boolean value, final boolean defaultValue) {
    return value == defaultValue ? null : value;
  }
}
