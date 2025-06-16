/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api;

import io.camunda.zeebe.stream.api.records.ImmutableRecordBatch;
import io.camunda.zeebe.stream.impl.records.RecordBatchEntry;
import java.util.Optional;

/**
 * Here the interface is just a suggestion. Can be whatever PDT teams thinks is best to work with
 */
public interface ProcessingResult {

  /**
   * Returns the resulting record batch, which can be empty or consist of multiple {@link
   * RecordBatchEntry}s. These entries are the result of the current processing. If an entry is of
   * type {@link io.camunda.zeebe.protocol.record.RecordType#COMMAND} it will be later processed as
   * follow-up command.
   *
   * @return returns the resulting immutable record batch
   */
  ImmutableRecordBatch getRecordBatch();

  /**
   * @return the processing response, which should be sent as answer of a user command. Can be empty
   *     if no user command was processed.
   */
  Optional<ProcessingResponse> getProcessingResponse();

  /**
   * @return <code>false</code> to indicate that the side effect could not be applied successfully
   */
  boolean executePostCommitTasks();

  /**
   * Indicates whether the processing result is empty.
   *
   * @return true if all the following applies:
   *     <ul>
   *       <li>there is no response
   *       <li>the record batch is empty
   *       <li>there is no tasks to execute
   *     </ul>
   *     false otherwise.
   */
  boolean isEmpty();

  /**
   * Signals that any follow-up commands generated during the processing of the current record
   * should be scheduled for handling in a new, separate command batch, rather than being processed
   * within the current batch.
   *
   * <p>This method is typically called when a processor (see {@code
   * TypedRecordProcessor#shouldProcessResultsInSeparateBatches()}) has requested isolation for the
   * results it produces. Separating follow-up commands into distinct batches can improve
   * consistency and error isolation (e.g., a failure in one follow-up will not impact the
   * processing of others). However, it may also lead to reduced throughput due to increased
   * overhead, though it enables interleaving with user or system commands at batch boundaries.
   *
   * @return this builder instance for chaining
   */
  default boolean shouldProcessInASeparateBatch() {
    return false;
  }
}
