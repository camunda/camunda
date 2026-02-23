/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.gateway.mcp.config.schema;

import static io.camunda.gateway.mcp.config.tool.McpToolUtils.isFrameworkParameter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import io.camunda.gateway.mcp.config.tool.McpToolParamsUnwrapped;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.util.Assert;
import io.modelcontextprotocol.util.Utils;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springaicommunity.mcp.method.tool.utils.ConcurrentReferenceHashMap;
import org.springaicommunity.mcp.method.tool.utils.JsonParser;
import org.springaicommunity.mcp.method.tool.utils.JsonSchemaGenerator;
import org.springaicommunity.mcp.method.tool.utils.SpringAiSchemaModule;
import org.springframework.lang.Nullable;

/**
 * This is an adapted variant of {@link JsonSchemaGenerator}, configured to inline defs and with
 * support for {@link McpToolParamsUnwrapped} expansion.
 */
public class CamundaJsonSchemaGenerator {

  private static final SchemaVersion SCHEMA_VERSION = SchemaVersion.DRAFT_2020_12;
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
    final Module jacksonModule =
        new JacksonModule(
            JacksonOption.RESPECT_JSONPROPERTY_REQUIRED,
            JacksonOption.FLATTENED_ENUMS_FROM_JSONVALUE);
    final Module openApiModule = new Swagger2Module();
    final Module springAiSchemaModule =
        CamundaJsonSchemaGenerator.PROPERTY_REQUIRED_BY_DEFAULT
            ? new SpringAiSchemaModule()
            : new SpringAiSchemaModule(
                SpringAiSchemaModule.Option.PROPERTY_REQUIRED_FALSE_BY_DEFAULT);

    return new SchemaGeneratorConfigBuilder(SCHEMA_VERSION, OptionPreset.PLAIN_JSON)
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
    final ObjectNode schema = createEmptySchema();

    // return minimal schema if the method accepts only a CallToolRequest
    if (Arrays.stream(method.getParameterTypes())
        .anyMatch(CallToolRequest.class::isAssignableFrom)) {
      final boolean hasNonFrameworkParams =
          Arrays.stream(method.getParameters()).anyMatch(param -> !isFrameworkParameter(param));

      if (!hasNonFrameworkParams) {
        return schema.toPrettyString();
      }
    }

    final ObjectNode properties = schema.withObject("properties");
    final ArrayNode required = schema.withArray("required");

    Arrays.stream(method.getParameters())
        .filter(parameter -> !isFrameworkParameter(parameter))
        .forEach(
            parameter -> {
              final String parameterName = parameter.getName();

              final ObjectNode parameterNode =
                  subtypeSchemaGenerator.generateSchema(parameter.getParameterizedType());

              // handle @McpToolParamsUnwrapped - unwrap DTO fields to root level
              if (parameter.isAnnotationPresent(McpToolParamsUnwrapped.class)) {
                if (parameterNode.has("properties") && parameterNode.get("properties").isObject()) {
                  parameterNode
                      .withObject("properties")
                      .properties()
                      .forEach(entry -> properties.set(entry.getKey(), entry.getValue()));
                }

                if (parameterNode.has("required") && parameterNode.get("required").isArray()) {
                  parameterNode
                      .withArray("required")
                      .forEach(requiredField -> required.add(requiredField.asText()));
                }
              } else {
                final String parameterDescription = getMethodParameterDescription(parameter);
                if (Utils.hasText(parameterDescription)) {
                  parameterNode.put("description", parameterDescription);
                }

                properties.set(parameterName, parameterNode);

                if (isMethodParameterRequired(parameter)) {
                  required.add(parameterName);
                }
              }
            });

    return schema.toPrettyString();
  }

  public String generateFromType(final Type type) {
    Assert.notNull(type, "type cannot be null");
    return typeSchemaCache.computeIfAbsent(type, this::internalGenerateFromType);
  }

  private String internalGenerateFromType(final Type type) {
    return typeSchemaGenerator.generateSchema(type).toPrettyString();
  }

  private ObjectNode createEmptySchema() {
    final ObjectNode schema = objectMapper.createObjectNode();
    schema.put("$schema", SCHEMA_VERSION.getIdentifier());
    schema.put("type", "object");
    schema.putObject("properties");
    schema.putArray("required");
    return schema;
  }

  private boolean isMethodParameterRequired(final Parameter parameter) {
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

  private String getMethodParameterDescription(final Parameter parameter) {
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
