/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableMappingRuleState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;

public class MappingRuleDeletedApplier
    implements TypedEventApplier<MappingRuleIntent, MappingRuleRecord> {

  private final MutableMappingRuleState mappingRuleState;

  public MappingRuleDeletedApplier(final MutableMappingRuleState mappingRuleState) {
    this.mappingRuleState = mappingRuleState;
  }

  @Override
  public void applyState(final long key, final MappingRuleRecord value) {
    mappingRuleState.delete(value.getMappingRuleId());
  }
}
