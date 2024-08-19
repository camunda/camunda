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
    return id;
  }

  public AssigneeOperationDto setId(final String id) {
    this.id = id;
    return this;
  }

  public String getUserId() {
    return userId;
  }

  public AssigneeOperationDto setUserId(final String userId) {
    this.userId = userId;
    return this;
  }

  public String getOperationType() {
    return operationType;
  }

  public AssigneeOperationDto setOperationType(final String operationType) {
    this.operationType = operationType;
    return this;
  }

  public OffsetDateTime getTimestamp() {
    return timestamp;
  }

  public AssigneeOperationDto setTimestamp(final OffsetDateTime timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof AssigneeOperationDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AssigneeOperationDto)) {
      return false;
    }
    final AssigneeOperationDto other = (AssigneeOperationDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "AssigneeOperationDto(id="
        + getId()
        + ", userId="
        + getUserId()
        + ", operationType="
        + getOperationType()
        + ", timestamp="
        + getTimestamp()
        + ")";
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String userId = "userId";
    public static final String operationType = "operationType";
    public static final String timestamp = "timestamp";
  }
}
