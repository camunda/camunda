/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.db.rdbms.sql.columns.SearchColumn;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.IntStream;

public class Cursor<T> {
  static final PolymorphicTypeValidator ptv =
      BasicPolymorphicTypeValidator.builder()
          .allowIfBaseType(Number.class)
          .allowIfBaseType(String.class)
          .build();

  static final PolymorphicTypeValidator ptv2 =
      BasicPolymorphicTypeValidator.builder()
          .allowIfSubType(Long.class)
          .allowIfSubType(Integer.class)
          .allowIfSubType(String.class)
          .allowIfSubType(OffsetDateTime.class)
          .allowIfSubType(Object[].class)
          .build();

  static final JsonMapper mapper =
      JsonMapper.builder()
          .addModule(new JavaTimeModule())
          .disable((SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
          .enable(DeserializationFeature.USE_LONG_FOR_INTS)
          .activateDefaultTyping(ptv2, DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY)
          .build();

  public String encode(final T entity, final List<SearchColumn<T>> columns) {
    try {
      final var fields = columns.stream().map(s -> s.getPropertyValue(entity)).toArray();
      final var value = mapper.writeValueAsString(fields);

      return Base64.getEncoder().encodeToString(value.getBytes());
    } catch (final JsonProcessingException e) {
      // log warning
      return null;
    }
  }

  public Object[] decode(final String cursor, final List<SearchColumn<T>> columns) {
    try {
      final var decodedCursor = java.util.Base64.getDecoder().decode(cursor);
      final var values =
          mapper.readValue(
              decodedCursor,
              new TypeReference<Object[]>() {}); // new TypeReference<List<Object>>() {}

      return IntStream.range(0, columns.size())
          .mapToObj(i -> columns.get(i).convertSortOption(values[i]))
          .toArray();

    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
