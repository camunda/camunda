/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities;

import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Objects;

public class WaitingStateEntity
    implements ExporterEntity<WaitingStateEntity>,
        PartitionedEntity<WaitingStateEntity>,
        TenantOwned {

  private String id;
  private long elementInstanceKey;
  private long processInstanceKey;
  private String elementType;
  private String details;
  private String tenantId;
  private int partitionId;
  private Long position;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public WaitingStateEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public WaitingStateEntity setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public WaitingStateEntity setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public String getElementType() {
    return elementType;
  }

  public WaitingStateEntity setElementType(final String elementType) {
    this.elementType = elementType;
    return this;
  }

  public String getDetails() {
    return details;
  }

  public WaitingStateEntity setDetails(final String details) {
    this.details = details;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public WaitingStateEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public WaitingStateEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public WaitingStateEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        elementInstanceKey,
        processInstanceKey,
        elementType,
        details,
        tenantId,
        partitionId,
        position);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final WaitingStateEntity that = (WaitingStateEntity) o;
    return elementInstanceKey == that.elementInstanceKey
        && processInstanceKey == that.processInstanceKey
        && partitionId == that.partitionId
        && Objects.equals(id, that.id)
        && Objects.equals(elementType, that.elementType)
        && Objects.equals(details, that.details)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(position, that.position);
  }

  @Override
  public String toString() {
    return "WaitingStateEntity{"
        + "id='"
        + id
        + '\''
        + ", elementInstanceKey="
        + elementInstanceKey
        + ", processInstanceKey="
        + processInstanceKey
        + ", elementType='"
        + elementType
        + '\''
        + ", details='"
        + details
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + ", partitionId="
        + partitionId
        + ", position="
        + position
        + '}';
  }
}
