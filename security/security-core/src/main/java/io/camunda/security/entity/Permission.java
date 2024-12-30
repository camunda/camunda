/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.entity;

import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Objects;
import java.util.Set;

public final class Permission {
  private final PermissionType type;
  private final Set<String> resourceIds;

  public Permission(final PermissionType type, final Set<String> resourceIds) {
    this.type = type;
    this.resourceIds = resourceIds;
  }

  public PermissionType type() {
    return type;
  }

  public Set<String> resourceIds() {
    return resourceIds;
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, resourceIds);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    final Permission that = (Permission) obj;
    return Objects.equals(type, that.type) && Objects.equals(resourceIds, that.resourceIds);
  }

  @Override
  public String toString() {
    return "Permission[" + "type=" + type + ", " + "resourceIds=" + resourceIds + ']';
  }
}
