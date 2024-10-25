/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutableMappingState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;

public class MappingCreatedApplier implements TypedEventApplier<MappingIntent, MappingRecord> {

  private final MutableMappingState mappingState;
  private final MutableAuthorizationState authorizationState;

  public MappingCreatedApplier(
      final MutableMappingState mappingState, final MutableAuthorizationState authorizationState) {
    this.mappingState = mappingState;
    this.authorizationState = authorizationState;
  }

  @Override
  public void applyState(final long key, final MappingRecord value) {
    mappingState.create(value);
    authorizationState.insertOwnerTypeByKey(value.getMappingKey(), AuthorizationOwnerType.MAPPING);
  }
}
