/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.analytics.dataset.RegisteredDataset;

/**
 * Serializes a {@link RegisteredDataset} to/from the JSON stored in {@code ANALYTICS_DATASET_SPEC.
 * SPEC}. The declaration, activation vector, and ids are Jackson records/enums/maps, so a plain
 * {@link ObjectMapper} round-trips them. This is the RDBMS backend's transform of the neutral spec
 * (a document backend serializes the same model through its own client mapper).
 */
final class SpecJson {

  private final ObjectMapper objectMapper = new ObjectMapper();

  String toJson(final RegisteredDataset spec) {
    try {
      return objectMapper.writeValueAsString(spec);
    } catch (final Exception e) {
      throw new IllegalStateException("failed to serialize dataset spec " + spec.cubeId(), e);
    }
  }

  RegisteredDataset fromJson(final String json) {
    try {
      return objectMapper.readValue(json, RegisteredDataset.class);
    } catch (final Exception e) {
      throw new IllegalStateException("failed to deserialize dataset spec", e);
    }
  }
}
