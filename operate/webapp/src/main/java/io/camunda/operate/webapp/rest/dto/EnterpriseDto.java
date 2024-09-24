/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto;

import java.util.Objects;

public class EnterpriseDto {

  private final boolean enterprise;

  public EnterpriseDto(final boolean enterprise) {
    this.enterprise = enterprise;
  }

  public boolean isEnterprise() {
    return enterprise;
  }

  @Override
  public int hashCode() {
    return Objects.hash(enterprise);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EnterpriseDto that = (EnterpriseDto) o;
    return enterprise == that.enterprise;
  }

  @Override
  public String toString() {
    return "EnterpriseDto{" + "enterprise=" + enterprise + '}';
  }
}
