/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableSet;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Slf4j
public class ConfigurationParser {

  private static final String ENGINES_FIELD = "engines";
  private static final Pattern VARIABLE_PLACEHOLDER_PATTERN = Pattern.compile(
    "\\$\\{([a-zA-Z_]+[a-zA-Z0-9_]*)(:(.*))?}"
  );
  // explicit yaml null values see https://yaml.org/type/null.html
  private static final Set<String> YAML_EXPLICIT_NULL_VALUES = ImmutableSet.of("~", "null", "Null", "NULL");

  public static Optional<DocumentContext> parseConfigFromLocations(List<InputStream> sources) {
    YAMLMapper yamlMapper = configureConfigMapper();
    try {
      if (sources.isEmpty()) {
        return Optional.empty();
      }
      //read default values from the first location
      JsonNode resultNode = yamlMapper.readTree(sources.remove(0));
      //read with overriding default values all locations
      for (InputStream inputStream : sources) {
        merge(resultNode, yamlMapper.readTree(inputStream));
      }

      // resolve environment placeholders
      Map<String, Object> rawConfigMap =
        yamlMapper.convertValue(resultNode, new TypeReference<Map<String, Object>>() {});
      final Map<String, Object> configMap = resolveVariablePlaceholders(rawConfigMap);

      //prepare to work with JSON Path
      return Optional.of(JsonPath.parse(configMap));
    } catch (IOException e) {
      log.error("error reading configuration", e);
      return Optional.empty();
    }
  }

  private static Map<String, Object> resolveVariablePlaceholders(final Map<String, Object> configMap) {
    return configMap.entrySet().stream()
      .peek(entry -> {
        final Object newValue = resolveVariablePlaceholders(entry.getValue());
        entry.setValue(newValue);
      })
      .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), HashMap::putAll);
  }

  private static Object resolveVariablePlaceholders(final Object value) {
    Object newValue = value;
    if (value instanceof Map) {
      newValue = resolveVariablePlaceholders((Map<String, Object>) value);
    } else if (value instanceof List) {
      final List<Object> values = ((List<Object>) value);
      if (values.size() > 0) {
        newValue = values.stream().map(ConfigurationParser::resolveVariablePlaceholders)
          .collect(Collectors.toList());
      }
    } else if (value instanceof String) {
      newValue = resolveVariablePlaceholders((String) value);
    }
    return newValue;
  }

  private static String resolveVariablePlaceholders(final String rawValue) {
    String resolvedValue = rawValue;
    final Matcher matcher = VARIABLE_PLACEHOLDER_PATTERN.matcher(rawValue);
    while (matcher.find()) {
      final String envVariableName = matcher.group(1);
      String envVariableValue = Optional.ofNullable(System.getProperty(envVariableName, null))
        .orElseGet(() -> System.getenv(envVariableName));
      if (envVariableValue == null) {
        final String envVariableDefaultValue = matcher.group(3);
        if (envVariableDefaultValue == null) {
          throw new OptimizeConfigurationException(String.format(
            "Could not resolve system/environment variable [%s] used in configuration and no default value supplied",
            envVariableName
          ));
        }
        envVariableValue = YAML_EXPLICIT_NULL_VALUES.contains(envVariableDefaultValue) ? null : envVariableDefaultValue;
      }
      resolvedValue = Optional.ofNullable(envVariableValue)
        .map(value -> rawValue.replace(matcher.group(), value))
        .orElse(null);
    }
    return resolvedValue;
  }

  private static void merge(JsonNode mainNode, JsonNode updateNode) {
    if (updateNode == null) {
      return;
    }

    Iterator<String> fieldNames = updateNode.fieldNames();
    while (fieldNames.hasNext()) {

      String fieldName = fieldNames.next();
      JsonNode jsonNode = mainNode.get(fieldName);
      // if field exists and is an embedded object
      if (jsonNode != null && jsonNode.isObject() && !ENGINES_FIELD.equals(fieldName)) {
        merge(jsonNode, updateNode.get(fieldName));
      } else if (jsonNode != null && jsonNode.isObject() && ENGINES_FIELD.equals(fieldName)) {
        // Overwrite field
        overwriteField((ObjectNode) mainNode, updateNode, fieldName);
      } else if (mainNode instanceof ObjectNode) {
        // Overwrite field
        overwriteField((ObjectNode) mainNode, updateNode, fieldName);
      }

    }
  }

  private static void overwriteField(ObjectNode mainNode, JsonNode updateNode, String fieldName) {
    JsonNode value = updateNode.get(fieldName);
    mainNode.set(fieldName, value);
  }

  private static YAMLMapper configureConfigMapper() {
    final YAMLMapper yamlMapper = new YAMLMapper();
    yamlMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
    // to parse dates
    yamlMapper.registerModule(new JavaTimeModule());
    //configure Jackson as provider in order to be able to use TypeRef objects
    //during serialization process
    Configuration.setDefaults(new Configuration.Defaults() {
      private final ObjectMapper objectMapper =
        new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

      private final JsonProvider jsonProvider = new JacksonJsonProvider(objectMapper);
      private final MappingProvider mappingProvider = new JacksonMappingProvider(objectMapper);

      @Override
      public JsonProvider jsonProvider() {
        return jsonProvider;
      }

      @Override
      public MappingProvider mappingProvider() {
        return mappingProvider;
      }

      @Override
      public Set<Option> options() {
        return EnumSet.noneOf(Option.class);
      }
    });
    return yamlMapper;
  }
}
