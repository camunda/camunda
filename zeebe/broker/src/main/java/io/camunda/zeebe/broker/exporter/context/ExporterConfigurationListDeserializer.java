/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.context;

import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDelegatingDeserializer;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.StdConverter;
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
final class ExporterConfigurationListDeserializer<E> extends StdDelegatingDeserializer<List<E>> {

  public ExporterConfigurationListDeserializer() {
    super(new MapListConverter<>());
  }

  @Override
  public JsonDeserializer<?> createContextual(
      final DeserializationContext ctxt, final BeanProperty property) throws JsonMappingException {
    return new StdDelegatingDeserializer<List<E>>(
            new MapListConverter<>(property.getType().getContentType()))
        .createContextual(ctxt, property);
  }

  private static final class MapListConverter<E> extends StdConverter<Map<String, E>, List<E>> {

    private final JavaType contentType;

    public MapListConverter() {
      this(TypeFactory.defaultInstance().constructType(Object.class));
    }

    public MapListConverter(final JavaType contentType) {
      this.contentType = contentType;
    }

    @Override
    public List<E> convert(final Map<String, E> value) {
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

    private void setListValue(
        final Map<String, E> value, final String key, final ArrayList<E> list) {
      final int index;
      try {
        index = Integer.parseInt(key);
      } catch (final NumberFormatException e) {
        throw new IllegalArgumentException(
            "Failed to convert a map of integer to list; at least one key is not a number: [%s]"
                .formatted(key),
            e);
      }

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
