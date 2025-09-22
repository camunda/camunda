/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.processor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;

public class CamundaLegacyPropertiesMappingLoader {

  public static List<CamundaLegacyPropertiesMapping> load() {
    try {
      return new ObjectMapper()
          .readValue(
              CamundaLegacyPropertiesMappingLoader.class
                  .getClassLoader()
                  .getResource("legacy-property-mappings.json"),
              new TypeReference<>() {});
    } catch (final IOException e) {
      throw new RuntimeException("Error while reading legacy property mappings", e);
    }
  }
}
