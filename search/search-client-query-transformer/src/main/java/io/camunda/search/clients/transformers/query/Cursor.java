/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import java.util.Base64;

public class Cursor {

  static final JsonMapper MAPPER =
      JsonMapper.builder().enable(DeserializationFeature.USE_LONG_FOR_INTS).build();

  public static String encode(final Object[] values) {
    if (values == null || values.length == 0) {
      return null;
    }

    try {
      final var value = MAPPER.writeValueAsString(values);

      return Base64.getEncoder().encodeToString(value.getBytes());
    } catch (final Exception e) {
      throw new CamundaSearchException(
          "Cannot encode data store pagination information in cursor",
          e,
          Reason.SEARCH_CLIENT_FAILED);
    }
  }

  public static Object[] decode(final String cursor) {
    if (cursor == null || cursor.isEmpty()) {
      return null;
    }

    try {
      final var decodedCursor = Base64.getDecoder().decode(cursor);

      return MAPPER.readValue(decodedCursor, Object[].class);
    } catch (final Exception e) {
      throw new CamundaSearchException(
          "Cannot decode pagination cursor '%s'".formatted(cursor), e, Reason.INVALID_ARGUMENT);
    }
  }
}
