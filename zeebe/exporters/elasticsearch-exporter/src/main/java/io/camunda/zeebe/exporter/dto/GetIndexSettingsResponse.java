/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GetIndexSettingsResponse {
  private final Map<String, Index> indices = new HashMap<>();

  @JsonAnySetter
  public void setIndice(final String indexName, final Index index) {
    indices.put(indexName, index);
  }

  public Map<String, Index> getIndices() {
    return indices;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Index(Settings settings) {}
}
