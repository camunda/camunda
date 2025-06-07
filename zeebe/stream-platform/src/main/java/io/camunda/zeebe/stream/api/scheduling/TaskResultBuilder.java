/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api.scheduling;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Here the interface is just a suggestion. Can be whatever PDT team thinks is best to work with */
public interface TaskResultBuilder {

  long NULL_KEY = -1;

  /**
   * Appends a record to the result without a key
   *
   * @return returns true if the record still fits into the result, false otherwise
   */
  default boolean appendCommandRecord(final Intent intent, final UnifiedRecordValue value) {
    return appendCommandRecord(NULL_KEY, intent, value);
  }

  /**
   * Appends a record to the result
   *
   * @return returns true if the record still fits into the result, false otherwise
   */
  default boolean appendCommandRecord(
      final long key, final Intent intent, final UnifiedRecordValue value) {
    return appendCommandRecord(key, intent, value, Metadata.of(b -> {}));
  }

  /**
   * Appends a record to the result
   *
   * @return returns true if the record still fits into the result, false otherwise
   */
  boolean appendCommandRecord(
      final long key, final Intent intent, final UnifiedRecordValue value, final Metadata metadata);

  TaskResult build();

  record Metadata(long operationReference, long batchOperationKey, Map<String, Object> claims) {

    public static MetadataBuilder builder() {
      return new MetadataBuilder();
    }

    public static Metadata of(final Consumer<MetadataBuilder> consumer) {
      final MetadataBuilder builder = new MetadataBuilder();
      consumer.accept(builder);
      return builder.build();
    }

    public static class MetadataBuilder {
      private long operationReference = RecordMetadataDecoder.operationReferenceNullValue();
      private long batchOperationReference =
          RecordMetadataDecoder.batchOperationReferenceNullValue();
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

      public Metadata build() {
        return new Metadata(operationReference, batchOperationReference, claims);
      }
    }
  }
}
