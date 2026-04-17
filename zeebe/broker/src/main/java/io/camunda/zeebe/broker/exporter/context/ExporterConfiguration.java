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
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.exporter.api.context.Configuration;
import io.camunda.zeebe.exporter.api.context.StrictConfiguration;
import io.camunda.zeebe.util.ReflectUtil;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record ExporterConfiguration(String id, Map<String, Object> arguments)
    implements Configuration {

  /**
   * Jackson ObjectMapper for deserialization (fromArgs). Configured for lenient deserialization
   * from Spring Boot properties, handling string-to-object conversions.
   *
   * <p>Features:
   *
   * <ul>
   *   <li>Kebab-case property naming strategy: both {@code myField} and {@code my-field} are
   *       accepted in YAML args. {@code asArgs}/{@code toArgs} serialise to kebab-case; {@code
   *       fromArgs}/{@code instantiate} first convert camelCase keys to kebab-case via {@code
   *       normalizeKeys}, then hand off to the MAPPER which matches via kebab-case canonical names
   *       and {@code ACCEPT_CASE_INSENSITIVE_PROPERTIES} (e.g. {@code my-field} and {@code
   *       MY-FIELD} are equivalent).
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
          .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
          .build();
  private static final Logger LOG = LoggerFactory.getLogger(ExporterConfiguration.class);
  /**
   * Strict variant of {@link #MAPPER} that rejects unknown properties. Used in {@link
   * #fromArgs(Class, Map)} to fail fast at startup when the exporter args contain a typo or an
   * unsupported property name.
   */
  private static final ObjectMapper STRICT_MAPPER =
      MAPPER.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

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
      // Normalize camelCase keys to kebab-case so that both forms are accepted in YAML args.
      // Spring Boot relaxed binding (camelCase ↔ kebab-case) only applies to
      // @ConfigurationProperties
      // beans and does NOT apply here — the args arrive as a raw Map<String, Object> that Jackson
      // binds directly. With KEBAB_CASE naming strategy the mapper expects kebab-case keys, so we
      // pre-process the map to convert any camelCase keys before handing off to convertValue().
      final var normalized = normalizeKeys(values);
      final var mapper =
          configClass.isAnnotationPresent(StrictConfiguration.class) ? STRICT_MAPPER : MAPPER;
      try {
        return mapper.convertValue(normalized, configClass);
      } catch (final IllegalArgumentException e) {
        if (e.getCause() instanceof final UnrecognizedPropertyException upe) {
          final String coloredMessage =
              "\u001B[1;31m❌ Unknown exporter configuration property"
                  + " '\u001B[0;31m"
                  + upe.getPropertyName()
                  + "\u001B[1;31m'"
                  + " in config class "
                  + configClass.getSimpleName()
                  + "\u001B[0m\n"
                  + "\u001B[33m   ⚠ Known properties: "
                  + upe.getKnownPropertyIds()
                  + "\u001B[0m\n"
                  + "   Fix the property name in your exporter args — the application cannot start.";
          LOG.error(coloredMessage);
          throw new IllegalArgumentException(
              "Unknown exporter configuration property '"
                  + upe.getPropertyName()
                  + "' in config class "
                  + configClass.getSimpleName()
                  + "; known properties: "
                  + upe.getKnownPropertyIds(),
              upe);
        }
        throw e;
      }
    } else {
      return ReflectUtil.newInstance(configClass);
    }
  }

  /**
   * Recursively converts all camelCase map keys to kebab-case. Leaves already-kebab-case and
   * numeric keys unchanged. Nested {@link Map} values are normalized in the same pass.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> normalizeKeys(final Map<String, Object> map) {
    final var result = new LinkedHashMap<String, Object>(map.size());
    for (final var entry : map.entrySet()) {
      final var key = camelToKebab(entry.getKey());
      final var value =
          entry.getValue() instanceof final Map<?, ?> nested
              ? normalizeKeys((Map<String, Object>) nested)
              : entry.getValue();
      result.put(key, value);
    }
    return result;
  }

  /** Converts a camelCase or PascalCase string to kebab-case. Kebab-case input is unchanged. */
  private static String camelToKebab(final String name) {
    return name.replaceAll("([a-z\\d])([A-Z])", "$1-$2").toLowerCase();
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
