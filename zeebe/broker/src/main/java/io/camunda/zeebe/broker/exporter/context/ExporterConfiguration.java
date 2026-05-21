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
import com.fasterxml.jackson.databind.JavaType;
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
import io.camunda.zeebe.util.ReflectUtil;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
      // Java field name regardless of original casing.
      final var normalized = normalizeKeys(values, MAPPER.constructType(configClass));
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
   * Normalises keys that represent configuration properties to lowercase with separators stripped,
   * so that {@code myField}, {@code my-field}, {@code MY-FIELD}, and {@code myfield} all map to the
   * same canonical key.
   *
   * <p>The normalisation is type-aware: object-property maps are normalised recursively, while map
   * keys of {@code Map<K, V>} fields are preserved as user data.
   *
   * @param map the raw exporter args map
   * @return the same structure with normalised lowercase keys
   */
  private static Map<String, Object> normalizeKeys(
      final Map<String, Object> map, final JavaType targetType) {
    final var result = new LinkedHashMap<String, Object>(map.size());
    final var propertyTypesByKey = propertyTypesByNormalizedKey(targetType);
    final var indexedElementType = indexedElementType(targetType, map);
    for (final var entry : map.entrySet()) {
      final var normalizedKey = normalizeKey(entry.getKey());
      var propertyType = propertyTypesByKey.get(normalizedKey);
      if (propertyType == null) {
        propertyType = indexedElementType;
      }
      result.put(normalizedKey, normalizeValue(entry.getValue(), propertyType));
    }
    return result;
  }

  private static JavaType indexedElementType(
      final JavaType targetType, final Map<String, Object> map) {
    if (targetType == null || (!targetType.isCollectionLikeType() && !targetType.isArrayType())) {
      return null;
    }

    var hasNumericKeys = false;
    var hasNonNumericKeys = false;
    for (final var key : map.keySet()) {
      try {
        Integer.parseInt(key);
        hasNumericKeys = true;
      } catch (final NumberFormatException e) {
        hasNonNumericKeys = true;
      }
    }

    if (hasNumericKeys && hasNonNumericKeys) {
      throw new IllegalArgumentException(
          "Cannot mix indexed (numeric) and named keys in configuration map targeting a collection: "
              + map.keySet());
    }

    return hasNumericKeys ? targetType.getContentType() : null;
  }

  private static Map<String, JavaType> propertyTypesByNormalizedKey(final JavaType targetType) {
    if (targetType == null || targetType.isMapLikeType() || targetType.isContainerType()) {
      return Map.of();
    }

    final var properties =
        MAPPER.getDeserializationConfig().introspect(targetType).findProperties();
    if (properties.isEmpty()) {
      return Map.of();
    }

    final var propertyTypes = new LinkedHashMap<String, JavaType>(properties.size());
    for (final var property : properties) {
      final var propertyType = property.getPrimaryType();
      if (propertyType != null) {
        propertyTypes.put(normalizeKey(property.getName()), propertyType);
      }
    }
    return propertyTypes;
  }

  private static Object normalizeValue(final Object value, final JavaType targetType) {
    if (value instanceof final Map<?, ?> nestedMap) {
      return normalizeMapValue(nestedMap, targetType);
    }

    if (value instanceof final Iterable<?> iterable) {
      return normalizeIterableValue(iterable, targetType);
    }

    if (value != null && value.getClass().isArray()) {
      return normalizeArrayValue(value, targetType);
    }

    return value;
  }

  private static Object normalizeMapValue(final Map<?, ?> nestedMap, final JavaType targetType) {
    if (targetType == null || targetType.hasRawClass(Object.class)) {
      return nestedMap;
    }

    if (targetType.isMapLikeType()) {
      return normalizeMapValues(nestedMap, targetType.getContentType());
    }

    @SuppressWarnings("unchecked")
    final var typedNested = (Map<String, Object>) nestedMap;
    return normalizeKeys(typedNested, targetType);
  }

  private static List<Object> normalizeIterableValue(
      final Iterable<?> iterable, final JavaType targetType) {
    final var contentType =
        targetType != null && targetType.isCollectionLikeType()
            ? targetType.getContentType()
            : null;
    final var normalized = new ArrayList<>();
    for (final var item : iterable) {
      normalized.add(normalizeValue(item, contentType));
    }
    return normalized;
  }

  private static Object[] normalizeArrayValue(final Object value, final JavaType targetType) {
    final var length = Array.getLength(value);
    final var contentType =
        targetType != null && targetType.isArrayType() ? targetType.getContentType() : null;
    final var normalized = new Object[length];
    for (int i = 0; i < length; i++) {
      normalized[i] = normalizeValue(Array.get(value, i), contentType);
    }
    return normalized;
  }

  private static Map<Object, Object> normalizeMapValues(
      final Map<?, ?> map, final JavaType contentType) {
    final var result = new LinkedHashMap<Object, Object>(map.size());
    for (final var entry : map.entrySet()) {
      result.put(entry.getKey(), normalizeValue(entry.getValue(), contentType));
    }
    return result;
  }

  /** Strips hyphens and lowercases a key so all naming styles map to the same canonical form. */
  private static String normalizeKey(final String name) {
    return name.replace("-", "").toLowerCase(Locale.ROOT);
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
