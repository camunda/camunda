/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.api.records;

import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;

/**
 * Represents an unmodifiable entry of an {@link ImmutableRecordBatch}. Contains data about a record
 * which has been created by an RecordProcessor.
 */
public interface ImmutableRecordBatchEntry {

  /**
   * @return the key of the record
   */
  long key();

  /**
   * @return points to a command which is part of the same batch, which caused that entry
   */
  int sourceIndex();

  /**
   * @return meta data of the record, like ValueType, Intent, RecordType etc.
   */
  RecordMetadata recordMetadata();

  /**
   * @return the actual record value, this method returns a general type but can be casted to the
   *     right record value class if necessary
   */
  UnifiedRecordValue recordValue();

  /**
   * @return the length of the record entry, important for the batch to determine whether it reached
   *     its maximum size
   */
  int getLength();

  default boolean shouldSkipProcessing() {
    return false;
  }
}
