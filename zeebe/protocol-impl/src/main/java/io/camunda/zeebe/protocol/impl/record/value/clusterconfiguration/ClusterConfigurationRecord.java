/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.clusterconfiguration;

import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ClusterConfigurationRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.concurrent.UnsafeBuffer;

public final class ClusterConfigurationRecord extends UnifiedRecordValue
    implements ClusterConfigurationRecordValue {

  private final LongProperty expectedPreviousVersionProp =
      new LongProperty("expectedPreviousVersion", -1L);
  private final StringProperty requestIdProp = new StringProperty("requestId", "");
  private final BinaryProperty configurationProp =
      new BinaryProperty("configuration", new UnsafeBuffer(new byte[0]));
  private final BinaryProperty appliedOperationProp =
      new BinaryProperty("appliedOperation", new UnsafeBuffer(new byte[0]));
  private final StringProperty rejectionReasonProp = new StringProperty("rejectionReason", "");

  public ClusterConfigurationRecord() {
    super(5);
    declareProperty(expectedPreviousVersionProp)
        .declareProperty(requestIdProp)
        .declareProperty(configurationProp)
        .declareProperty(appliedOperationProp)
        .declareProperty(rejectionReasonProp);
  }

  @Override
  public long getExpectedPreviousVersion() {
    return expectedPreviousVersionProp.getValue();
  }

  public ClusterConfigurationRecord setExpectedPreviousVersion(final long expectedPreviousVersion) {
    expectedPreviousVersionProp.setValue(expectedPreviousVersion);
    return this;
  }

  @Override
  public String getRequestId() {
    return BufferUtil.bufferAsString(requestIdProp.getValue());
  }

  public ClusterConfigurationRecord setRequestId(final String requestId) {
    requestIdProp.setValue(requestId);
    return this;
  }

  @Override
  public byte[] getConfiguration() {
    return BufferUtil.bufferAsArray(configurationProp.getValue());
  }

  public ClusterConfigurationRecord setConfiguration(final byte[] configuration) {
    configurationProp.setValue(new UnsafeBuffer(configuration));
    return this;
  }

  @Override
  public byte[] getAppliedOperation() {
    return BufferUtil.bufferAsArray(appliedOperationProp.getValue());
  }

  public ClusterConfigurationRecord setAppliedOperation(final byte[] appliedOperation) {
    appliedOperationProp.setValue(new UnsafeBuffer(appliedOperation));
    return this;
  }

  @Override
  public String getRejectionReason() {
    return BufferUtil.bufferAsString(rejectionReasonProp.getValue());
  }

  public ClusterConfigurationRecord setRejectionReason(final String rejectionReason) {
    rejectionReasonProp.setValue(rejectionReason);
    return this;
  }
}
