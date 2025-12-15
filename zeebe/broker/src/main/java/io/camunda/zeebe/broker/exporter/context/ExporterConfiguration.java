/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.context;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.exporter.api.context.Configuration;
import io.camunda.zeebe.util.ReflectUtil;
import java.util.List;
import java.util.Map;

public record ExporterConfiguration(String id, Map<String, Object> arguments)
    implements Configuration {

  // Accepts more lenient cases, such that the property "something" would match a field "someThing"
  // Note however that if a field "something" and "someThing" are present, only one of them will be
  // instantiated (the last declared one), using the last matching value.
  public static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .addModule(new JavaTimeModule())
          .addModule(
              new SimpleModule()
                  .addDeserializer(List.class, new ExporterConfigurationListDeserializer<>()))
          .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
          .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
          .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES)
          .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .build();

  @Override
  public String getId() {
    return id;
  }

  @Override
  public Map<String, Object> getArguments() {
    return arguments;
  }

  @Override
  public <T> T instantiate(final Class<T> configClass) {
    return fromArgs(configClass, arguments);
  }

  public static <T> T fromArgs(final Class<T> configClass, final Map<String, Object> values) {
    if (values != null) {
      // Use MAPPER (with custom list deserializer) to properly handle Spring Boot indexed
      // properties like myList[0]=value which are stored as Maps with numeric string keys
      return MAPPER.convertValue(values, configClass);
    } else {
      return ReflectUtil.newInstance(configClass);
    }
  }

  /**
   * Creates a fluent API wrapper for convenient configuration mapping. Enables chaining operations
   * like: <code>
   * ExporterConfiguration.of(ConfigClass.class, args)
   *     .map(config -> { config.setSomething(...); return config; })
   *     .toArgs()
   * </code>
   */
  public static <T> ConfigurationMapper<T> of(
      final Class<T> configClass, final Map<String, Object> values) {
    return new ConfigurationMapper<>(fromArgs(configClass, values));
  }

  public static <T> Map<String, Object> asArgs(final T config) {
    return MAPPER.convertValue(config, new TypeReference<>() {});
  }

  /**
   * Fluent API wrapper for configuration mapping operations. Allows chaining transformations before
   * converting back to args map.
   */
  public static class ConfigurationMapper<T> {
    private final T config;

    private ConfigurationMapper(final T config) {
      this.config = config;
    }

    public ConfigurationMapper apply(final java.util.function.Consumer<T> mapper) {
      mapper.accept(config);
      return this;
    }

    /** Returns the wrapped configuration object. */
    public T get() {
      return config;
    }

    /** Converts the configuration back to an args map. */
    public Map<String, Object> toArgs() {
      return asArgs(config);
    }
  }
}
