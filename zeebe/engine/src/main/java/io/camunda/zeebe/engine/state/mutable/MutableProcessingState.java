/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public interface MutableProcessingState extends ProcessingState {

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
  MutableBannedInstanceState getBannedInstanceState();

  @Override
  MutableVariableState getVariableState();

  @Override
  MutableTimerInstanceState getTimerState();

  @Override
  MutableElementInstanceState getElementInstanceState();

  @Override
  MutableEventScopeInstanceState getEventScopeInstanceState();

  @Override
  MutableDecisionState getDecisionState();

  @Override
  MutableFormState getFormState();

  @Override
  MutableSignalSubscriptionState getSignalSubscriptionState();

  @Override
  MutableDistributionState getDistributionState();

  @Override
  MutableMigrationState getMigrationState();

  @Override
  MutableUserTaskState getUserTaskState();

  @Override
  MutableCompensationSubscriptionState getCompensationSubscriptionState();

  @Override
  MutableMultiInstanceState getMultiInstanceState();

  KeyGenerator getKeyGenerator();
}
