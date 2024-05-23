/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.json;

import io.camunda.data.clients.json.jackson.WrappedJsonGenerator;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.jackson.JacksonJsonpGenerator;
import org.opensearch.client.opensearch.core.SearchRequest;

public class WrappedJsonMapper implements JsonpMapper {

  private final JsonpMapper mapper;

  public WrappedJsonMapper(final JsonpMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public JsonProvider jsonProvider() {
    return mapper.jsonProvider();
  }

  @Override
  public <T> T deserialize(JsonParser parser, Class<T> clazz) {
    return mapper.deserialize(parser, clazz);
  }

  @Override
  public <T> void serialize(T value, JsonGenerator generator) {
    if (SearchRequest.class.isAssignableFrom(value.getClass())) {
      final var wrappedGenerator = new WrappedJsonGenerator((JacksonJsonpGenerator) generator);
      mapper.serialize(value, wrappedGenerator);
    } else {
      mapper.serialize(value, generator);
    }
  }
}
