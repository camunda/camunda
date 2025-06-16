/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api;

import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public record FollowUpCommandMetadata(
    long operationReference, long batchOperationReference, Map<String, Object> claims) {

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
    private Map<String, Object> claims = null;

    public Builder operationReference(final long operationReference) {
      this.operationReference = operationReference;
      return this;
    }

    public Builder batchOperationReference(final long batchOperationReference) {
      this.batchOperationReference = batchOperationReference;
      return this;
    }

    public Builder claims(final Map<String, Object> claims) {
      this.claims = claims;
      return this;
    }

    public Builder claim(final String key, final Object value) {
      if (claims == null) {
        claims = new HashMap<>();
      }

      claims.put(key, value);
      return this;
    }

    public FollowUpCommandMetadata build() {
      return new FollowUpCommandMetadata(operationReference, batchOperationReference, claims);
    }
  }
}
