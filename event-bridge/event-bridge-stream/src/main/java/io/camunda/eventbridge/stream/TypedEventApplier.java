/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.stream;

import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;

/**
 * Applies the state changes for a single event intent. Mirrors the Zeebe engine's {@code
 * TypedEventApplier}: one applier per {@code (intent)} is registered in {@link EventAppliers} and
 * is the <em>only</em> place that mutates state, so the leader (on write) and followers (on replay)
 * run identical logic and converge to the same state.
 *
 * @param <I> the event intent this applier handles
 * @param <V> the record value carried by the event
 */
@FunctionalInterface
public interface TypedEventApplier<I extends Intent, V extends RecordValue> {

  void applyState(long key, V value);
}
