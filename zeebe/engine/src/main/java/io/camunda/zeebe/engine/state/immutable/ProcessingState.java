/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
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

  BannedInstanceState getBannedInstanceState();

  VariableState getVariableState();

  TimerInstanceState getTimerState();

  ElementInstanceState getElementInstanceState();

  EventScopeInstanceState getEventScopeInstanceState();

  DecisionState getDecisionState();

  FormState getFormState();

  SignalSubscriptionState getSignalSubscriptionState();

  DistributionState getDistributionState();

  PendingMessageSubscriptionState getPendingMessageSubscriptionState();

  PendingProcessMessageSubscriptionState getPendingProcessMessageSubscriptionState();

  TransientPendingSubscriptionState getTransientPendingSubscriptionState();

  MigrationState getMigrationState();

  UserTaskState getUserTaskState();

  CompensationSubscriptionState getCompensationSubscriptionState();

  int getPartitionId();

  boolean isEmpty(final ZbColumnFamilies column);

  MultiInstanceState getMultiInstanceState();
}
