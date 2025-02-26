/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.scaling.redistribution.RedistributionState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableBatchOperationState;
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

  MessageCorrelationState getMessageCorrelationState();

  IncidentState getIncidentState();

  BannedInstanceState getBannedInstanceState();

  VariableState getVariableState();

  TimerInstanceState getTimerState();

  ElementInstanceState getElementInstanceState();

  EventScopeInstanceState getEventScopeInstanceState();

  DecisionState getDecisionState();

  FormState getFormState();

  ResourceState getResourceState();

  SignalSubscriptionState getSignalSubscriptionState();

  DistributionState getDistributionState();

  PendingMessageSubscriptionState getPendingMessageSubscriptionState();

  PendingProcessMessageSubscriptionState getPendingProcessMessageSubscriptionState();

  TransientPendingSubscriptionState getTransientPendingSubscriptionState();

  MigrationState getMigrationState();

  UserTaskState getUserTaskState();

  CompensationSubscriptionState getCompensationSubscriptionState();

  UserState getUserState();

  AuthorizationState getAuthorizationState();

  RoutingState getRoutingState();

  RedistributionState getRedistributionState();

  int getPartitionId();

  boolean isEmpty(final ZbColumnFamilies column);

  ClockState getClockState();

  RoleState getRoleState();

  GroupState getGroupState();

  TenantState getTenantState();

  MappingState getMappingState();

  BatchOperationState getBatchOperationState();
}
