/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state;

import io.zeebe.engine.state.mutable.MutableZeebeState;
import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.intent.Intent;

/** Applies state changes for a specific event to the {@link MutableZeebeState}. */
public interface TypedEventApplier<I extends Intent, V extends RecordValue> {

  void applyState(final long key, final V value);
}
