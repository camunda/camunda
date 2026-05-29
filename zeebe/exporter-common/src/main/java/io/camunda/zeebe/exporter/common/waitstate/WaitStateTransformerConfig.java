/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.Set;

/**
 * Configuration for a {@link WaitStateTransformer}, declaring which record intents cause a
 * waiting-state entry to be added to the index and which cause it to be removed.
 */
public record WaitStateTransformerConfig(
    ValueType valueType, Set<Intent> addIntents, Set<Intent> removeIntents) {

  public static WaitStateTransformerConfig of(final ValueType valueType) {
    return new WaitStateTransformerConfig(valueType, Set.of(), Set.of());
  }

  public WaitStateTransformerConfig withAddIntents(final Intent... intents) {
    return new WaitStateTransformerConfig(valueType, Set.of(intents), removeIntents);
  }

  public WaitStateTransformerConfig withRemoveIntents(final Intent... intents) {
    return new WaitStateTransformerConfig(valueType, addIntents, Set.of(intents));
  }

  public boolean supports(final Record<?> record) {
    if (record.getRecordType() != RecordType.EVENT) {
      return false;
    }
    final Intent intent = record.getIntent();
    return addIntents.contains(intent) || removeIntents.contains(intent);
  }

  public boolean triggersAdd(final Record<?> record) {
    return addIntents.contains(record.getIntent());
  }

  public boolean triggersRemoval(final Record<?> record) {
    return removeIntents.contains(record.getIntent());
  }
}
