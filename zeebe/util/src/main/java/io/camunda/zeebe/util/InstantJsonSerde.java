/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.time.Instant;

/**
 * Utility Jackson module to perform serialization and deserialization of {@link Instant} in long
 * representation without having to enable/disable additional Jackson features. If using the
 * existing {@link JavaTimeModule} from Jackson, make sure to register this module after the
 * JavaTimeModule registration to override the provided Instant serializer/deserializer.
 */
public final class InstantJsonSerde {

  public static final SimpleModule INSTANT_TO_LONG_SERDE_MODULE =
      new SimpleModule()
          .addSerializer(Instant.class, new InstantJsonSerializer())
          .addDeserializer(Instant.class, new InstantJsonDeserializer());

  public static class InstantJsonSerializer extends JsonSerializer<Instant> {
    @Override
    public void serialize(
        final Instant value, final JsonGenerator gen, final SerializerProvider serializers)
        throws IOException {
      gen.writeNumber(value.equals(Instant.MIN) ? -1L : value.toEpochMilli());
    }
  }

  public static class InstantJsonDeserializer extends JsonDeserializer<Instant> {
    @Override
    public Instant deserialize(final JsonParser p, final DeserializationContext ctxt)
        throws IOException {
      final var value = p.getLongValue();
      return value == -1L ? Instant.MIN : Instant.ofEpochMilli(value);
    }
  }
}
