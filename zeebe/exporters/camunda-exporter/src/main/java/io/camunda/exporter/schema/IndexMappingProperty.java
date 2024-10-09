/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import static java.util.Map.entry;

import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.stream.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;
import org.apache.commons.io.IOUtils;

public record IndexMappingProperty(String name, Object typeDefinition) {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final JsonpMapper JSONP_MAPPER = new JacksonJsonpMapper(MAPPER);

  public Entry<String, Property> toElasticsearchProperty() {
    try {
      final var typeDefinitionParser = getTypeDefinitionParser();
      final var elasticsearchProperty =
          Property._DESERIALIZER.deserialize(typeDefinitionParser, JSONP_MAPPER);

      return entry(name(), elasticsearchProperty);
    } catch (final IOException e) {
      throw new IllegalStateException(
          String.format(
              "Failed to deserialize IndexMappingProperty [%s] to [%s]",
              this, Property.class.getName()),
          e);
    }
  }

  private JsonParser getTypeDefinitionParser() throws JsonProcessingException {
    final var typeDefinitionJson =
        IOUtils.toInputStream(MAPPER.writeValueAsString(typeDefinition()), StandardCharsets.UTF_8);

    return JSONP_MAPPER.jsonProvider().createParser(typeDefinitionJson);
  }

  //  T = OP.Property, or
  //  T = ELS.Property

  public static IndexMappingProperty createIndexMappingProperty(
      final Entry<String, Object> propertiesMapEntry) {
    return new IndexMappingProperty(propertiesMapEntry.getKey(), propertiesMapEntry.getValue());
  }

  public static class Builder {
    private String name;
    private Object typeDefinition;

    public Builder name(final String name) {
      this.name = name;
      return this;
    }

    public Builder typeDefinition(final Object typeDefinition) {
      this.typeDefinition = typeDefinition;
      return this;
    }

    public IndexMappingProperty build() {
      return new IndexMappingProperty(name, typeDefinition);
    }
  }
}
