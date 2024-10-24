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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String id = "id";
    public static final String groupId = "groupId";
    public static final String operationType = "operationType";
    public static final String timestamp = "timestamp";
  }
}
