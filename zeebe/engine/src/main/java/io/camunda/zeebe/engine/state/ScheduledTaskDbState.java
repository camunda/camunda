/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.batchoperation.DbBatchOperationState;
import io.camunda.zeebe.engine.state.deployment.DbDeploymentState;
import io.camunda.zeebe.engine.state.distribution.DbDistributionState;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.DeploymentState;
import io.camunda.zeebe.engine.state.immutable.DistributionState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.PendingMessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.PendingProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.instance.DbJobState;
import io.camunda.zeebe.engine.state.instance.DbTimerInstanceState;
import io.camunda.zeebe.engine.state.instance.DbUserTaskState;
import io.camunda.zeebe.engine.state.message.DbMessageState;
import io.camunda.zeebe.engine.state.message.DbMessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.DbProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.time.InstantSource;

/** Contains read-only state that can be accessed safely by scheduled tasks. */
public final class ScheduledTaskDbState implements ScheduledTaskState {

  private final DistributionState distributionState;
  private final MessageState messageState;
  private final TimerInstanceState timerInstanceState;
  private final JobState jobState;
  private final DeploymentState deploymentState;
  private final PendingMessageSubscriptionState pendingMessageSubscriptionState;
  private final PendingProcessMessageSubscriptionState pendingProcessMessageSubscriptionState;
  private final UserTaskState userTaskState;
  private final BatchOperationState batchOperationState;

  public ScheduledTaskDbState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final int partitionId,
      final TransientPendingSubscriptionState transientMessageSubscriptionState,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState,
      final InstantSource clock,
      final EngineConfiguration config) {
    distributionState = new DbDistributionState(zeebeDb, transactionContext);
    messageState = new DbMessageState(zeebeDb, transactionContext, partitionId);
    timerInstanceState = new DbTimerInstanceState(zeebeDb, transactionContext);
    jobState = new DbJobState(zeebeDb, transactionContext);
    deploymentState = new DbDeploymentState(zeebeDb, transactionContext);
    pendingMessageSubscriptionState =
        new DbMessageSubscriptionState(
            zeebeDb, transactionContext, transientMessageSubscriptionState, clock);
    pendingProcessMessageSubscriptionState =
        new DbProcessMessageSubscriptionState(
            zeebeDb, transactionContext, transientProcessMessageSubscriptionState, clock);
    userTaskState = new DbUserTaskState(zeebeDb, transactionContext);
    batchOperationState = new DbBatchOperationState(zeebeDb, transactionContext, config);
  }

  @Override
  public DistributionState getDistributionState() {
    return distributionState;
  }

  @Override
  public MessageState getMessageState() {
    return messageState;
  }

  @Override
  public TimerInstanceState getTimerState() {
    return timerInstanceState;
  }

  @Override
  public JobState getJobState() {
    return jobState;
  }

  @Override
  public DeploymentState getDeploymentState() {
    return deploymentState;
  }

  @Override
  public PendingMessageSubscriptionState getPendingMessageSubscriptionState() {
    return pendingMessageSubscriptionState;
  }

  @Override
  public PendingProcessMessageSubscriptionState getPendingProcessMessageSubscriptionState() {
    return pendingProcessMessageSubscriptionState;
  }

  @Override
  public UserTaskState getUserTaskState() {
    return userTaskState;
  }

  @Override
  public BatchOperationState getBatchOperationState() {
    return batchOperationState;
  }
}
