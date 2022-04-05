/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto;

import java.util.Objects;

public class EnterpriseDto {

  private final boolean enterprise;

  public EnterpriseDto(boolean enterprise) {
    this.enterprise = enterprise;
  }

  public boolean isEnterprise() {
    return enterprise;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EnterpriseDto that = (EnterpriseDto) o;
    return enterprise == that.enterprise;
  }

  @Override
  public int hashCode() {
    return Objects.hash(enterprise);
  }

  @Override
  public String toString() {
    return "EnterpriseDto{" +
        "enterprise=" + enterprise +
        '}';
  }
}
