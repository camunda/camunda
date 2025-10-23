/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.deployment.DbHistoryDeletionState;
import io.camunda.zeebe.engine.state.routing.DbRoutingState;

public interface ScheduledTaskState {

  DistributionState getDistributionState();

  MessageState getMessageState();

  TimerInstanceState getTimerState();

  JobState getJobState();

  DeploymentState getDeploymentState();

  PendingMessageSubscriptionState getPendingMessageSubscriptionState();

  PendingProcessMessageSubscriptionState getPendingProcessMessageSubscriptionState();

  UserTaskState getUserTaskState();

  BatchOperationState getBatchOperationState();

  DbRoutingState getRoutingState();

  DbHistoryDeletionState getHistoryDeletionState();
}
