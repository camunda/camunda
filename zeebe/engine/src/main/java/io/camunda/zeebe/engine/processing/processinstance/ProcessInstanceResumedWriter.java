/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import org.jspecify.annotations.NullMarked;

/**
 * Writes {@link ProcessInstanceIntent#RESUMED} with the references of the {@code RESUME} command
 * that started the resume. {@code RESUMED} is often written by a later command, like a drained
 * command or a worker's job completion, that doesn't carry them; without the batch reference a
 * batch resume item would never complete.
 */
@NullMarked
public final class ProcessInstanceResumedWriter {

  private final StateWriter stateWriter;
  private final AsyncRequestState asyncRequestState;

  public ProcessInstanceResumedWriter(
      final StateWriter stateWriter, final AsyncRequestState asyncRequestState) {
    this.stateWriter = stateWriter;
    this.asyncRequestState = asyncRequestState;
  }

  public void writeResumed(final long processInstanceKey, final ProcessInstanceRecord value) {
    asyncRequestState
        .findRequest(processInstanceKey, ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.RESUME)
        .ifPresentOrElse(
            request -> {
              stateWriter.appendFollowUpEvent(
                  processInstanceKey,
                  ProcessInstanceIntent.RESUMED,
                  value,
                  m ->
                      m.operationReference(request.operationReference())
                          .batchOperationReference(request.batchOperationReference()));
              stateWriter.appendFollowUpEvent(
                  request.key(), AsyncRequestIntent.PROCESSED, request.record());
            },
            // resumes started before the request was stored have none
            () ->
                stateWriter.appendFollowUpEvent(
                    processInstanceKey, ProcessInstanceIntent.RESUMED, value));
  }
}
