/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.managedscriptdefinition;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ManagedScriptDefinitionIntent;
import java.time.InstantSource;

public final class ManagedScriptDefinitionProcessors {

  private ManagedScriptDefinitionProcessors() {}

  public static void addProcessors(
      final TypedRecordProcessors processors,
      final MutableProcessingState state,
      final Writers writers,
      final InstantSource clock,
      final int partitionId) {
    processors
        .onCommand(
            ValueType.MANAGED_SCRIPT_DEFINITION,
            ManagedScriptDefinitionIntent.ACTIVATE,
            new ManagedScriptDefinitionActivateProcessor(state, writers, clock, partitionId))
        .onCommand(
            ValueType.MANAGED_SCRIPT_DEFINITION,
            ManagedScriptDefinitionIntent.RENEW_LEASE,
            new ManagedScriptDefinitionRenewLeaseProcessor(state, writers, clock))
        .onCommand(
            ValueType.MANAGED_SCRIPT_DEFINITION,
            ManagedScriptDefinitionIntent.UPDATE,
            new ManagedScriptDefinitionUpdateProcessor(state, writers, clock))
        .onCommand(
            ValueType.MANAGED_SCRIPT_DEFINITION,
            ManagedScriptDefinitionIntent.GET,
            new ManagedScriptDefinitionGetProcessor(state, writers));
  }
}
