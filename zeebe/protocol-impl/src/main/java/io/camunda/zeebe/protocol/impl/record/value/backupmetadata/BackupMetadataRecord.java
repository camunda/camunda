/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.backupmetadata;

import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.BackupMetadataRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.concurrent.UnsafeBuffer;

public final class BackupMetadataRecord extends UnifiedRecordValue
    implements BackupMetadataRecordValue {

  private final LongProperty checkpointIdProp = new LongProperty("checkpointId", -1L);
  private final IntegerProperty partitionIdProp = new IntegerProperty("partitionId", -1);
  private final StringProperty statusProp = new StringProperty("status", "");
  private final BinaryProperty descriptorProp =
      new BinaryProperty("descriptor", new UnsafeBuffer(new byte[0]));
  private final StringProperty failureReasonProp = new StringProperty("failureReason", "");

  public BackupMetadataRecord() {
    super(5);
    declareProperty(checkpointIdProp)
        .declareProperty(partitionIdProp)
        .declareProperty(statusProp)
        .declareProperty(descriptorProp)
        .declareProperty(failureReasonProp);
  }

  @Override
  public long getCheckpointId() {
    return checkpointIdProp.getValue();
  }

  public BackupMetadataRecord setCheckpointId(final long checkpointId) {
    checkpointIdProp.setValue(checkpointId);
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionIdProp.getValue();
  }

  public BackupMetadataRecord setPartitionId(final int partitionId) {
    partitionIdProp.setValue(partitionId);
    return this;
  }

  @Override
  public String getStatus() {
    return BufferUtil.bufferAsString(statusProp.getValue());
  }

  public BackupMetadataRecord setStatus(final String status) {
    statusProp.setValue(status);
    return this;
  }

  @Override
  public byte[] getDescriptor() {
    return BufferUtil.bufferAsArray(descriptorProp.getValue());
  }

  public BackupMetadataRecord setDescriptor(final byte[] descriptor) {
    descriptorProp.setValue(new UnsafeBuffer(descriptor));
    return this;
  }

  @Override
  public String getFailureReason() {
    return BufferUtil.bufferAsString(failureReasonProp.getValue());
  }

  public BackupMetadataRecord setFailureReason(final String failureReason) {
    failureReasonProp.setValue(failureReason);
    return this;
  }
}
