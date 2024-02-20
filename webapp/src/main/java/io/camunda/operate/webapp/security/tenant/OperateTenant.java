/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.tenant;

import java.io.Serializable;
import java.util.Objects;

public class OperateTenant implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String tenantId;
  private final String name;

  public OperateTenant(String tenantId, String name) {
    this.tenantId = tenantId;
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public String getTenantId() {
    return tenantId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, tenantId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    OperateTenant other = (OperateTenant) obj;
    return Objects.equals(name, other.name) && Objects.equals(tenantId, other.tenantId);
  }
}
