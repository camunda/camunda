/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableManagedScriptDefinitionState;
import io.camunda.zeebe.protocol.impl.record.value.managedscriptdefinition.ManagedScriptDefinitionRecord;
import io.camunda.zeebe.protocol.record.intent.ManagedScriptDefinitionIntent;

public final class ManagedScriptDefinitionUpdatedApplier
    implements TypedEventApplier<ManagedScriptDefinitionIntent, ManagedScriptDefinitionRecord> {

  private final MutableManagedScriptDefinitionState managedScriptDefinitionState;

  public ManagedScriptDefinitionUpdatedApplier(
      final MutableManagedScriptDefinitionState managedScriptDefinitionState) {
    this.managedScriptDefinitionState = managedScriptDefinitionState;
  }

  @Override
  public void applyState(final long key, final ManagedScriptDefinitionRecord value) {
    managedScriptDefinitionState.insert(key, value);
  }
}
