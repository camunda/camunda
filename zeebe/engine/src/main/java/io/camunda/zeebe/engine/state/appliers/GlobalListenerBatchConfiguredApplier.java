/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.globallistener.MutableGlobalListenersState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerBatchIntent;

public final class GlobalListenerBatchConfiguredApplier
    implements TypedEventApplier<GlobalListenerBatchIntent, GlobalListenerBatchRecord> {

  private final MutableGlobalListenersState globalListenersState;

  public GlobalListenerBatchConfiguredApplier(final MutableProcessingState processingState) {
    globalListenersState = processingState.getGlobalListenersState();
  }

  @Override
  public void applyState(final long eventKey, final GlobalListenerBatchRecord value) {
    value.setGlobalListenerBatchKey(eventKey);
    globalListenersState.updateConfigKey(value.getGlobalListenerBatchKey());
  }
}
