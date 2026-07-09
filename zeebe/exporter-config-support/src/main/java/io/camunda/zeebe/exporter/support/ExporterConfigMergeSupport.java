/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.support;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Single owner of the type-aware exporter-args key normalization and deep-merge semantics (ADR-0008
 * §4). Two callers share it so their semantics cannot drift apart:
 *
 * <ul>
 *   <li>the broker's {@code ExporterConfiguration.fromArgs} normalizes an args map against the
 *       exporter's config class before binding it, so relaxed spellings ({@code index-prefix},
 *       {@code INDEX_PREFIX}, {@code indexPrefix}) collapse to one canonical key;
 *   <li>{@code ExporterConfigMerger} implementations normalize the root and tenant args maps the
 *       same way before deep-merging them per physical tenant.
 * </ul>
 *
 * <p>Both operations are <em>type-aware</em>: the exporter's config class is introspected via
 * Jackson, and only keys that correspond to configuration properties are normalized (and merged
 * recursively for nested POJO properties), while the keys of {@code Map}-typed fields are user data
 * and preserved untouched.
 */
public final class ExporterConfigMergeSupport {

  /**
   * Used only to construct {@link JavaType}s and introspect config classes for their properties —
   * never to bind or convert values. Property discovery does not depend on any of the lenient
   * deserialization features the broker's binding mapper configures, so a plain mapper yields the
   * same property set.
   */
  private static final ObjectMapper INTROSPECTION_MAPPER = new ObjectMapper();

  private ExporterConfigMergeSupport() {}

  /**
   * Normalises keys that represent configuration properties of {@code configClass} to lowercase
   * with separators stripped, so that {@code myField}, {@code my-field}, {@code MY-FIELD}, and
   * {@code myfield} all map to the same canonical key.
   *
   * <p>The normalisation is type-aware: object-property maps are normalised recursively, while map
   * keys of {@code Map<K, V>} fields are preserved as user data.
   *
   * @param configClass the exporter's configuration class the args bind into
   * @param args the raw exporter args map
   * @return the same structure with normalised lowercase keys
   */
  public static Map<String, Object> normalize(
      final Class<?> configClass, final Map<String, Object> args) {
    return normalizeKeys(args, INTROSPECTION_MAPPER.constructType(configClass));
  }

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
        INTROSPECTION_MAPPER.getDeserializationConfig().introspect(targetType).findProperties();
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
}
