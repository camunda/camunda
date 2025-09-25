/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing;

import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.common.state.immutable.AsyncRequestState.AsyncRequest;
import io.camunda.zeebe.protocol.impl.record.value.AsyncRequestRecord;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class AsyncRequestBehavior {

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;

  public AsyncRequestBehavior(final KeyGenerator keyGenerator, final StateWriter stateWriter) {
    this.keyGenerator = keyGenerator;
    this.stateWriter = stateWriter;
  }

  public AsyncRequest writeAsyncRequestReceived(final long scopeKey, final TypedRecord<?> command) {
    final var key = keyGenerator.nextKey();
    final var record = toAsyncRequestRecord(scopeKey, command);
    stateWriter.appendFollowUpEvent(key, AsyncRequestIntent.RECEIVED, record);
    return new AsyncRequest(key, record);
  }

  private static AsyncRequestRecord toAsyncRequestRecord(
      final long scopeKey, final TypedRecord<?> command) {
    return new AsyncRequestRecord()
        .setScopeKey(scopeKey)
        .setIntent(command.getIntent())
        .setValueType(command.getValueType())
        .setRequestId(command.getRequestId())
        .setRequestStreamId(command.getRequestStreamId())
        .setOperationReference(command.getOperationReference());
  }
}
