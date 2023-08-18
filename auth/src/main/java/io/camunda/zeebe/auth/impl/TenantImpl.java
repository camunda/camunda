/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.auth.impl;

import io.camunda.zeebe.auth.api.Tenant;
import java.util.Objects;

public class TenantImpl implements Tenant {

  private String id;

  public TenantImpl(final String id) {
    this.id = id;
  }

  @Override
  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public boolean equals(final Object obj) {
    if (!(obj instanceof Tenant)) {
      return false;
    }

    final Tenant that = (Tenant) obj;
    return id.equals(that.getId());
  }
}
