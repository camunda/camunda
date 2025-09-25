/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.immutable;

import io.camunda.zeebe.engine.common.state.asyncrequest.AsyncRequestMetadataValue;
import io.camunda.zeebe.protocol.impl.record.value.AsyncRequestRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.Optional;
import java.util.stream.Stream;

public interface AsyncRequestState {

  Optional<AsyncRequest> findRequest(
      final long scopeKey, final ValueType valueType, final Intent intent);

  Stream<AsyncRequest> findAllRequestsByScopeKey(final long scopeKey);

  record AsyncRequest(long key, AsyncRequestRecord record) {

    public AsyncRequest(final AsyncRequestMetadataValue value) {
      this(value.getAsyncRequestKey(), value.getRecord());
    }

    public ValueType valueType() {
      return record.getValueType();
    }

    public Intent intent() {
      return record.getIntent();
    }

    public long requestId() {
      return record.getRequestId();
    }

    public int requestStreamId() {
      return record.getRequestStreamId();
    }

    public long operationReference() {
      return record.getOperationReference();
    }
  }
}
