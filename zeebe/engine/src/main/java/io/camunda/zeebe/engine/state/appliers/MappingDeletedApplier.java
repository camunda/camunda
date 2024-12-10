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
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;

public class MappingDeletedApplier implements TypedEventApplier<MappingIntent, MappingRecord> {

  private final MutableMappingState mappingState;
  private final MutableAuthorizationState authorizationState;

  public MappingDeletedApplier(final MutableProcessingState state) {
    mappingState = state.getMappingState();
    authorizationState = state.getAuthorizationState();
  }

  @Override
  public void applyState(final long key, final MappingRecord value) {
    // retrieve mapping from the state
    final var mappingKey = value.getMappingKey();
    final var mapping = mappingState.get(mappingKey);
    if (mapping.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "Expected to delete mapping with key '%s', but a mapping with this key does not exist.",
              value.getMappingKey()));
    }
    // remove mapping from authorization state
    authorizationState.deleteOwnerTypeByKey(mappingKey);
    mappingState.delete(mappingKey);
  }
}
