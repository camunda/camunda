/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableAsyncRequestState;
import io.camunda.zeebe.protocol.impl.record.value.AsyncRequestRecord;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;

public class AsyncRequestReceivedApplier
    implements TypedEventApplier<AsyncRequestIntent, AsyncRequestRecord> {

  final MutableAsyncRequestState asyncRequestState;

  public AsyncRequestReceivedApplier(final MutableAsyncRequestState asyncRequestState) {
    this.asyncRequestState = asyncRequestState;
  }

  @Override
  public void applyState(final long key, final AsyncRequestRecord value) {
    asyncRequestState.storeRequest(key, value);
  }
}
