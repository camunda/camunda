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
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;

public class UserCreatedApplier implements TypedEventApplier<UserIntent, UserRecord> {

  private final MutableUserState userState;
  private final MutableAuthorizationState authorizationState;

  public UserCreatedApplier(final MutableProcessingState processingState) {
    userState = processingState.getUserState();
    authorizationState = processingState.getAuthorizationState();
  }

  @Override
  public void applyState(final long key, final UserRecord value) {
    userState.create(value);
    authorizationState.insertOwnerTypeByKey(value.getUserKey(), AuthorizationOwnerType.USER);
  }
}
