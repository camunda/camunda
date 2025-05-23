/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.MetadataAwareTypedEventApplier;
import io.camunda.zeebe.engine.state.TriggeringRecordMetadata;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableTriggeringRecordMetadataState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;

public class VariableDocumentUpdatingApplier
    implements MetadataAwareTypedEventApplier<VariableDocumentIntent, VariableDocumentRecord> {

  private final MutableVariableState variableState;
  private final MutableTriggeringRecordMetadataState triggeringRecordMetadataState;

  public VariableDocumentUpdatingApplier(final MutableProcessingState processingState) {
    variableState = processingState.getVariableState();
    triggeringRecordMetadataState = processingState.getTriggeringRecordMetadataState();
  }

  @Override
  public void applyState(final long key, final VariableDocumentRecord value) {
    variableState.storeVariableDocumentState(key, value);
  }

  @Override
  public void applyState(
      final long key, final VariableDocumentRecord value, final TriggeringRecordMetadata metadata) {
    applyState(key, value);
    triggeringRecordMetadataState.store(key, metadata);
  }
}
