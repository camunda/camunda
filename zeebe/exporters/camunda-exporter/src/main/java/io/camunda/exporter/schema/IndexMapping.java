/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

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
