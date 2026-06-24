/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Metadata for customizing the writing of follow-up event.
 *
 * <p>This metadata allows enriching follow-up events with additional information such as {@code
 * operationReference} (to correlate follow-up records with prior client operations) and {@code
 * recordVersion} (to specify a version of the event record to use when writing and applying the
 * event).
 */
public record FollowUpEventMetadata(
    long operationReference,
    long batchOperationReference,
    int recordVersion,
    Map<String, Object> claims) {

  public static final int VERSION_NOT_SET = -1;

  public static FollowUpEventMetadata of(final Consumer<Builder> consumer) {
    final Builder builder = new Builder();
    consumer.accept(builder);
    return builder.build();
  }

  public static FollowUpEventMetadata empty() {
    return new Builder().build();
  }

  public long getOperationReference() {
    return operationReference;
  }

  public long getBatchOperationReference() {
    return batchOperationReference;
  }

  public int getRecordVersion() {
    return recordVersion;
  }

  public Map<String, Object> getClaims() {
    return claims;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private long operationReference = RecordMetadataDecoder.operationReferenceNullValue();
    private long batchOperationReference = RecordMetadataDecoder.batchOperationReferenceNullValue();
    private int recordVersion = VERSION_NOT_SET;
    private Map<String, Object> claims = Map.of();

    public Builder operationReference(final long operationReference) {
      this.operationReference = operationReference;
      return this;
    }

    public Builder batchOperationReference(final long batchOperationReference) {
      this.batchOperationReference = batchOperationReference;
      return this;
    }

    public Builder recordVersion(final int recordVersion) {
      this.recordVersion = recordVersion;
      return this;
    }

    public Builder claims(final Map<String, Object> claims) {
      this.claims = claims;
      return this;
    }

    public FollowUpEventMetadata build() {
      return new FollowUpEventMetadata(
          operationReference, batchOperationReference, recordVersion, claims);
    }
  }
}
