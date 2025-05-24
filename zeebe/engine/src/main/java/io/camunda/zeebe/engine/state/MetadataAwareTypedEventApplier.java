/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;

/**
 * Optional extension of {@link TypedEventApplier} to support additional triggering record metadata.
 *
 * <p>Implementations that require access to requestId, streamId, or operationReference should
 * implement this interface instead of {@link TypedEventApplier} directly.
 */
public interface MetadataAwareTypedEventApplier<I extends Intent, V extends RecordValue>
    extends TypedEventApplier<I, V> {

  /** Applies state transition with access to the triggering record's metadata. */
  void applyState(final long key, final V value, final TriggeringRecordMetadata metadata);
}
