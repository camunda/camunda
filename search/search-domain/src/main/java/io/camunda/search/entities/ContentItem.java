/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A single content block within a history item or an agent instance's system prompt. The {@code
 * text}, {@code documentReference}, and {@code object} fields are mutually exclusive based on
 * {@code contentType}. Shared between {@link AgentInstanceHistoryEntity} and {@link
 * AgentInstanceEntity}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContentItem(
    ContentType contentType,
    @Nullable String text,
    @Nullable DocumentReference documentReference,
    @Nullable Object object) {

  public ContentItem {
    Objects.requireNonNull(contentType, "contentType");
  }

  public enum ContentType {
    TEXT,
    DOCUMENT,
    OBJECT
  }
}
