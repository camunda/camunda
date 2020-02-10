/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.el.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public final class MessagePackConverter {

  private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE =
      new TypeReference<>() {};

  private final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());

  public Map<String, Object> readMessagePack(final DirectBuffer messagePack) {
    try {
      return objectMapper.readValue(messagePack.byteArray(), MAP_TYPE_REFERENCE);

    } catch (final IOException e) {
      throw new RuntimeException("Failed to transform a MessagePack buffer into a Map", e);
    }
  }

  public DirectBuffer writeMessagePack(final Object value) {

    try {
      final var messagePack = objectMapper.writeValueAsBytes(value);
      return new UnsafeBuffer(messagePack);

    } catch (final JsonProcessingException e) {
      throw new RuntimeException("Failed to transform an object into MessagePack", e);
    }
  }
}
