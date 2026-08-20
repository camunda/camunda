/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.context;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.exporter.api.context.Configuration;
import io.camunda.zeebe.exporter.api.context.StrictConfiguration;
import io.camunda.zeebe.exporter.support.ExporterConfigMergeSupport;
import io.camunda.zeebe.util.ReflectUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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
   *   <li>All incoming keys are normalised to lowercase with separators stripped ({@code
   *       normalizeKeys}), so {@code myField}, {@code my-field}, {@code MY-FIELD}, and {@code
   *       myfield} are all equivalent. {@code ACCEPT_CASE_INSENSITIVE_PROPERTIES} then matches the
   *       normalised key to the Java field name.
   *   <li>Case-insensitive enum, property, and value matching
   *   <li>Custom List deserializer for Spring Boot indexed properties (myList[0]=value)
   *   <li>Custom Path module to preserve relative/absolute paths (no resolution)
   *   <li>Custom Duration module to preserve Duration objects (no conversion to BigDecimal)
   *   <li>JavaTimeModule for other temporal types (Instant, LocalDateTime, etc.)
   * </ul>
   */
  static final ObjectMapper MAPPER =
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

  private static final Logger LOG = LoggerFactory.getLogger(ExporterConfiguration.class);

  /**
   * Strict variant of {@link #MAPPER} that rejects unknown properties. Used in {@link
   * #fromArgs(Class, Map)} to fail fast at startup when the exporter args contain a typo or an
   * unsupported property name.
   */
  private static final ObjectMapper STRICT_MAPPER =
      MAPPER.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

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
      // Normalize all keys to lowercase-no-separator so that camelCase, kebab-case, and the
      // all-lowercase concatenated form produced by Spring Boot env var lowercasing are all
      // equivalent. ACCEPT_CASE_INSENSITIVE_PROPERTIES then matches the normalised key to the
      // Java field name regardless of original casing. The type-aware walk is shared with the
      // per-physical-tenant ExporterConfigMerger implementations (single owner of the
      // normalization semantics, ADR-0008 §4).
      final var normalized = ExporterConfigMergeSupport.normalize(configClass, values);
      final var mapper =
          configClass.isAnnotationPresent(StrictConfiguration.class) ? STRICT_MAPPER : MAPPER;
      try {
        return mapper.convertValue(normalized, configClass);
      } catch (final IllegalArgumentException e) {
        if (e.getCause() instanceof final UnrecognizedPropertyException upe) {
          // getReferringClass() is the actual class that owns the unrecognized property.
          // When the typo is inside a nested object it differs from configClass, so prefer it;
          // fall back to configClass only if Jackson did not populate it.
          final var referringClass = upe.getReferringClass();
          final var className =
              referringClass != null ? referringClass.getSimpleName() : configClass.getSimpleName();
          // getPathReference() produces a human-readable path like
          // "ElasticsearchExporterConfiguration[\"index\"]->IndexConfiguration[\"unknownProp\"]"
          // which lets users locate the typo even in deeply nested configs.
          final var message =
              "Unknown exporter configuration property '"
                  + upe.getPropertyName()
                  + "' in "
                  + className
                  + " (path: "
                  + upe.getPathReference()
                  + "); known properties: "
                  + upe.getKnownPropertyIds();
          LOG.error(message);
          throw new IllegalArgumentException(message, upe);
        }
        throw e;
      }
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
    return MAPPER.convertValue(config, MAP_TYPE);
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
            Duration.class,
            new JsonSerializer<>() {
              @Override
              public void serialize(
                  final Duration value,
                  final JsonGenerator gen,
                  final SerializerProvider serializers)
                  throws IOException {
                if (gen instanceof TokenBuffer) {
                  gen.writeEmbeddedObject(value);
                } else {
                  gen.writeString(value.toString());
                }
              }
            })
        .addDeserializer(
            Duration.class,
            new JsonDeserializer<>() {
              @Override
              public Duration deserialize(final JsonParser p, final DeserializationContext ctxt)
                  throws IOException {
                if (p.currentToken() == JsonToken.VALUE_EMBEDDED_OBJECT) {
                  final Object embedded = p.getEmbeddedObject();
                  if (embedded instanceof final Duration duration) {
                    return duration;
                  }
                }
                final String text = p.getText();
                if (text != null && !text.isEmpty()) {
                  return Duration.parse(text);
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
            new JsonSerializer<>() {
              @Override
              public void serialize(
                  final Path value, final JsonGenerator gen, final SerializerProvider serializers)
                  throws IOException {
                gen.writeString(value.toString());
              }
            })
        .addDeserializer(
            Path.class,
            new JsonDeserializer<>() {
              @Override
              public Path deserialize(final JsonParser p, final DeserializationContext ctxt)
                  throws IOException {
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
    public ConfigurationMapper<T> apply(final Consumer<T> consumer) {
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
