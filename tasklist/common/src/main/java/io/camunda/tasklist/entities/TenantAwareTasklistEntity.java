/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.entities;

import static io.camunda.client.api.command.CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;

import java.util.Objects;
import java.util.StringJoiner;

public class TenantAwareTasklistEntity<T extends TasklistEntity<T>> extends TasklistEntity<T> {

  private String tenantId = DEFAULT_TENANT_IDENTIFIER;

  public String getTenantId() {
    return tenantId;
  }

  public T setTenantId(final String id) {
    tenantId = id;
    return (T) this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), tenantId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final TenantAwareTasklistEntity<?> that = (TenantAwareTasklistEntity<?>) o;
    return Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", TenantAwareTasklistEntity.class.getSimpleName() + "[", "]")
        .add("id='" + getId() + "'")
        .add("tenantId='" + tenantId + "'")
        .toString();
  }
}
