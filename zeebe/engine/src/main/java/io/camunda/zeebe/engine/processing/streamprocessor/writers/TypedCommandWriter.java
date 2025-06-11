/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** This interface is supposed to replace TypedCommandWriter */
public interface TypedCommandWriter {

  /**
   * Append a new command to the result builder
   *
   * @param intent the intent of the command
   * @param value the record of the command
   * @throws ExceededBatchRecordSizeException if the appended command doesn't fit into the
   *     RecordBatch
   */
  void appendNewCommand(Intent intent, RecordValue value);

  /**
   * Append a follow up command to the result builder
   *
   * @param intent the intent of the command
   * @param value the record of the command
   * @throws ExceededBatchRecordSizeException if the appended command doesn't fit into the
   *     RecordBatch
   */
  void appendFollowUpCommand(long key, Intent intent, RecordValue value);

  /**
   * Append a follow up command to the result builder
   *
   * @param intent the intent of the command
   * @param value the record of the command
   * @param metadata the optional metadata for the command
   * @throws ExceededBatchRecordSizeException if the appended command doesn't fit into the
   *     RecordBatch
   */
  void appendFollowUpCommand(long key, Intent intent, RecordValue value, CommandMetadata metadata);

  /**
   * @param commandLength the length of the command that will be written
   * @return true if a command of the given length can be written
   */
  boolean canWriteCommandOfLength(final int commandLength);

  record CommandMetadata(
      long operationReference, long batchOperationReference, Map<String, Object> claims) {

    public static CommandMetadataBuilder builder() {
      return new CommandMetadataBuilder();
    }

    public static CommandMetadata of(final Consumer<CommandMetadataBuilder> consumer) {
      final CommandMetadataBuilder builder = new CommandMetadataBuilder();
      consumer.accept(builder);
      return builder.build();
    }

    public static class CommandMetadataBuilder {
      private long operationReference = RecordMetadataDecoder.operationReferenceNullValue();
      private long batchOperationReference =
          RecordMetadataDecoder.batchOperationReferenceNullValue();
      private Map<String, Object> claims = null;

      public CommandMetadataBuilder operationReference(final long operationReference) {
        this.operationReference = operationReference;
        return this;
      }

      public CommandMetadataBuilder batchOperationReference(final long batchOperationReference) {
        this.batchOperationReference = batchOperationReference;
        return this;
      }

      public CommandMetadataBuilder claims(final Map<String, Object> claims) {
        this.claims = claims;
        return this;
      }

      public CommandMetadataBuilder claim(final String key, final Object value) {
        if (claims == null) {
          claims = new HashMap<>();
        }

        claims.put(key, value);
        return this;
      }

      public CommandMetadata build() {
        return new CommandMetadata(operationReference, batchOperationReference, claims);
      }
    }
  }
}
