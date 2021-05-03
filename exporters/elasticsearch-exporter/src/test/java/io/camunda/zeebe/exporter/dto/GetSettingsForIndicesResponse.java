/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class GetSettingsForIndicesResponse {
  @JsonUnwrapped private Map<String, IndexSettings> indices;

  public GetSettingsForIndicesResponse() {
    indices = new HashMap<>();
  }

  @JsonAnySetter
  public void put(final String index, final SettingsWrapper settingsWrapper) {
    indices.put(index, settingsWrapper.getIndex());
  }

  public Map<String, IndexSettings> getIndices() {
    return indices;
  }

  @SuppressWarnings("FieldCanBeLocal")
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class IndexSettings {
    @JsonProperty("number_of_shards")
    private int numberOfShards = -1;

    @JsonProperty("number_of_replicas")
    private int numberOfReplicas = -1;

    public int getNumberOfShards() {
      return numberOfShards;
    }

    public int getNumberOfReplicas() {
      return numberOfReplicas;
    }
  }

  private static final class SettingsWrapper {
    @JsonProperty("settings")
    private IndexSettingsWrapper wrapper;

    public IndexSettings getIndex() {
      return wrapper.indexSettings;
    }

    private static final class IndexSettingsWrapper {
      @JsonProperty("index")
      private IndexSettings indexSettings;
    }
  }
}
