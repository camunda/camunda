/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GlobalListenerEntity(
    String id,
    String listenerId,
    String type,
    List<String> eventTypes,
    Integer retries,
    Boolean afterNonGlobal,
    Integer priority,
    GlobalListenerSource source,
    @Nullable GlobalListenerType listenerType) {
  public GlobalListenerEntity {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(listenerId, "listenerId");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(retries, "retries");
    Objects.requireNonNull(afterNonGlobal, "afterNonGlobal");
    Objects.requireNonNull(priority, "priority");
    Objects.requireNonNull(source, "source");
    // Mutable collections are required: MyBatis hydrates collection-mapped fields (e.g. from a
    // <collection> result map or a LEFT JOIN) by calling .add() on the existing instance.
    // Immutable defaults (e.g. List.of()) would cause UnsupportedOperationException at runtime.
    eventTypes = eventTypes != null ? eventTypes : new ArrayList<>();
  }
}
