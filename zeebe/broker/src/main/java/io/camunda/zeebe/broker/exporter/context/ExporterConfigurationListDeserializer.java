/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.context;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.StdConverter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Delegating deserializer which converts a {@link Map} with all numeric keys into a list, where the
 * keys are the indices of the items in the list. This allows converting Spring's default {@link
 * java.util.LinkedHashMap} lists back into lists.
 *
 * <p>NOTE: this class expects the keys to start from 0 and increase by one in sequence, with no
 * gaps. This mirrors the Spring behavior which does not allow specifying gaps in list fields.
 *
 * <p>You can then configure this via Spring properties, e.g. {@code
 * camunda.broker.myConfiguration.myListProperty[0] = foo}
 */
final class ExporterConfigurationListDeserializer<E> extends JsonDeserializer<List<E>>
    implements ContextualDeserializer {

  private final JavaType contentType;

  public ExporterConfigurationListDeserializer() {
    this(TypeFactory.defaultInstance().constructType(Object.class));
  }

  private ExporterConfigurationListDeserializer(final JavaType contentType) {
    this.contentType = contentType;
  }

  @Override
  public JsonDeserializer<?> createContextual(
      final DeserializationContext ctxt, final BeanProperty property) throws JsonMappingException {
    final JavaType type;
    if (property == null) {
      // When property is null, use Object as fallback
      type = ctxt.getTypeFactory().constructType(Object.class);
    } else {
      type = property.getType().getContentType();
    }

    return new ExporterConfigurationListDeserializer<>(type);
  }

  @Override
  public List<E> deserialize(final JsonParser p, final DeserializationContext ctxt)
      throws IOException {
    return switch (p.currentToken()) {
      case START_ARRAY -> deserializeArray(p, ctxt);
      case START_OBJECT -> deserializeMapWithNumericKeys(p, ctxt);
      default ->
          throw new IllegalArgumentException(
              "Expected START_ARRAY or START_OBJECT, got: " + p.currentToken());
    };
  }

  /**
   * Deserializes a JSON array into a List. Delegates element deserialization to Jackson's standard
   * deserializers.
   */
  private List<E> deserializeArray(final JsonParser p, final DeserializationContext ctxt)
      throws IOException {
    final List<E> result = new ArrayList<>();
    while (p.nextToken() != JsonToken.END_ARRAY) {
      @SuppressWarnings("unchecked")
      final E value = (E) ctxt.readValue(p, contentType);
      result.add(value);
    }
    return result;
  }

  /**
   * Deserializes a JSON object (Map) with numeric string keys into a List. This handles Spring
   * Boot's indexed property syntax (e.g., {@code myList[0]=value}).
   */
  private List<E> deserializeMapWithNumericKeys(
      final JsonParser p, final DeserializationContext ctxt) throws IOException {
    // Delegate Map deserialization to Jackson
    @SuppressWarnings("unchecked")
    final Map<String, E> map =
        (Map<String, E>)
            ctxt.readValue(
                p,
                ctxt.getTypeFactory()
                    .constructMapType(Map.class, String.class, contentType.getRawClass()));
    // Convert Map with numeric keys to List
    return new MapListConverter<E>(contentType).convert(map);
  }

  private static final class MapListConverter<E> extends StdConverter<Map<String, E>, List<E>> {

    private final JavaType contentType;

    public MapListConverter(final JavaType contentType) {
      this.contentType = contentType;
    }

    @Override
    public List<E> convert(final Map<String, E> value) {
      // Validate that all keys are numeric before attempting conversion
      validateAllKeysAreNumeric(value);

      final ArrayList<E> list = new ArrayList<>(value.size());

      // sort the keys so we can access them in ascending order, avoiding index out of bounds due to
      // random access order of the map keys
      final var keys = new TreeSet<>(value.keySet());
      for (final var key : keys) {
        setListValue(value, key, list);
      }

      return list;
    }

    @Override
    public JavaType getInputType(final TypeFactory typeFactory) {
      return super.getInputType(typeFactory).withContentType(contentType);
    }

    @Override
    public JavaType getOutputType(final TypeFactory typeFactory) {
      return super.getOutputType(typeFactory).withContentType(contentType);
    }

    private void validateAllKeysAreNumeric(final Map<String, E> value) {
      for (final var key : value.keySet()) {
        try {
          Integer.parseInt(key);
        } catch (final NumberFormatException e) {
          throw new IllegalArgumentException(
              """
              Cannot convert Map to List: Map contains non-numeric keys. \
              This deserializer only converts Maps with numeric keys (from Spring Boot indexed properties like 'list[0]=value'). \
              Found non-numeric key: '%s'. If you need a Map, use Map type instead of List."""
                  .formatted(key),
              e);
        }
      }
    }

    private void setListValue(
        final Map<String, E> value, final String key, final ArrayList<E> list) {
      // Key is guaranteed to be numeric because we validated in convert()
      final int index = Integer.parseInt(key);

      try {
        list.add(index, value.get(key));
      } catch (final IndexOutOfBoundsException e) {
        throw new IndexOutOfBoundsException(
            """
            Failed to convert map of integers to list; tried to insert at [%d], but highest \
            index is [%d]. Check your configuration for errors when setting the index."""
                .formatted(index, list.size() - 1));
      }
    }
  }
}
