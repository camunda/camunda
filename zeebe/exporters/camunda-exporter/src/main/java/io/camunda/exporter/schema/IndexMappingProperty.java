/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import static java.util.Map.entry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;

public record IndexMappingProperty(String name, Object typeDefinition) {

  public static <T> Map<String, T> toPropertiesMap(
      final Collection<IndexMappingProperty> properties,
      final ObjectMapper mapper,
      final Function<InputStream, T> typeDeserializer) {

    return properties.stream()
        .map(
            prop -> {
              try {
                final var typeJson =
                    IOUtils.toInputStream(
                        mapper.writeValueAsString(prop.typeDefinition()), StandardCharsets.UTF_8);
                final var type = typeDeserializer.apply(typeJson);
                return entry(prop.name(), type);
              } catch (final JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            })
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
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
