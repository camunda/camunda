/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class IndexMapping {

  private String indexName;

  private String dynamic;

  private Set<IndexMappingProperty> properties;

  private Map<String, Object> metaProperties;

  public String getIndexName() {
    return indexName;
  }

  public IndexMapping setIndexName(final String indexName) {
    this.indexName = indexName;
    return this;
  }

  public String getDynamic() {
    return dynamic;
  }

  public IndexMapping setDynamic(final String dynamic) {
    // Opensearch changes the capitalization of this field on some query results, change to
    // lowercase for consistency
    this.dynamic = dynamic == null ? null : dynamic.toLowerCase();
    return this;
  }

  public Set<IndexMappingProperty> getProperties() {
    return properties;
  }

  public IndexMapping setProperties(final Set<IndexMappingProperty> properties) {
    this.properties = properties;
    return this;
  }

  public Map<String, Object> toMap() {
    return properties.stream()
        .collect(
            Collectors.toMap(
                IndexMappingProperty::getName,
                property -> convertToOrderedStructures(property.getTypeDefinition())));
  }

  /**
   * On Opensearch Schema validation fails when the schema is created by the Exporter (primarility
   * for tasklist-task index and the `join` field), due to the order of parsed index mapping
   * properties. Using TreeMap and sorting the Lists present in the properties ensures the order of
   * properties when comparing the existing mappings with the ones defined in the schema.
   */
  private Object convertToOrderedStructures(final Object value) {
    if (value instanceof Map<?, ?>) {
      return ((Map<?, ?>) value)
          .entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Entry::getKey,
                      item -> {
                        if (item.getValue() instanceof Map<?, ?>) {
                          return convertToOrderedStructures(item.getValue());
                        } else if (item.getValue() instanceof List<?>) {
                          return ((ArrayList<?>) item.getValue())
                              .stream().sorted().collect(Collectors.toList());
                          // Elasticsearch parses true/false as boolean,
                          // Opensearch parses them as strings.
                          // The diff we do on the validation expects them to be Strings.
                        } else if (item.getValue() instanceof Boolean) {
                          return String.valueOf(item.getValue());
                        } else {
                          return item.getValue();
                        }
                      },
                      (e1, e2) -> e1,
                      TreeMap::new));
    }
    return value;
  }

  public Map<String, Object> getMetaProperties() {
    return metaProperties;
  }

  public IndexMapping setMetaProperties(final Map<String, Object> metaProperties) {
    this.metaProperties = metaProperties;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(indexName, dynamic, properties, metaProperties);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IndexMapping that = (IndexMapping) o;
    return Objects.equals(indexName, that.indexName)
        && Objects.equals(properties, that.properties)
        && Objects.equals(dynamic, that.dynamic)
        && Objects.equals(metaProperties, that.metaProperties);
  }

  @Override
  public String toString() {
    return "IndexMapping{"
        + "indexName='"
        + indexName
        + '\''
        + ", dynamic='"
        + dynamic
        + '\''
        + ", properties="
        + properties
        + ", metaProperties="
        + metaProperties
        + '}';
  }

  public static class IndexMappingProperty {

    private String name;

    private Object typeDefinition;

    public String getName() {
      return name;
    }

    public IndexMappingProperty setName(final String name) {
      this.name = name;
      return this;
    }

    public Object getTypeDefinition() {
      return typeDefinition;
    }

    public IndexMappingProperty setTypeDefinition(final Object typeDefinition) {
      this.typeDefinition = typeDefinition;
      return this;
    }

    public static String toJsonString(
        final Set<IndexMappingProperty> properties, final ObjectMapper objectMapper) {
      try {
        final Map<String, Object> fields =
            properties.stream()
                .collect(Collectors.toMap(p -> p.getName(), p -> p.getTypeDefinition()));
        return objectMapper.writeValueAsString(fields);
      } catch (final JsonProcessingException e) {
        throw new TasklistRuntimeException(e);
      }
    }

    public static IndexMappingProperty createIndexMappingProperty(
        final Entry<String, Object> propertiesMapEntry) {
      return new IndexMappingProperty()
          .setName(propertiesMapEntry.getKey())
          .setTypeDefinition(propertiesMapEntry.getValue());
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, typeDefinition);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final IndexMappingProperty that = (IndexMappingProperty) o;
      return Objects.equals(name, that.name) && Objects.equals(typeDefinition, that.typeDefinition);
    }

    @Override
    public String toString() {
      return "IndexMappingProperty{"
          + "name='"
          + name
          + '\''
          + ", typeDefinition="
          + typeDefinition
          + '}';
    }
  }
}
