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
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthorizationEntity(
    Long authorizationKey,
    String ownerId,
    String ownerType,
    String resourceType,
    String resourceId,
    Set<PermissionType> permissionTypes) {

  public void foo() {
    AuthorizationCheck.of(b -> b.authorization().bar((authorization) -> "abc"));
  }
}
