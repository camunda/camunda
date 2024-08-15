/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.persistence;

import io.camunda.optimize.dto.optimize.OptimizeDto;

public class BusinessKeyDto implements OptimizeDto {

  private String processInstanceId;
  private String businessKey;

  public BusinessKeyDto(final String processInstanceId, final String businessKey) {
    this.processInstanceId = processInstanceId;
    this.businessKey = businessKey;
  }

  public BusinessKeyDto() {}

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public String getBusinessKey() {
    return businessKey;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof BusinessKeyDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $processInstanceId = getProcessInstanceId();
    result = result * PRIME + ($processInstanceId == null ? 43 : $processInstanceId.hashCode());
    final Object $businessKey = getBusinessKey();
    result = result * PRIME + ($businessKey == null ? 43 : $businessKey.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof BusinessKeyDto)) {
      return false;
    }
    final BusinessKeyDto other = (BusinessKeyDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$processInstanceId = getProcessInstanceId();
    final Object other$processInstanceId = other.getProcessInstanceId();
    if (this$processInstanceId == null
        ? other$processInstanceId != null
        : !this$processInstanceId.equals(other$processInstanceId)) {
      return false;
    }
    final Object this$businessKey = getBusinessKey();
    final Object other$businessKey = other.getBusinessKey();
    if (this$businessKey == null
        ? other$businessKey != null
        : !this$businessKey.equals(other$businessKey)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "BusinessKeyDto(processInstanceId="
        + getProcessInstanceId()
        + ", businessKey="
        + getBusinessKey()
        + ")";
  }

  public static final class Fields {

    public static final String processInstanceId = "processInstanceId";
    public static final String businessKey = "businessKey";
  }
}
