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

public class CandidateGroupOperationDto implements OptimizeDto, Serializable {

  private String id;
  private String groupId;
  private String operationType;
  private OffsetDateTime timestamp;

  public CandidateGroupOperationDto(
      final String id,
      final String groupId,
      final String operationType,
      final OffsetDateTime timestamp) {
    this.id = id;
    this.groupId = groupId;
    this.operationType = operationType;
    this.timestamp = timestamp;
  }

  public CandidateGroupOperationDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(final String groupId) {
    this.groupId = groupId;
  }

  public String getOperationType() {
    return operationType;
  }

  public void setOperationType(final String operationType) {
    this.operationType = operationType;
  }

  public OffsetDateTime getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final OffsetDateTime timestamp) {
    this.timestamp = timestamp;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CandidateGroupOperationDto;
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
    if (!(o instanceof CandidateGroupOperationDto)) {
      return false;
    }
    final CandidateGroupOperationDto other = (CandidateGroupOperationDto) o;
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
    return "CandidateGroupOperationDto(id="
        + getId()
        + ", groupId="
        + getGroupId()
        + ", operationType="
        + getOperationType()
        + ", timestamp="
        + getTimestamp()
        + ")";
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String groupId = "groupId";
    public static final String operationType = "operationType";
    public static final String timestamp = "timestamp";
  }
}
