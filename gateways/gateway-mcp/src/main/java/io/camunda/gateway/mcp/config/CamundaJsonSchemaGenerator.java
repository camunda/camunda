/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/*
 * This file is a 1:1 copy of org.springaicommunity.mcp.method.tool.utils.JsonSchemaGenerator
 *
 * Original source:
 * https://github.com/spring-projects-experimental/spring-ai-mcp
 * org.springaicommunity.mcp.method.tool.utils.JsonSchemaGenerator
 *
 * License: Apache License, Version 2.0
 * Copyright 2025-2025 the original author or authors.
 *
 * This copy will be customized in Phase 2 to generate inline schemas without $defs.
 */

package io.camunda.gateway.mcp.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
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
import org.springaicommunity.mcp.annotation.McpMeta;
import org.springaicommunity.mcp.annotation.McpProgressToken;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springaicommunity.mcp.context.McpAsyncRequestContext;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import org.springaicommunity.mcp.method.tool.utils.ClassUtils;
import org.springaicommunity.mcp.method.tool.utils.ConcurrentReferenceHashMap;
import org.springaicommunity.mcp.method.tool.utils.JsonParser;
import org.springaicommunity.mcp.method.tool.utils.SpringAiSchemaModule;
import reactor.util.annotation.Nullable;

/**
 * Camunda's copy of JsonSchemaGenerator for MCP tool schema generation.
 *
 * <p>This is a 1:1 copy of the mcp-annotations JsonSchemaGenerator. It generates JSON schemas with
 * $defs (default behavior). In Phase 2, this will be modified to generate inline schemas without
 * $defs.
 *
 * <p><b>Phase 1:</b> Exact copy - generates schemas with $defs (current)
 *
 * <p><b>Phase 2:</b> Will be modified to inline all definitions
 */
public class CamundaJsonSchemaGenerator {

  private static final boolean PROPERTY_REQUIRED_BY_DEFAULT = true;

  private static final SchemaGenerator TYPE_SCHEMA_GENERATOR;

  private static final SchemaGenerator SUBTYPE_SCHEMA_GENERATOR;

  private static final Map<Method, String> methodSchemaCache =
      new ConcurrentReferenceHashMap<>(256);

  private static final Map<Class<?>, String> classSchemaCache =
      new ConcurrentReferenceHashMap<>(256);

  private static final Map<Type, String> typeSchemaCache = new ConcurrentReferenceHashMap<>(256);

  /*
   * Initialize JSON Schema generators.
   */
  static {
    Module jacksonModule = new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED);
    Module openApiModule = new Swagger2Module();
    Module springAiSchemaModule =
        PROPERTY_REQUIRED_BY_DEFAULT
            ? new SpringAiSchemaModule()
            : new SpringAiSchemaModule(
                SpringAiSchemaModule.Option.PROPERTY_REQUIRED_FALSE_BY_DEFAULT);

    SchemaGeneratorConfigBuilder schemaGeneratorConfigBuilder =
        new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
            .with(jacksonModule)
            .with(openApiModule)
            .with(springAiSchemaModule)
            .with(Option.EXTRA_OPEN_API_FORMAT_VALUES)
            .with(Option.STANDARD_FORMATS)
            .with(Option.INLINE_ALL_SCHEMAS);

    SchemaGeneratorConfig typeSchemaGeneratorConfig = schemaGeneratorConfigBuilder.build();
    TYPE_SCHEMA_GENERATOR = new SchemaGenerator(typeSchemaGeneratorConfig);

    SchemaGeneratorConfig subtypeSchemaGeneratorConfig =
        schemaGeneratorConfigBuilder.without(Option.SCHEMA_VERSION_INDICATOR).build();
    SUBTYPE_SCHEMA_GENERATOR = new SchemaGenerator(subtypeSchemaGeneratorConfig);
  }

  public static String generateForMethodInput(Method method) {
    Assert.notNull(method, "method cannot be null");
    return methodSchemaCache.computeIfAbsent(
        method, CamundaJsonSchemaGenerator::internalGenerateFromMethodArguments);
  }

  private static String internalGenerateFromMethodArguments(Method method) {
    // Check if method has CallToolRequest parameter
    boolean hasCallToolRequestParam =
        Arrays.stream(method.getParameterTypes())
            .anyMatch(type -> CallToolRequest.class.isAssignableFrom(type));

    // If method has CallToolRequest, return minimal schema
    if (hasCallToolRequestParam) {
      // Check if there are other parameters besides CallToolRequest, exchange
      // types,
      // @McpProgressToken annotated parameters, and McpMeta parameters
      boolean hasOtherParams =
          Arrays.stream(method.getParameters())
              .anyMatch(
                  param -> {
                    Class<?> type = param.getType();
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
        ObjectNode schema = JsonParser.getObjectMapper().createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        schema.putArray("required");
        return schema.toPrettyString();
      }
    }

    ObjectNode schema = JsonParser.getObjectMapper().createObjectNode();
    schema.put("$schema", SchemaVersion.DRAFT_2020_12.getIdentifier());
    schema.put("type", "object");

    ObjectNode properties = schema.putObject("properties");
    List<String> required = new ArrayList<>();

    for (int i = 0; i < method.getParameterCount(); i++) {
      Parameter parameter = method.getParameters()[i];
      String parameterName = parameter.getName();
      Type parameterType = method.getGenericParameterTypes()[i];

      // Skip parameters annotated with @McpProgressToken
      if (parameter.isAnnotationPresent(McpProgressToken.class)) {
        continue;
      }

      // Skip McpMeta parameters
      if (parameterType instanceof Class<?> parameterClass
          && McpMeta.class.isAssignableFrom(parameterClass)) {
        continue;
      }

      // Skip special parameter types
      if (parameterType instanceof Class<?> parameterClass
          && (ClassUtils.isAssignable(McpSyncRequestContext.class, parameterClass)
              || ClassUtils.isAssignable(McpAsyncRequestContext.class, parameterClass)
              || ClassUtils.isAssignable(McpSyncServerExchange.class, parameterClass)
              || ClassUtils.isAssignable(McpAsyncServerExchange.class, parameterClass)
              || ClassUtils.isAssignable(CallToolRequest.class, parameterClass))) {
        continue;
      }

      // Handle @McpToolParams - unwrap DTO fields to root level
      if (parameter.isAnnotationPresent(McpToolParams.class)) {
        // Generate schema for the DTO type and merge its properties at root level
        ObjectNode dtoSchema = SUBTYPE_SCHEMA_GENERATOR.generateSchema(parameterType);

        // Extract properties from DTO schema
        if (dtoSchema.has("properties") && dtoSchema.get("properties").isObject()) {
          ObjectNode dtoProperties = (ObjectNode) dtoSchema.get("properties");
          dtoProperties
              .fields()
              .forEachRemaining(entry -> properties.set(entry.getKey(), entry.getValue()));
        }

        // Extract required fields from DTO schema
        if (dtoSchema.has("required") && dtoSchema.get("required").isArray()) {
          dtoSchema.get("required").forEach(requiredField -> required.add(requiredField.asText()));
        }

        continue; // Skip standard parameter handling
      }

      if (isMethodParameterRequired(method, i)) {
        required.add(parameterName);
      }
      ObjectNode parameterNode = SUBTYPE_SCHEMA_GENERATOR.generateSchema(parameterType);
      String parameterDescription = getMethodParameterDescription(method, i);
      if (Utils.hasText(parameterDescription)) {
        parameterNode.put("description", parameterDescription);
      }
      properties.set(parameterName, parameterNode);
    }

    var requiredArray = schema.putArray("required");
    required.forEach(requiredArray::add);

    return schema.toPrettyString();
  }

  public static String generateFromClass(Class<?> clazz) {
    Assert.notNull(clazz, "clazz cannot be null");
    return classSchemaCache.computeIfAbsent(
        clazz, CamundaJsonSchemaGenerator::internalGenerateFromClass);
  }

  private static String internalGenerateFromClass(Class<?> clazz) {
    return TYPE_SCHEMA_GENERATOR.generateSchema(clazz).toPrettyString();
  }

  public static String generateFromType(Type type) {
    Assert.notNull(type, "type cannot be null");
    return typeSchemaCache.computeIfAbsent(
        type, CamundaJsonSchemaGenerator::internalGenerateFromType);
  }

  private static String internalGenerateFromType(Type type) {
    return TYPE_SCHEMA_GENERATOR.generateSchema(type).toPrettyString();
  }

  /**
   * Check if a method has a CallToolRequest parameter.
   *
   * @param method The method to check
   * @return true if the method has a CallToolRequest parameter, false otherwise
   */
  public static boolean hasCallToolRequestParameter(Method method) {
    return Arrays.stream(method.getParameterTypes())
        .anyMatch(type -> CallToolRequest.class.isAssignableFrom(type));
  }

  private static boolean isMethodParameterRequired(Method method, int index) {
    Parameter parameter = method.getParameters()[index];

    var toolParamAnnotation = parameter.getAnnotation(McpToolParam.class);
    if (toolParamAnnotation != null) {
      return toolParamAnnotation.required();
    }

    var propertyAnnotation = parameter.getAnnotation(JsonProperty.class);
    if (propertyAnnotation != null) {
      return propertyAnnotation.required();
    }

    var schemaAnnotation = parameter.getAnnotation(Schema.class);
    if (schemaAnnotation != null) {
      return schemaAnnotation.requiredMode() == RequiredMode.REQUIRED
          || schemaAnnotation.requiredMode() == RequiredMode.AUTO
          || schemaAnnotation.required();
    }

    var nullableAnnotation = parameter.getAnnotation(Nullable.class);
    if (nullableAnnotation != null) {
      return false;
    }

    return PROPERTY_REQUIRED_BY_DEFAULT;
  }

  private static String getMethodParameterDescription(Method method, int index) {
    Parameter parameter = method.getParameters()[index];

    var toolParamAnnotation = parameter.getAnnotation(McpToolParam.class);
    if (toolParamAnnotation != null && Utils.hasText(toolParamAnnotation.description())) {
      return toolParamAnnotation.description();
    }

    var jacksonAnnotation = parameter.getAnnotation(JsonPropertyDescription.class);
    if (jacksonAnnotation != null && Utils.hasText(jacksonAnnotation.value())) {
      return jacksonAnnotation.value();
    }

    var schemaAnnotation = parameter.getAnnotation(Schema.class);
    if (schemaAnnotation != null && Utils.hasText(schemaAnnotation.description())) {
      return schemaAnnotation.description();
    }

    return null;
  }
}
