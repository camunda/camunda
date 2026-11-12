/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.engine.state.globallistener.MutableGlobalListenersState;
import io.camunda.zeebe.engine.state.message.DbMessageCorrelationState;
import io.camunda.zeebe.engine.state.message.DbMessageStartProcessInstanceAskState;
import io.camunda.zeebe.engine.state.message.DbMessageStartProcessInstanceDedupState;
import io.camunda.zeebe.engine.state.message.DbMessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.DbProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.TransientPendingMessageStartProcessInstanceAskState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableAgentDefinitionState;
import io.camunda.zeebe.engine.state.mutable.MutableAgentHistoryState;
import io.camunda.zeebe.engine.state.mutable.MutableAgentInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableAsyncRequestState;
import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutableBannedInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableBatchOperationState;
import io.camunda.zeebe.engine.state.mutable.MutableClockState;
import io.camunda.zeebe.engine.state.mutable.MutableClusterVariableState;
import io.camunda.zeebe.engine.state.mutable.MutableCompensationSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableConditionalSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableDecisionState;
import io.camunda.zeebe.engine.state.mutable.MutableDeploymentState;
import io.camunda.zeebe.engine.state.mutable.MutableDistributionState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableFormState;
import io.camunda.zeebe.engine.state.mutable.MutableGroupState;
import io.camunda.zeebe.engine.state.mutable.MutableIncidentState;
import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableMappingRuleState;
import io.camunda.zeebe.engine.state.mutable.MutableMembershipState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageState;
import io.camunda.zeebe.engine.state.mutable.MutableMigrationState;
import io.camunda.zeebe.engine.state.mutable.MutableMultiInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableResourceState;
import io.camunda.zeebe.engine.state.mutable.MutableRoleState;
import io.camunda.zeebe.engine.state.mutable.MutableRoutingState;
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.engine.state.mutable.MutableSignalSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableSuspensionState;
import io.camunda.zeebe.engine.state.mutable.MutableTenantState;
import io.camunda.zeebe.engine.state.mutable.MutableTimerInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import java.time.InstantSource;

/**
 * Creates the state stores of the {@link ProcessingDbState}, so the storage behind each store can
 * be chosen without {@link ProcessingDbState} knowing about it. Dependencies between stores are
 * passed as arguments.
 */
public interface ProcessingStateStores {

  MutableVariableState variableState();

  MutableClusterVariableState clusterVariableState();

  MutableAgentHistoryState agentHistoryState();

  MutableAgentInstanceState agentInstanceState();

  MutableAgentDefinitionState agentDefinitionState();

  MutableProcessState processState(
      final EngineConfiguration config, final BpmnTransformer transformer);

  MutableTimerInstanceState timerInstanceState();

  MutableElementInstanceState elementInstanceState(final MutableVariableState variableState);

  MutableEventScopeInstanceState eventScopeInstanceState();

  MutableDeploymentState deploymentState();

  MutableJobState jobState();

  MutableMessageState messageState(final int partitionId);

  DbMessageSubscriptionState messageSubscriptionState(
      final TransientPendingSubscriptionState transientState, final InstantSource clock);

  MutableMessageStartEventSubscriptionState messageStartEventSubscriptionState();

  DbMessageStartProcessInstanceDedupState messageStartProcessInstanceDedupState();

  DbMessageStartProcessInstanceAskState messageStartProcessInstanceAskState(
      final TransientPendingMessageStartProcessInstanceAskState transientAskState);

  DbProcessMessageSubscriptionState processMessageSubscriptionState(
      final TransientPendingSubscriptionState transientState, final InstantSource clock);

  DbMessageCorrelationState messageCorrelationState();

  MutableIncidentState incidentState();

  MutableBannedInstanceState bannedInstanceState();

  MutableSuspensionState suspensionState();

  MutableDecisionState decisionState(final EngineConfiguration config);

  MutableFormState formState(final EngineConfiguration config);

  MutableResourceState resourceState(final EngineConfiguration config);

  MutableSignalSubscriptionState signalSubscriptionState();

  MutableDistributionState distributionState();

  MutableMigrationState migrationState();

  MutableUserTaskState userTaskState();

  MutableCompensationSubscriptionState compensationSubscriptionState();

  MutableUserState userState();

  MutableClockState clockState();

  MutableAuthorizationState authorizationState();

  MutableRoutingState routingState();

  MutableRoleState roleState();

  MutableGroupState groupState(final EngineConfiguration config);

  MutableTenantState tenantState();

  MutableMappingRuleState mappingRuleState();

  MutableBatchOperationState batchOperationState();

  MutableMembershipState membershipState();

  MutableUsageMetricState usageMetricState();

  MutableMultiInstanceState multiInstanceState();

  MutableAsyncRequestState asyncRequestState();

  MutableConditionalSubscriptionState conditionalSubscriptionState();

  MutableGlobalListenersState globalListenersState();

  MutableSecretReferenceState secretReferenceState();

  MutableJobMetricsState jobMetricsState(
      final InstantSource clock, final EngineConfiguration config);

  /**
   * Called with every key the engine generates while processing. Keys grow monotonically, so a
   * store can tell from the first key of a transaction which keys the transaction created: no
   * committed row can belong to them.
   */
  default void onKeyGenerated(final long key) {}
}
