/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.persistence;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.io.Serializable;
import java.time.OffsetDateTime;

public class AssigneeOperationDto implements OptimizeDto, Serializable {

  private String id;

  private String userId;
  private String operationType;
  private OffsetDateTime timestamp;

  public AssigneeOperationDto(
      final String id,
      final String userId,
      final String operationType,
      final OffsetDateTime timestamp) {
    this.id = id;
    this.userId = userId;
    this.operationType = operationType;
    this.timestamp = timestamp;
  }

  public AssigneeOperationDto() {}

  public String getId() {
    return this.id;
  }

  public String getUserId() {
    return this.userId;
  }

  public String getOperationType() {
    return this.operationType;
  }

  public OffsetDateTime getTimestamp() {
    return this.timestamp;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public void setUserId(final String userId) {
    this.userId = userId;
  }

  public void setOperationType(final String operationType) {
    this.operationType = operationType;
  }

  public void setTimestamp(final OffsetDateTime timestamp) {
    this.timestamp = timestamp;
  }

  public String toString() {
    return "AssigneeOperationDto(id="
        + this.getId()
        + ", userId="
        + this.getUserId()
        + ", operationType="
        + this.getOperationType()
        + ", timestamp="
        + this.getTimestamp()
        + ")";
  }

  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  protected boolean canEqual(final Object other) {
    return other instanceof AssigneeOperationDto;
  }

  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String id = "id";
    public static final String userId = "userId";
    public static final String operationType = "operationType";
    public static final String timestamp = "timestamp";
  }
}
