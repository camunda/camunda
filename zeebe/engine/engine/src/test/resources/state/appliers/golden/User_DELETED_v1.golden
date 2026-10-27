/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.intent.UserIntent;

public class UserDeletedApplier implements TypedEventApplier<UserIntent, UserRecord> {
  private final MutableUserState userState;

  public UserDeletedApplier(final MutableProcessingState processingState) {
    userState = processingState.getUserState();
  }

  @Override
  public void applyState(final long key, final UserRecord value) {
    final var username = value.getUsername();
    userState.deleteByUserKey(value.getUserKey());
    userState.deleteByUsername(username);
  }
}
