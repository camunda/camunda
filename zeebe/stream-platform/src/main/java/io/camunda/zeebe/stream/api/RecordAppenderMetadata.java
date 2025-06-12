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

public record RecordAppenderMetadata(
    long operationReference, long batchOperationReference, Map<String, Object> claims) {

  public static RecordAppenderMetadata of(final Consumer<MetadataBuilder> consumer) {
    final MetadataBuilder builder = new MetadataBuilder();
    consumer.accept(builder);
    return builder.build();
  }

  public static class MetadataBuilder {
    private long operationReference = RecordMetadataDecoder.operationReferenceNullValue();
    private long batchOperationReference = RecordMetadataDecoder.batchOperationReferenceNullValue();
    private Map<String, Object> claims = null;

    public MetadataBuilder operationReference(final long operationReference) {
      this.operationReference = operationReference;
      return this;
    }

    public MetadataBuilder batchOperationReference(final long batchOperationReference) {
      this.batchOperationReference = batchOperationReference;
      return this;
    }

    public MetadataBuilder claims(final Map<String, Object> claims) {
      this.claims = claims;
      return this;
    }

    public MetadataBuilder claim(final String key, final Object value) {
      if (claims == null) {
        claims = new HashMap<>();
      }

      claims.put(key, value);
      return this;
    }

    public RecordAppenderMetadata build() {
      return new RecordAppenderMetadata(operationReference, batchOperationReference, claims);
    }
  }
}
