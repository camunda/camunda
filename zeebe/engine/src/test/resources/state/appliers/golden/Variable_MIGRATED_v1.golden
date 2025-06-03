/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;

final class VariableMigratedApplier implements TypedEventApplier<VariableIntent, VariableRecord> {

  public VariableMigratedApplier() {}

  @Override
  public void applyState(final long key, final VariableRecord value) {
    // A migrated variable has an adjusted process definition key, but the process definition key is
    // not data that we persist in the state for variables yet. Additionally, the variable's value
    // is unset. If we wanted to persist the data of the variable, we first have to look up the
    // current value in order to not overwrite it. Instead, we can just do nothing for now, which is
    // also the most performant.
  }
}
