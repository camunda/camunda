/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableBannedInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;

public class ErrorCreatedApplier implements TypedEventApplier<ErrorIntent, ErrorRecord> {

  private final MutableBannedInstanceState mutableBannedInstanceState;

  public ErrorCreatedApplier(final MutableBannedInstanceState mutableBannedInstanceState) {
    this.mutableBannedInstanceState = mutableBannedInstanceState;
  }

  @Override
  public void applyState(final long key, final ErrorRecord value) {
    mutableBannedInstanceState.banProcessInstance(value.getProcessInstanceKey());
  }
}
