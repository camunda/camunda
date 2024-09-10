/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;

public record IndexMappingProperty(String name, Object typeDefinition) {

  public static InputStream toPropertiesJson(
      final Set<IndexMappingProperty> properties, final ObjectMapper mapper) {
    final var propertiesAsMap =
        properties.stream()
            .collect(
                Collectors.toMap(IndexMappingProperty::name, IndexMappingProperty::typeDefinition));
    final var propertiesBlock = new HashMap<>();
    propertiesBlock.put("properties", propertiesAsMap);
    try {
      return IOUtils.toInputStream(
          mapper.writeValueAsString(propertiesBlock), StandardCharsets.UTF_8);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
