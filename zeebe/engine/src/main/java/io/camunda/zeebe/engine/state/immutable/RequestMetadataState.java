/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.impl.record.value.RequestMetadataRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.Optional;
import java.util.stream.Stream;

public interface RequestMetadataState {

  Optional<RequestMetadata> find(
      final long scopeKey, final ValueType valueType, final Intent intent);

  Stream<RequestMetadata> findAllByScopeKey(final long scopeKey);

  record RequestMetadata(RequestMetadataRecord record) {

    public long metadataKey() {
      return record.getMetadataKey();
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
