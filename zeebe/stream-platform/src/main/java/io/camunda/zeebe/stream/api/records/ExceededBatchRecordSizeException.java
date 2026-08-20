/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api.records;

import io.camunda.zeebe.stream.impl.records.RecordBatchEntry;
import io.camunda.zeebe.util.StringUtil;

/**
 * This exception is part of the contract with the engine. The engine may handle this exception
 * explicitly
 */
public class ExceededBatchRecordSizeException extends RuntimeException {

  public ExceededBatchRecordSizeException(
      final RecordBatchEntry recordBatchEntry,
      final int entryLength,
      final int recordBatchEntriesSize,
      final int batchSize) {
    super(
        """
            Can't append entry: '%s' with size: %d this would exceed the maximum batch size. \
            [ currentBatchEntryCount: %d, currentBatchSize: %d]"""
            .formatted(
                StringUtil.limitString(recordBatchEntry.toString(), 1024),
                entryLength,
                recordBatchEntriesSize,
                batchSize));
  }
}
