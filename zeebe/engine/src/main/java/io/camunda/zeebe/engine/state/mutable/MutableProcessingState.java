/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
  MutableMessageCorrelationState getMessageCorrelationState();

  @Override
  MutableIncidentState getIncidentState();

  @Override
  MutableBannedInstanceState getBannedInstanceState();

  @Override
  MutableVariableState getVariableState();

  @Override
  MutableClusterVariableState getClusterVariableState();

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
  MutableResourceState getResourceState();

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
  MutableUserState getUserState();

  @Override
  MutableAuthorizationState getAuthorizationState();

  @Override
  MutableRoutingState getRoutingState();

  @Override
  MutableClockState getClockState();

  @Override
  MutableRoleState getRoleState();

  @Override
  MutableGroupState getGroupState();

  @Override
  MutableTenantState getTenantState();

  @Override
  MutableMappingRuleState getMappingRuleState();

  @Override
  MutableBatchOperationState getBatchOperationState();

  @Override
  MutableMembershipState getMembershipState();

  @Override
  MutableUsageMetricState getUsageMetricState();

  @Override
  MutableAsyncRequestState getAsyncRequestState();

  @Override
  MutableMultiInstanceState getMultiInstanceState();

  @Override
  MutableConditionSubscriptionState getConditionSubscriptionState();

  KeyGenerator getKeyGenerator();
}
