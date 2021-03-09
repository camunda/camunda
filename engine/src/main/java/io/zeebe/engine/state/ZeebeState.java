/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state;

import io.zeebe.engine.state.immutable.BlackListState;
import io.zeebe.engine.state.immutable.DeploymentState;
import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.zeebe.engine.state.immutable.IncidentState;
import io.zeebe.engine.state.immutable.JobState;
import io.zeebe.engine.state.immutable.MessageStartEventSubscriptionState;
import io.zeebe.engine.state.immutable.MessageState;
import io.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.zeebe.engine.state.immutable.ProcessInstanceSubscriptionState;
import io.zeebe.engine.state.immutable.ProcessState;
import io.zeebe.engine.state.immutable.TimerInstanceState;
import io.zeebe.engine.state.immutable.VariableState;

public interface ZeebeState {

  DeploymentState getDeploymentState();

  ProcessState getProcessState();

  JobState getJobState();

  MessageState getMessageState();

  MessageSubscriptionState getMessageSubscriptionState();

  MessageStartEventSubscriptionState getMessageStartEventSubscriptionState();

  ProcessInstanceSubscriptionState getProcessInstanceSubscriptionState();

  IncidentState getIncidentState();

  BlackListState getBlackListState();

  VariableState getVariableState();

  TimerInstanceState getTimerState();

  ElementInstanceState getElementInstanceState();

  EventScopeInstanceState getEventScopeInstanceState();

  int getPartitionId();

  boolean isEmpty(final ZbColumnFamilies column);
}
