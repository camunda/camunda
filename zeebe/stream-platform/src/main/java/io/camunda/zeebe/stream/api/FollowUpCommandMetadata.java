/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api;

import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.encoding.InternalOperationReference;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import java.util.function.Consumer;

/**
 * Metadata of a follow-up command.
 *
 * @param isolated whether the command is written as if it came from outside the current processing
 *     batch, see {@link ProcessingResultBuilder#appendIsolatedCommand}
 */
public record FollowUpCommandMetadata(
    long operationReference,
    long batchOperationReference,
    InternalOperationReference internalOperationReference,
    AuthInfo authInfo,
    boolean isolated) {

  public static FollowUpCommandMetadata empty() {
    return of(builder -> {});
  }

  public static FollowUpCommandMetadata of(final Consumer<Builder> consumer) {
    final Builder builder = new Builder();
    consumer.accept(builder);
    return builder.build();
  }

  public static class Builder {
    private long operationReference = RecordMetadataDecoder.operationReferenceNullValue();
    private long batchOperationReference = RecordMetadataDecoder.batchOperationReferenceNullValue();
    private InternalOperationReference internalOperationReference = InternalOperationReference.NONE;
    private AuthInfo authInfo = AuthInfo.EMPTY;
    private boolean isolated = false;

    public Builder operationReference(final long operationReference) {
      this.operationReference = operationReference;
      return this;
    }

    public Builder batchOperationReference(final long batchOperationReference) {
      this.batchOperationReference = batchOperationReference;
      return this;
    }

    public Builder internalOperationReference(
        final InternalOperationReference internalOperationReference) {
      this.internalOperationReference = internalOperationReference;
      return this;
    }

    public Builder authInfo(final AuthInfo authInfo) {
      this.authInfo = authInfo;
      return this;
    }

    /**
     * Writes the command as if it came from outside the current processing batch: it inherits no
     * metadata from the batch and is processed in a batch of its own.
     */
    public Builder isolated() {
      isolated = true;
      return this;
    }

    public FollowUpCommandMetadata build() {
      return new FollowUpCommandMetadata(
          operationReference,
          batchOperationReference,
          internalOperationReference,
          authInfo,
          isolated);
    }
  }
}
