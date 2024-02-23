/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.api.records;

import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferWriter;

/**
 * Represents a modifiable batch of record, which means we can add multiple Records to the batch.
 * For further processing the user can iterate of the appended entries and retrieve the needed data.
 */
public interface MutableRecordBatch extends ImmutableRecordBatch {

  /**
   * Allows to add a new Record to the batch
   *
   * @param key the key of the record
   * @param metadata the record's metadata
   * @param sourceIndex the position/index in the current batch which caused that entry; should be
   *     set to -1 if no entry caused it
   * @param valueWriter the actual record value
   * @return either a failure if record can't be added to the batch or null on success
   */
  Either<RuntimeException, Void> appendRecord(
      final long key,
      final RecordMetadata metadata,
      final int sourceIndex,
      final BufferWriter valueWriter);

  /**
   * Allows to verify whether the given record length is suitable to be appended in the current
   * batch. This method is useful if you have one record which will be updated and you don't want to
   * append it right now, just to verify whether it would still fit.
   *
   * @param recordLength the expected record length, which needs to be verified
   * @return true if the record length would fit into the batch, false otherwise
   */
  boolean canAppendRecordOfLength(int recordLength);
}
