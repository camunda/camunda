/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.util.Base64;

public class Cursor {

  static final JsonMapper mapper =
      JsonMapper.builder().enable(DeserializationFeature.USE_LONG_FOR_INTS).build();

  public static String encode(final Object[] values) {
    if (values == null || values.length == 0) {
      return null;
    }

    try {
      final var value = mapper.writeValueAsString(values);
      return Base64.getEncoder().encodeToString(value.getBytes());
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public static Object[] decode(final String cursor) {
    if (cursor == null || cursor.isEmpty()) {
      return null;
    }

    try {
      final var decodedCursor = Base64.getDecoder().decode(cursor);
      return mapper.readValue(decodedCursor, Object[].class);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static class SlimTypeIdResolver extends TypeIdResolverBase {
    @Override
    public String idFromValue(final Object value) {
      if (value instanceof Long) {
        return "long";
      }
      if (value instanceof Integer) {
        return "int";
      }
      if (value instanceof String) {
        return "string";
      }
      if (value instanceof Double) {
        return "double";
      }
      return "unknown";
    }

    @Override
    public String idFromValueAndType(final Object value, final Class<?> suggestedType) {
      return idFromValue(value);
    }

    @Override
    public JsonTypeInfo.Id getMechanism() {
      return JsonTypeInfo.Id.CUSTOM;
    }

    @Override
    public JavaType typeFromId(final DatabindContext context, final String id) {
      switch (id) {
        case "long":
          return context.constructType(Long.class);
        case "int":
          return context.constructType(Integer.class);
        case "string":
          return context.constructType(String.class);
        case "double":
          return context.constructType(Double.class);
        default:
          throw new IllegalArgumentException("Unknown type id: " + id);
      }
    }
  }

  public class LongWithTypeInfoSerializer extends StdSerializer<Long> {

    public LongWithTypeInfoSerializer() {
      super(Long.class);
    }

    @Override
    public void serialize(final Long value, final JsonGenerator gen, final SerializerProvider provider) throws IOException {
      gen.writeNumber(value); // used when no type info is needed
    }

    @Override
    public void serializeWithType(
        final Long value,
        final JsonGenerator gen,
        final SerializerProvider provider,
        final TypeSerializer typeSer
    ) throws IOException {
      // This is crucial for WRAPPER_ARRAY style!
      typeSer.writeTypePrefixForScalar(value, gen);
      gen.writeNumber(value);
      typeSer.writeTypeSuffixForScalar(value, gen);
    }
  }
}
