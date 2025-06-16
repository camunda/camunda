/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;

/**
 * Metadata for customizing the writing of follow-up event.
 *
 * <p>This metadata allows enriching follow-up events with additional information such as {@code
 * operationReference} (to correlate follow-up records with prior client operations) and {@code
 * recordVersion} (to specify a version of the event record to use when writing and applying the
 * event).
 */
public final class FollowUpEventMetadata {

  public static final int VERSION_NOT_SET = -1;

  private final long operationReference;
  private final int recordVersion;

  private FollowUpEventMetadata(Builder builder) {
    this.operationReference = builder.operationReference;
    this.recordVersion = builder.recordVersion;
  }

  public long getOperationReference() {
    return operationReference;
  }

  public int getRecordVersion() {
    return recordVersion;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private long operationReference = RecordMetadataDecoder.operationReferenceNullValue();
    private int recordVersion = VERSION_NOT_SET;

    public Builder operationReference(final long operationReference) {
      this.operationReference = operationReference;
      return this;
    }

    public Builder recordVersion(final int recordVersion) {
      this.recordVersion = recordVersion;
      return this;
    }

    public FollowUpEventMetadata build() {
      return new FollowUpEventMetadata(this);
    }
  }
}
