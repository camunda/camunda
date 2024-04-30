/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final OperateTenant other = (OperateTenant) obj;
    return Objects.equals(name, other.name) && Objects.equals(tenantId, other.tenantId);
  }
}
