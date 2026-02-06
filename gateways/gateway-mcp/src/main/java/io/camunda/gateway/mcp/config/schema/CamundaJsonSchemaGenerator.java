/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.gateway.mcp.config.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.Module;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.util.Assert;
import io.modelcontextprotocol.util.Utils;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springaicommunity.mcp.annotation.McpMeta;
import org.springaicommunity.mcp.annotation.McpProgressToken;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springaicommunity.mcp.context.McpAsyncRequestContext;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import org.springaicommunity.mcp.method.tool.utils.ClassUtils;
import org.springaicommunity.mcp.method.tool.utils.ConcurrentReferenceHashMap;
import org.springaicommunity.mcp.method.tool.utils.JsonParser;
import org.springaicommunity.mcp.method.tool.utils.JsonSchemaGenerator;
import org.springaicommunity.mcp.method.tool.utils.SpringAiSchemaModule;
import org.springframework.lang.Nullable;

/**
 * This is an adapted variant of {@link JsonSchemaGenerator}, configured to inline defs and made
 * non-static.
 */
public class CamundaJsonSchemaGenerator {

  private static final boolean PROPERTY_REQUIRED_BY_DEFAULT = true;

  private final Map<Method, String> methodSchemaCache = new ConcurrentReferenceHashMap<>(256);
  private final Map<Type, String> typeSchemaCache = new ConcurrentReferenceHashMap<>(256);

  private final ObjectMapper objectMapper;
  private final SchemaGenerator typeSchemaGenerator;
  private final SchemaGenerator subtypeSchemaGenerator;

  public CamundaJsonSchemaGenerator() {
    this(JsonParser.getObjectMapper());
  }

  public CamundaJsonSchemaGenerator(final ObjectMapper objectMapper) {
    this(objectMapper, Function.identity(), Function.identity());
  }

  public CamundaJsonSchemaGenerator(
      final ObjectMapper objectMapper,
      final Function<SchemaGeneratorConfigBuilder, SchemaGeneratorConfigBuilder>
          typeSchemaCustomizer,
      final Function<SchemaGeneratorConfigBuilder, SchemaGeneratorConfigBuilder>
          subtypeSchemaCustomizer) {
    this.objectMapper = objectMapper;

    final SchemaGeneratorConfigBuilder schemaGeneratorConfigBuilder =
        typeSchemaCustomizer.apply(createSchemaGeneratorConfig());

    typeSchemaGenerator = new SchemaGenerator(schemaGeneratorConfigBuilder.build());

    final SchemaGeneratorConfig subtypeSchemaGeneratorConfig =
        subtypeSchemaCustomizer
            .apply(schemaGeneratorConfigBuilder.without(Option.SCHEMA_VERSION_INDICATOR))
            .build();
    subtypeSchemaGenerator = new SchemaGenerator(subtypeSchemaGeneratorConfig);
  }

  private static SchemaGeneratorConfigBuilder createSchemaGeneratorConfig() {
    final Module jacksonModule = new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED);
    final Module openApiModule = new Swagger2Module();
    final Module springAiSchemaModule =
        CamundaJsonSchemaGenerator.PROPERTY_REQUIRED_BY_DEFAULT
            ? new SpringAiSchemaModule()
            : new SpringAiSchemaModule(
                SpringAiSchemaModule.Option.PROPERTY_REQUIRED_FALSE_BY_DEFAULT);

    return new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
        .with(jacksonModule)
        .with(openApiModule)
        .with(springAiSchemaModule)
        .with(Option.EXTRA_OPEN_API_FORMAT_VALUES)
        .with(Option.STANDARD_FORMATS)
        .with(Option.INLINE_ALL_SCHEMAS);
  }

  public String generateForMethodInput(final Method method) {
    Assert.notNull(method, "method cannot be null");
    return methodSchemaCache.computeIfAbsent(method, this::internalGenerateFromMethodArguments);
  }

  private String internalGenerateFromMethodArguments(final Method method) {
    // Check if method has CallToolRequest parameter
    final boolean hasCallToolRequestParam =
        Arrays.stream(method.getParameterTypes()).anyMatch(CallToolRequest.class::isAssignableFrom);

    // If method has CallToolRequest, return minimal schema
    if (hasCallToolRequestParam) {
      // Check if there are other parameters besides CallToolRequest, exchange
      // types,
      // @McpProgressToken annotated parameters, and McpMeta parameters
      final boolean hasOtherParams =
          Arrays.stream(method.getParameters())
              .anyMatch(
                  param -> {
                    final Class<?> type = param.getType();
                    return !McpSyncRequestContext.class.isAssignableFrom(type)
                        && !McpAsyncRequestContext.class.isAssignableFrom(type)
                        && !CallToolRequest.class.isAssignableFrom(type)
                        && !McpSyncServerExchange.class.isAssignableFrom(type)
                        && !McpAsyncServerExchange.class.isAssignableFrom(type)
                        && !param.isAnnotationPresent(McpProgressToken.class)
                        && !McpMeta.class.isAssignableFrom(type);
                  });

      // If only CallToolRequest (and possibly exchange), return empty schema
      if (!hasOtherParams) {
        final ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        schema.putArray("required");
        return schema.toPrettyString();
      }
    }

    final ObjectNode schema = objectMapper.createObjectNode();
    schema.put("$schema", SchemaVersion.DRAFT_2020_12.getIdentifier());
    schema.put("type", "object");

    final ObjectNode properties = schema.putObject("properties");
    final List<String> required = new ArrayList<>();

    for (int i = 0; i < method.getParameterCount(); i++) {
      final Parameter parameter = method.getParameters()[i];
      final String parameterName = parameter.getName();
      final Type parameterType = method.getGenericParameterTypes()[i];

      // Skip parameters annotated with @McpProgressToken
      if (parameter.isAnnotationPresent(McpProgressToken.class)) {
        continue;
      }

      // Skip McpMeta parameters
      if (parameterType instanceof final Class<?> parameterClass
          && McpMeta.class.isAssignableFrom(parameterClass)) {
        continue;
      }

      // Skip special parameter types
      if (parameterType instanceof final Class<?> parameterClass
          && (ClassUtils.isAssignable(McpSyncRequestContext.class, parameterClass)
              || ClassUtils.isAssignable(McpAsyncRequestContext.class, parameterClass)
              || ClassUtils.isAssignable(McpSyncServerExchange.class, parameterClass)
              || ClassUtils.isAssignable(McpAsyncServerExchange.class, parameterClass)
              || ClassUtils.isAssignable(CallToolRequest.class, parameterClass))) {
        continue;
      }

      if (isMethodParameterRequired(method, i)) {
        required.add(parameterName);
      }

      final ObjectNode parameterNode = subtypeSchemaGenerator.generateSchema(parameterType);
      final String parameterDescription = getMethodParameterDescription(method, i);

      if (Utils.hasText(parameterDescription)) {
        parameterNode.put("description", parameterDescription);
      }

      properties.set(parameterName, parameterNode);
    }

    final var requiredArray = schema.putArray("required");
    required.forEach(requiredArray::add);

    return schema.toPrettyString();
  }

  public String generateFromType(final Type type) {
    Assert.notNull(type, "type cannot be null");
    return typeSchemaCache.computeIfAbsent(type, this::internalGenerateFromType);
  }

  private String internalGenerateFromType(final Type type) {
    return typeSchemaGenerator.generateSchema(type).toPrettyString();
  }

  private boolean isMethodParameterRequired(final Method method, final int index) {
    final Parameter parameter = method.getParameters()[index];

    final var toolParamAnnotation = parameter.getAnnotation(McpToolParam.class);
    if (toolParamAnnotation != null) {
      return toolParamAnnotation.required();
    }

    final var propertyAnnotation = parameter.getAnnotation(JsonProperty.class);
    if (propertyAnnotation != null) {
      return propertyAnnotation.required();
    }

    final var schemaAnnotation = parameter.getAnnotation(Schema.class);
    if (schemaAnnotation != null) {
      return schemaAnnotation.requiredMode() == RequiredMode.REQUIRED
          || schemaAnnotation.requiredMode() == RequiredMode.AUTO
          || schemaAnnotation.required();
    }

    final var nullableAnnotation = parameter.getAnnotation(Nullable.class);
    if (nullableAnnotation != null) {
      return false;
    }

    return PROPERTY_REQUIRED_BY_DEFAULT;
  }

  private String getMethodParameterDescription(final Method method, final int index) {
    final Parameter parameter = method.getParameters()[index];

    final var toolParamAnnotation = parameter.getAnnotation(McpToolParam.class);
    if (toolParamAnnotation != null && Utils.hasText(toolParamAnnotation.description())) {
      return toolParamAnnotation.description();
    }

    final var jacksonAnnotation = parameter.getAnnotation(JsonPropertyDescription.class);
    if (jacksonAnnotation != null && Utils.hasText(jacksonAnnotation.value())) {
      return jacksonAnnotation.value();
    }

    final var schemaAnnotation = parameter.getAnnotation(Schema.class);
    if (schemaAnnotation != null && Utils.hasText(schemaAnnotation.description())) {
      return schemaAnnotation.description();
    }

    return null;
  }
}
