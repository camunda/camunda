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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "BusinessKeyDto(processInstanceId="
        + getProcessInstanceId()
        + ", businessKey="
        + getBusinessKey()
        + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String processInstanceId = "processInstanceId";
    public static final String businessKey = "businessKey";
  }
}
