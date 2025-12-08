/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities;

import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import java.util.Objects;

public class HistoryDeletionEntity implements ExporterEntity<HistoryDeletionEntity> {

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String id;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private long resourceKey;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private HistoryDeletionType resourceType;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private long batchOperationKey;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private long partitionId;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public HistoryDeletionEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getResourceKey() {
    return resourceKey;
  }

  public HistoryDeletionEntity setResourceKey(final long resourceKey) {
    this.resourceKey = resourceKey;
    return this;
  }

  public HistoryDeletionType getResourceType() {
    return resourceType;
  }

  public HistoryDeletionEntity setResourceType(final HistoryDeletionType resourceType) {
    this.resourceType = resourceType;
    return this;
  }

  public long getBatchOperationKey() {
    return batchOperationKey;
  }

  public HistoryDeletionEntity setBatchOperationKey(final long batchOperationKey) {
    this.batchOperationKey = batchOperationKey;
    return this;
  }

  public long getPartitionId() {
    return partitionId;
  }

  public HistoryDeletionEntity setPartitionId(final long partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, resourceKey, resourceType, batchOperationKey, partitionId);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final HistoryDeletionEntity that = (HistoryDeletionEntity) o;
    return resourceKey == that.resourceKey
        && batchOperationKey == that.batchOperationKey
        && Objects.equals(id, that.id)
        && Objects.equals(resourceType, that.resourceType)
        && partitionId == that.partitionId;
  }

  @Override
  public String toString() {
    return "HistoryDeletionEntity{"
        + "id='"
        + id
        + '\''
        + ", resourceKey="
        + resourceKey
        + ", resourceType='"
        + resourceType
        + '\''
        + ", batchOperationKey="
        + batchOperationKey
        + ", partitionId="
        + partitionId
        + '}';
  }
}
