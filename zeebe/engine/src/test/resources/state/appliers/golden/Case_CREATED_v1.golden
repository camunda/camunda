/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableCaseState;
import io.camunda.zeebe.protocol.impl.record.value.cases.CaseRecord;
import io.camunda.zeebe.protocol.record.intent.CaseIntent;
import org.jspecify.annotations.NullMarked;

/** Stores the case, keyed by the key of its root process instance, together with its stages. */
@NullMarked
final class CaseCreatedApplier implements TypedEventApplier<CaseIntent, CaseRecord> {

  private final MutableCaseState caseState;

  CaseCreatedApplier(final MutableCaseState caseState) {
    this.caseState = caseState;
  }

  @Override
  public void applyState(final long key, final CaseRecord value) {
    caseState.create(key, value);
  }
}
