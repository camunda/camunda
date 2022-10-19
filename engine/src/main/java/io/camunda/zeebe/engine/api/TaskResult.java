/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.api;

import io.camunda.zeebe.logstreams.ImmutableRecordBatch;
import io.camunda.zeebe.engine.api.records.RecordBatchEntry;

public interface TaskResult {

  /**
   * Returns the resulting record batch, which can be empty or consist of multiple {@link
   * RecordBatchEntry}s. These entries are the result of the current task execution. If an entry is
   * of type {@link io.camunda.zeebe.protocol.record.RecordType#COMMAND} it will be later processed
   * as follow-up command by the {@link RecordProcessor}
   *
   * @return returns the resulting immutable record batch
   */
  ImmutableRecordBatch getRecordBatch();
}
