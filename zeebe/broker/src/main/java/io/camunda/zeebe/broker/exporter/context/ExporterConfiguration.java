/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.context;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.exporter.api.context.Configuration;
import io.camunda.zeebe.util.ReflectUtil;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record ExporterConfiguration(String id, Map<String, Object> arguments)
    implements Configuration {

  /**
   * Jackson ObjectMapper for deserialization (fromArgs). Configured for lenient deserialization
   * from Spring Boot properties, handling string-to-object conversions.
   *
   * <p>Features:
   *
   * <ul>
   *   <li>Case-insensitive enum, property, and value matching
   *   <li>Custom List deserializer for Spring Boot indexed properties (myList[0]=value)
   *   <li>Custom Path module to preserve relative/absolute paths (no resolution)
   *   <li>Custom Duration module to preserve Duration objects (no conversion to BigDecimal)
   *   <li>JavaTimeModule for other temporal types (Instant, LocalDateTime, etc.)
   * </ul>
   */
  public static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .addModule(new JavaTimeModule())
          .addModule(createDurationModule())
          .addModule(createPathModule())
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
   *     .apply(config -> config.setSomething(...))
   *     .toArgs()
   * </code>
   */
  public static <T> ConfigurationMapper<T> of(
      final Class<T> configClass, final Map<String, Object> values) {
    return new ConfigurationMapper<>(fromArgs(configClass, values));
  }

  public static <T> Map<String, Object> asArgs(final T config) {
    return MAPPER.convertValue(config, Map.class);
  }

  /**
   * Creates a Jackson module for Duration serialization/deserialization that handles Duration
   * objects properly.
   *
   * <p>Serialization: Writes Duration as POJO (preserves Duration object in Maps from convertValue)
   *
   * <p>Deserialization: Parses Duration strings ("PT0.5S" → Duration.ofMillis(500))
   */
  private static SimpleModule createDurationModule() {
    return new SimpleModule()
        .addSerializer(
            java.time.Duration.class,
            new com.fasterxml.jackson.databind.JsonSerializer<java.time.Duration>() {
              @Override
              public void serialize(
                  final java.time.Duration value,
                  final com.fasterxml.jackson.core.JsonGenerator gen,
                  final com.fasterxml.jackson.databind.SerializerProvider serializers)
                  throws java.io.IOException {
                // Write as embedded POJO to preserve Duration object when using convertValue()
                // For TokenBuffer (used by convertValue), this keeps the Duration object as-is
                if (gen instanceof com.fasterxml.jackson.databind.util.TokenBuffer) {
                  gen.writeEmbeddedObject(value);
                } else {
                  // For actual JSON output, write as ISO-8601 string
                  gen.writeString(value.toString());
                }
              }
            })
        .addDeserializer(
            java.time.Duration.class,
            new com.fasterxml.jackson.databind.JsonDeserializer<java.time.Duration>() {
              @Override
              public java.time.Duration deserialize(
                  final com.fasterxml.jackson.core.JsonParser p,
                  final com.fasterxml.jackson.databind.DeserializationContext ctxt)
                  throws java.io.IOException {
                // Handle embedded Duration objects (from TokenBuffer used by convertValue)
                if (p.currentToken()
                    == com.fasterxml.jackson.core.JsonToken.VALUE_EMBEDDED_OBJECT) {
                  final Object embedded = p.getEmbeddedObject();
                  if (embedded instanceof java.time.Duration) {
                    return (java.time.Duration) embedded;
                  }
                }
                // Parse from string format like "PT0.5S" (from Spring Boot properties)
                final String text = p.getText();
                if (text != null && !text.isEmpty()) {
                  return java.time.Duration.parse(text);
                }
                return null;
              }
            });
  }

  /**
   * Creates a Jackson module for Path serialization/deserialization that preserves the original
   * path representation (relative vs absolute) without any path resolution.
   *
   * <p>Serialization: Converts Path to its string representation using toString()
   *
   * <p>Deserialization: Converts string to Path using Path.of() without resolution
   */
  private static SimpleModule createPathModule() {
    return new SimpleModule()
        .addSerializer(
            Path.class,
            new com.fasterxml.jackson.databind.JsonSerializer<Path>() {
              @Override
              public void serialize(
                  final Path value,
                  final com.fasterxml.jackson.core.JsonGenerator gen,
                  final com.fasterxml.jackson.databind.SerializerProvider serializers)
                  throws java.io.IOException {
                // Serialize Path as its string representation, preserving relative/absolute form
                gen.writeString(value.toString());
              }
            })
        .addDeserializer(
            Path.class,
            new com.fasterxml.jackson.databind.JsonDeserializer<Path>() {
              @Override
              public Path deserialize(
                  final com.fasterxml.jackson.core.JsonParser p,
                  final com.fasterxml.jackson.databind.DeserializationContext ctxt)
                  throws java.io.IOException {
                // Deserialize string to Path using Path.of(), preserving relative/absolute form
                return Path.of(p.getText());
              }
            });
  }

  /**
   * Fluent API wrapper for configuration mapping operations. Allows chaining transformations before
   * converting back to args map.
   */
  public static final class ConfigurationMapper<T> {
    private final T config;

    private ConfigurationMapper(final T config) {
      this.config = config;
    }

    /**
     * Applies a transformation to the configuration using a Consumer. Useful for modifying the
     * configuration without needing to return it.
     *
     * @param consumer the consumer that modifies the configuration
     * @return this mapper for method chaining
     */
    public ConfigurationMapper<T> apply(final java.util.function.Consumer<T> consumer) {
      consumer.accept(config);
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
