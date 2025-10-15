/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api;

import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import java.util.Map;
import java.util.function.Consumer;

public record FollowUpCommandMetadata(
    long operationReference, long batchOperationReference, AuthInfo authInfo) {

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
    private final AuthInfo authInfo = new AuthInfo();

    public Builder operationReference(final long operationReference) {
      this.operationReference = operationReference;
      return this;
    }

    public Builder batchOperationReference(final long batchOperationReference) {
      this.batchOperationReference = batchOperationReference;
      return this;
    }

    public Builder authInfo(final AuthInfo authInfo) {
      this.authInfo.copyFrom(authInfo);
      return this;
    }

    public Builder claims(final Map<String, Object> claims) {
      authInfo.setClaims(claims);
      return this;
    }

    public FollowUpCommandMetadata build() {
      return new FollowUpCommandMetadata(operationReference, batchOperationReference, authInfo);
    }
  }
}
