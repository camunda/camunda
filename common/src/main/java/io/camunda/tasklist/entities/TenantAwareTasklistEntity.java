/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.entities;

import static io.camunda.zeebe.client.api.command.CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;

import java.util.Objects;
import java.util.StringJoiner;

public class TenantAwareTasklistEntity<T extends TasklistEntity<T>> extends TasklistEntity<T> {

  private String tenantId = DEFAULT_TENANT_IDENTIFIER;

  public String getTenantId() {
    return tenantId;
  }

  public T setTenantId(String id) {
    this.tenantId = id;
    return (T) this;
  }

  @Override
  public boolean equals(Object o) {
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
  public int hashCode() {
    return Objects.hash(super.hashCode(), tenantId);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", TenantAwareTasklistEntity.class.getSimpleName() + "[", "]")
        .add("id='" + getId() + "'")
        .add("tenantId='" + tenantId + "'")
        .toString();
  }
}
