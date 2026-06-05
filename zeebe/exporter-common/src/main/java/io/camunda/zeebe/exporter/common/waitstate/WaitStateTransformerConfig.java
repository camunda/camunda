/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate;

import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry.WaitStateType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.Set;

/**
 * Configuration for a {@link WaitStateTransformer}, declaring which record intents cause a
 * waiting-state entry to be added to the index and which cause it to be removed, and the {@link
 * WaitStateType} associated with this transformer.
 */
public record WaitStateTransformerConfig(
    ValueType valueType,
    Set<Intent> addIntents,
    Set<Intent> updateIntents,
    Set<Intent> removeIntents,
    Set<BpmnElementType> supportedElementTypes,
    WaitStateType waitStateType) {

  public static WaitStateTransformerConfig of(final ValueType valueType) {
    return new WaitStateTransformerConfig(valueType, Set.of(), Set.of(), Set.of(), Set.of(), null);
  }

  public WaitStateTransformerConfig withAddIntents(final Intent... intents) {
    return new WaitStateTransformerConfig(
        valueType,
        Set.of(intents),
        updateIntents,
        removeIntents,
        supportedElementTypes,
        waitStateType);
  }

  public WaitStateTransformerConfig withUpdateIntents(final Intent... intents) {
    return new WaitStateTransformerConfig(
        valueType,
        addIntents,
        Set.of(intents),
        removeIntents,
        supportedElementTypes,
        waitStateType);
  }

  public WaitStateTransformerConfig withRemoveIntents(final Intent... intents) {
    return new WaitStateTransformerConfig(
        valueType,
        addIntents,
        updateIntents,
        Set.of(intents),
        supportedElementTypes,
        waitStateType);
  }

  public WaitStateTransformerConfig withSupportedElementTypes(
      final BpmnElementType... elementTypes) {
    return new WaitStateTransformerConfig(
        valueType, addIntents, updateIntents, removeIntents, Set.of(elementTypes), waitStateType);
  }

  public WaitStateTransformerConfig withWaitStateType(final WaitStateType waitStateType) {
    return new WaitStateTransformerConfig(
        valueType, addIntents, updateIntents, removeIntents, supportedElementTypes, waitStateType);
  }

  public boolean supports(final Record<?> record) {
    if (record.getRecordType() != RecordType.EVENT) {
      return false;
    }
    return true;
  }

  public boolean triggersAdd(final Record<?> record) {
    return supports(record) && addIntents.contains(record.getIntent());
  }

  public boolean triggersUpdate(final Record<?> record) {
    return supports(record) && updateIntents.contains(record.getIntent());
  }

  public boolean triggersRemoval(final Record<?> record) {
    return supports(record) && removeIntents.contains(record.getIntent());
  }
}
