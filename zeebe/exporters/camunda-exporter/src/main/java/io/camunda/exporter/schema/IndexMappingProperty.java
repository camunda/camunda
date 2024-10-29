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
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.stream.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;

public record IndexMappingProperty(String name, Object typeDefinition) {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final JsonpMapper JSONP_MAPPER = new JacksonJsonpMapper(MAPPER);
  private static final org.opensearch.client.json.JsonpMapper OPENSEARCH_JSONP_MAPPER =
      new org.opensearch.client.json.jackson.JacksonJsonpMapper(MAPPER);

  public Entry<String, Property> toElasticsearchProperty() {
    final var typeDefinitionParser = getTypeDefinitionParser();
    final var elasticsearchProperty =
        Property._DESERIALIZER.deserialize(typeDefinitionParser, JSONP_MAPPER);

    return entry(name(), elasticsearchProperty);
  }

  public Entry<String, org.opensearch.client.opensearch._types.mapping.Property>
      toOpensearchProperty() {
    final var typeDefinitionParser = getTypeDefinitionParser();
    final var opensearchProperty =
        org.opensearch.client.opensearch._types.mapping.Property._DESERIALIZER.deserialize(
            typeDefinitionParser, OPENSEARCH_JSONP_MAPPER);

    return entry(name(), opensearchProperty);
  }

  private JsonParser getTypeDefinitionParser() {
    try {
      final var typeDefinitionJson =
          IOUtils.toInputStream(
              MAPPER.writeValueAsString(typeDefinition()), StandardCharsets.UTF_8);

      return JSONP_MAPPER.jsonProvider().createParser(typeDefinitionJson);
    } catch (final IOException e) {
      throw new IllegalStateException(
          String.format(
              "Failed to deserialize IndexMappingProperty [%s] to [%s]",
              this, Property.class.getName()),
          e);
    }
  }

  @Override
  public String toString() {
    final var typeDefinitionStr =
        ((Map<String, Object>) typeDefinition)
            .entrySet().stream()
                .collect(
                    Collectors.toMap(
                        Entry::getKey,
                        (ent) ->
                            ent.getValue()
                                + "["
                                + ent.getValue().getClass().getSimpleName()
                                + "]"));
    return "IndexMappingProperty{"
        + "name='"
        + name
        + '\''
        + ", typeDefinition="
        + typeDefinitionStr
        + '}';
  }

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
