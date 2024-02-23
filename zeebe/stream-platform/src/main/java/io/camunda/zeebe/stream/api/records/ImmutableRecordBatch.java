/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.api.records;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.stream.impl.records.RecordBatchEntry;
import java.util.List;

/**
 * Represents an unmodifiable batch of records, which extends the {@link Iterable<RecordBatchEntry>}
 * in order to make sure that the contained entries can be accessed.
 */
public interface ImmutableRecordBatch extends Iterable<RecordBatchEntry> {

  /**
   * @return true if no records to append
   */
  default boolean isEmpty() {
    return entries().isEmpty();
  }

  List<LogAppendEntry> entries();
}
