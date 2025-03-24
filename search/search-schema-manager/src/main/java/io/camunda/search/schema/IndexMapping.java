/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record IndexMapping(
    String indexName,
    String dynamic,
    Set<IndexMappingProperty> properties,
    Map<String, Object> metaProperties) {

  public Map<String, Object> toMap() {
    return properties.stream()
        .collect(
            Collectors.toMap(IndexMappingProperty::name, IndexMappingProperty::typeDefinition));
  }

  public boolean isDynamic() {
    return Boolean.parseBoolean(dynamic);
  }

  @SuppressWarnings("unchecked")
  public static IndexMapping from(
      final IndexDescriptor indexDescriptor, final ObjectMapper mapper) {
    try (final var mappingsStream =
        SchemaManager.class.getResourceAsStream(indexDescriptor.getMappingsClasspathFilename())) {
      final var nestedType = new TypeReference<Map<String, Map<String, Object>>>() {};
      final Map<String, Object> mappings =
          mapper.readValue(mappingsStream, nestedType).get("mappings");
      final Map<String, Object> properties = (Map<String, Object>) mappings.get("properties");
      final var dynamic = mappings.get("dynamic");

      return new IndexMapping.Builder()
          .dynamic(dynamic == null ? "strict" : dynamic.toString())
          .properties(
              properties.entrySet().stream()
                  .map(IndexMappingProperty::createIndexMappingProperty)
                  .collect(Collectors.toSet()))
          .build();
    } catch (final IOException e) {
      throw new IllegalArgumentException(
          String.format(
              "Failed to parse index json [%s]", indexDescriptor.getMappingsClasspathFilename()),
          e);
    }
  }

  public static class Builder {
    private Set<IndexMappingProperty> properties;
    private Map<String, Object> metaProperties;
    private String indexName;
    private String dynamic;

    public Builder indexName(final String indexName) {
      this.indexName = indexName;
      return this;
    }

    public Builder dynamic(final String dynamic) {
      this.dynamic = dynamic;
      return this;
    }

    public Builder properties(final Set<IndexMappingProperty> properties) {
      this.properties = properties;
      return this;
    }

    public Builder metaProperties(final Map<String, Object> metaProperties) {
      this.metaProperties = metaProperties;
      return this;
    }

    public IndexMapping build() {
      return new IndexMapping(indexName, dynamic, properties, metaProperties);
    }
  }
}
