/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public final class SchemaResourceSerializer {

  private final ObjectMapper objectMapper;

  public SchemaResourceSerializer(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> serialize(
      final Function<JsonGenerator, jakarta.json.stream.JsonGenerator> jacksonGenerator,
      final Consumer<jakarta.json.stream.JsonGenerator> serialize)
      throws IOException {
    try (final var out = new StringWriter();
        final var jsonGenerator = new JsonFactory().createGenerator(out);
        final jakarta.json.stream.JsonGenerator jacksonJsonpGenerator =
            jacksonGenerator.apply(jsonGenerator)) {
      serialize.accept(jacksonJsonpGenerator);
      jacksonJsonpGenerator.flush();

      return objectMapper.readValue(out.toString(), new TypeReference<>() {});
    }
  }
}
