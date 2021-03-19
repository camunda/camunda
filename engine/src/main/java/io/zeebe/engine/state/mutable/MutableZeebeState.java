/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.mutable;

import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.immutable.ZeebeState;

public interface MutableZeebeState extends ZeebeState {

  @Override
  MutableDeploymentState getDeploymentState();

  @Override
  MutableProcessState getProcessState();

  @Override
  MutableJobState getJobState();

  @Override
  MutableMessageState getMessageState();

  @Override
  MutableMessageSubscriptionState getMessageSubscriptionState();

  @Override
  MutableMessageStartEventSubscriptionState getMessageStartEventSubscriptionState();

  @Override
  MutableProcessMessageSubscriptionState getProcessMessageSubscriptionState();

  @Override
  MutableIncidentState getIncidentState();

  @Override
  MutableBlackListState getBlackListState();

  @Override
  MutableVariableState getVariableState();

  @Override
  MutableTimerInstanceState getTimerState();

  @Override
  MutableElementInstanceState getElementInstanceState();

  @Override
  MutableEventScopeInstanceState getEventScopeInstanceState();

  KeyGenerator getKeyGenerator();
}
