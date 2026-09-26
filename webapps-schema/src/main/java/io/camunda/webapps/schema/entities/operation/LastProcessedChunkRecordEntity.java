/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.operation;

import io.camunda.webapps.schema.entities.PartitionedEntity;
import java.util.Objects;

/**
 * High-water-mark marker for the last BATCH_OPERATION_CHUNK CREATED record key applied to {@link
 * BatchOperationEntity#getOperationsTotalCount()} from a given partition. One marker per partition
 * is kept - not one per record - because an exporter replays its own partition's log in ascending
 * position, so a record's key is only ever compared against its own partition's high-water mark to
 * detect a resend.
 */
public class LastProcessedChunkRecordEntity
    implements PartitionedEntity<LastProcessedChunkRecordEntity> {

  private int partitionId;
  private long recordKey;

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public LastProcessedChunkRecordEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public long getRecordKey() {
    return recordKey;
  }

  public LastProcessedChunkRecordEntity setRecordKey(final long recordKey) {
    this.recordKey = recordKey;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(partitionId, recordKey);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final LastProcessedChunkRecordEntity that = (LastProcessedChunkRecordEntity) o;
    return partitionId == that.partitionId && recordKey == that.recordKey;
  }

  @Override
  public String toString() {
    return "LastProcessedChunkRecordEntity{"
        + "partitionId="
        + partitionId
        + ", recordKey="
        + recordKey
        + '}';
  }
}
