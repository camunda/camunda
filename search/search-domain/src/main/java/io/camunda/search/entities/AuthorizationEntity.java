/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.HashSet;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthorizationEntity(
    Long authorizationKey,
    String ownerId,
    String ownerType,
    String resourceType,
    Short resourceMatcher,
    String resourceId,
    String resourcePropertyName,
    Set<PermissionType> permissionTypes) {

  public AuthorizationEntity {
    // Mutable collections are required: MyBatis hydrates collection-mapped fields (e.g. from a
    // <collection> result map or a LEFT JOIN) by calling .add() on the existing instance.
    // Immutable defaults (e.g. Set.of()) would cause UnsupportedOperationException at runtime.
    // Note: the API always requires at least one permission type, so null is not expected in
    // practice — this guard exists purely as a defensive measure.
    permissionTypes = permissionTypes != null ? permissionTypes : new HashSet<>();
  }
}
