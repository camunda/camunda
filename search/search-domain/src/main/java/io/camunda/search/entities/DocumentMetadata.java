/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Metadata for a {@link DocumentReference}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentMetadata(
    String contentType,
    String fileName,
    @Nullable OffsetDateTime expiresAt,
    Long size,
    @Nullable String processDefinitionId,
    @Nullable Long processInstanceKey,
    Map<String, Object> customProperties) {

  public DocumentMetadata {
    Objects.requireNonNull(contentType, "contentType");
    Objects.requireNonNull(fileName, "fileName");
    Objects.requireNonNull(size, "size");
    customProperties = customProperties != null ? new HashMap<>(customProperties) : new HashMap<>();
  }
}
