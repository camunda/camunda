/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.handlers;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;

public class DeleteHistoryProcessInstanceBatchOperationExecutor implements BatchOperationExecutor {

  final StateWriter stateWriter;

  public DeleteHistoryProcessInstanceBatchOperationExecutor(final StateWriter stateWriter) {
    this.stateWriter = stateWriter;
  }

  @Override
  public void execute(final long itemKey, final PersistedBatchOperation batchOperation) {
    // TODO delete the historic data for a the given item key
    System.out.println(itemKey);

    // Option 1: Write directly to secondary storage from the engine
    deleteHistoryByWritingFromTheEngine();

    // Option 2: Write an event to the log and delete the resources through events in the exporter
    deleteHistoryThroughExporter(itemKey);

    // Option 3: Somehow reuse the archiver to delete the resources from secondary storage

    // Option 4: Write a command with the filter and export it. Separate component can read the
    // filter and use it to delete in batches externally.
    // Pro: We go through the exporter so we know all data is available in secondary storage.
  }

  private void deleteHistoryByWritingFromTheEngine() {
    // Considerations:
    // - Do we write a command first or delete through this executor? Replication to other brokers
    // is important.
    // - Is this executor fault tolerant? What happens if there's a timeout when performing the
    // request to secondary storage?

    // What if there is an exporter backlog. There is no way of knowing in the engine if all data is
    // in secondary storage.
  }

  private void deleteHistoryThroughExporter(final long itemKey) {
    // Could have a big impact on the exporter performance. Might be good enough to let users know
    // this.
    // Allow setting a maximum limit on the batch operation size to limit the impact.
    // TODO consider writing command before event for semantics
    final var event = new ProcessInstanceRecord();
    event.setProcessInstanceKey(itemKey);
    stateWriter.appendFollowUpEvent(itemKey, ProcessInstanceIntent.DELETED, event);
  }
}
