/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;

public interface ProcessingState extends StreamProcessorLifecycleAware {

  DeploymentState getDeploymentState();

  ProcessState getProcessState();

  JobState getJobState();

  MessageState getMessageState();

  MessageSubscriptionState getMessageSubscriptionState();

  MessageStartEventSubscriptionState getMessageStartEventSubscriptionState();

  ProcessMessageSubscriptionState getProcessMessageSubscriptionState();

  IncidentState getIncidentState();

  BlackListState getBlackListState();

  VariableState getVariableState();

  TimerInstanceState getTimerState();

  ElementInstanceState getElementInstanceState();

  EventScopeInstanceState getEventScopeInstanceState();

  DecisionState getDecisionState();

  SignalSubscriptionState getSignalSubscriptionState();

  DistributionState getDistributionState();

  int getPartitionId();

  boolean isEmpty(final ZbColumnFamilies column);
}
