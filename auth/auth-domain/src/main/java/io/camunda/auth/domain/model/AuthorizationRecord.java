/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.model;

import java.util.Set;

/** A permission record granting specific permissions on a resource to an owner. */
public record AuthorizationRecord(
    long authorizationKey,
    String ownerId,
    String ownerType,
    String resourceType,
    String resourceId,
    Set<String> permissionTypes) {

  public AuthorizationRecord {
    permissionTypes = permissionTypes != null ? Set.copyOf(permissionTypes) : Set.of();
  }
}
