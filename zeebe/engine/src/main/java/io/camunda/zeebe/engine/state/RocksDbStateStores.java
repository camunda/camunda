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
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.engine.state.agentdefinition.DbAgentDefinitionState;
import io.camunda.zeebe.engine.state.agenthistory.DbAgentHistoryState;
import io.camunda.zeebe.engine.state.agentinstance.DbAgentInstanceState;
import io.camunda.zeebe.engine.state.asyncrequest.DbAsyncRequestState;
import io.camunda.zeebe.engine.state.authorization.DbAuthorizationState;
import io.camunda.zeebe.engine.state.authorization.DbMappingRuleState;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState;
import io.camunda.zeebe.engine.state.authorization.DbRoleState;
import io.camunda.zeebe.engine.state.batchoperation.DbBatchOperationState;
import io.camunda.zeebe.engine.state.clock.DbClockState;
import io.camunda.zeebe.engine.state.clustervariable.DbClusterVariableState;
import io.camunda.zeebe.engine.state.compensation.DbCompensationSubscriptionState;
import io.camunda.zeebe.engine.state.conditional.DbConditionalSubscriptionState;
import io.camunda.zeebe.engine.state.deployment.DbDecisionState;
import io.camunda.zeebe.engine.state.deployment.DbDeploymentState;
import io.camunda.zeebe.engine.state.deployment.DbFormState;
import io.camunda.zeebe.engine.state.deployment.DbProcessStore;
import io.camunda.zeebe.engine.state.deployment.DbResourceState;
import io.camunda.zeebe.engine.state.deployment.ProcessStateImpl;
import io.camunda.zeebe.engine.state.distribution.DbDistributionState;
import io.camunda.zeebe.engine.state.globallistener.DbGlobalListenersState;
import io.camunda.zeebe.engine.state.globallistener.MutableGlobalListenersState;
import io.camunda.zeebe.engine.state.group.DbGroupState;
import io.camunda.zeebe.engine.state.instance.DbElementInstanceStore;
import io.camunda.zeebe.engine.state.instance.DbEventScopeInstanceState;
import io.camunda.zeebe.engine.state.instance.DbIncidentState;
import io.camunda.zeebe.engine.state.instance.DbJobState;
import io.camunda.zeebe.engine.state.instance.DbTimerInstanceState;
import io.camunda.zeebe.engine.state.instance.DbUserTaskState;
import io.camunda.zeebe.engine.state.instance.ElementInstanceStateImpl;
import io.camunda.zeebe.engine.state.jobmetrics.DbJobMetricsState;
import io.camunda.zeebe.engine.state.message.DbMessageCorrelationState;
import io.camunda.zeebe.engine.state.message.DbMessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.message.DbMessageStartProcessInstanceAskState;
import io.camunda.zeebe.engine.state.message.DbMessageStartProcessInstanceDedupState;
import io.camunda.zeebe.engine.state.message.DbMessageState;
import io.camunda.zeebe.engine.state.message.DbMessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.DbProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.TransientPendingMessageStartProcessInstanceAskState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.metrics.DbUsageMetricState;
import io.camunda.zeebe.engine.state.migration.DbMigrationState;
import io.camunda.zeebe.engine.state.multiinstance.DbMultiInstanceState;
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
import io.camunda.zeebe.engine.state.processing.DbBannedInstanceState;
import io.camunda.zeebe.engine.state.routing.DbRoutingState;
import io.camunda.zeebe.engine.state.secretreference.DbSecretReferenceState;
import io.camunda.zeebe.engine.state.signal.DbSignalSubscriptionState;
import io.camunda.zeebe.engine.state.suspension.DbSuspensionState;
import io.camunda.zeebe.engine.state.tenant.DbTenantState;
import io.camunda.zeebe.engine.state.user.DbUserState;
import io.camunda.zeebe.engine.state.variable.DbVariableStore;
import io.camunda.zeebe.engine.state.variable.VariableStateImpl;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.time.InstantSource;

/** Stores all engine state in RocksDB. */
public class RocksDbStateStores implements ProcessingStateStores {

  private final ZeebeDb<ZbColumnFamilies> zeebeDb;
  private final TransactionContext transactionContext;

  public RocksDbStateStores(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    this.zeebeDb = zeebeDb;
    this.transactionContext = transactionContext;
  }

  @Override
  public MutableVariableState variableState() {
    return new VariableStateImpl(new DbVariableStore(zeebeDb, transactionContext));
  }

  @Override
  public MutableClusterVariableState clusterVariableState() {
    return new DbClusterVariableState(zeebeDb, transactionContext);
  }

  @Override
  public MutableAgentHistoryState agentHistoryState() {
    return new DbAgentHistoryState(zeebeDb, transactionContext);
  }

  @Override
  public MutableAgentInstanceState agentInstanceState() {
    return new DbAgentInstanceState(zeebeDb, transactionContext);
  }

  @Override
  public MutableAgentDefinitionState agentDefinitionState() {
    return new DbAgentDefinitionState(zeebeDb, transactionContext);
  }

  @Override
  public MutableProcessState processState(
      final EngineConfiguration config, final BpmnTransformer transformer) {
    return new ProcessStateImpl(
        new DbProcessStore(zeebeDb, transactionContext),
        config,
        transformer,
        transactionContext,
        zeebeDb.getMeterRegistry());
  }

  @Override
  public MutableTimerInstanceState timerInstanceState() {
    return new DbTimerInstanceState(zeebeDb, transactionContext);
  }

  @Override
  public MutableElementInstanceState elementInstanceState(
      final MutableVariableState variableState) {
    return new ElementInstanceStateImpl(
        new DbElementInstanceStore(zeebeDb, transactionContext), variableState);
  }

  @Override
  public MutableEventScopeInstanceState eventScopeInstanceState() {
    return new DbEventScopeInstanceState(zeebeDb, transactionContext);
  }

  @Override
  public MutableDeploymentState deploymentState() {
    return new DbDeploymentState(zeebeDb, transactionContext);
  }

  @Override
  public MutableJobState jobState() {
    return new DbJobState(zeebeDb, transactionContext);
  }

  @Override
  public MutableMessageState messageState(final int partitionId) {
    return new DbMessageState(zeebeDb, transactionContext, partitionId);
  }

  @Override
  public DbMessageSubscriptionState messageSubscriptionState(
      final TransientPendingSubscriptionState transientState, final InstantSource clock) {
    return new DbMessageSubscriptionState(zeebeDb, transactionContext, transientState, clock);
  }

  @Override
  public MutableMessageStartEventSubscriptionState messageStartEventSubscriptionState() {
    return new DbMessageStartEventSubscriptionState(zeebeDb, transactionContext);
  }

  @Override
  public DbMessageStartProcessInstanceDedupState messageStartProcessInstanceDedupState() {
    return new DbMessageStartProcessInstanceDedupState(zeebeDb, transactionContext);
  }

  @Override
  public DbMessageStartProcessInstanceAskState messageStartProcessInstanceAskState(
      final TransientPendingMessageStartProcessInstanceAskState transientAskState) {
    return new DbMessageStartProcessInstanceAskState(
        zeebeDb, transactionContext, transientAskState);
  }

  @Override
  public DbProcessMessageSubscriptionState processMessageSubscriptionState(
      final TransientPendingSubscriptionState transientState, final InstantSource clock) {
    return new DbProcessMessageSubscriptionState(
        zeebeDb, transactionContext, transientState, clock);
  }

  @Override
  public DbMessageCorrelationState messageCorrelationState() {
    return new DbMessageCorrelationState(zeebeDb, transactionContext);
  }

  @Override
  public MutableIncidentState incidentState() {
    return new DbIncidentState(zeebeDb, transactionContext);
  }

  @Override
  public MutableBannedInstanceState bannedInstanceState() {
    return new DbBannedInstanceState(zeebeDb, transactionContext);
  }

  @Override
  public MutableSuspensionState suspensionState() {
    return new DbSuspensionState(zeebeDb, transactionContext);
  }

  @Override
  public MutableDecisionState decisionState(final EngineConfiguration config) {
    return new DbDecisionState(zeebeDb, transactionContext, config);
  }

  @Override
  public MutableFormState formState(final EngineConfiguration config) {
    return new DbFormState(zeebeDb, transactionContext, config);
  }

  @Override
  public MutableResourceState resourceState(final EngineConfiguration config) {
    return new DbResourceState(zeebeDb, transactionContext, config);
  }

  @Override
  public MutableSignalSubscriptionState signalSubscriptionState() {
    return new DbSignalSubscriptionState(zeebeDb, transactionContext);
  }

  @Override
  public MutableDistributionState distributionState() {
    return new DbDistributionState(zeebeDb, transactionContext);
  }

  @Override
  public MutableMigrationState migrationState() {
    return new DbMigrationState(zeebeDb, transactionContext);
  }

  @Override
  public MutableUserTaskState userTaskState() {
    return new DbUserTaskState(zeebeDb, transactionContext);
  }

  @Override
  public MutableCompensationSubscriptionState compensationSubscriptionState() {
    return new DbCompensationSubscriptionState(zeebeDb, transactionContext);
  }

  @Override
  public MutableUserState userState() {
    return new DbUserState(zeebeDb, transactionContext);
  }

  @Override
  public MutableClockState clockState() {
    return new DbClockState(zeebeDb, transactionContext);
  }

  @Override
  public MutableAuthorizationState authorizationState() {
    return new DbAuthorizationState(zeebeDb, transactionContext);
  }

  @Override
  public MutableRoutingState routingState() {
    return new DbRoutingState(zeebeDb, transactionContext);
  }

  @Override
  public MutableRoleState roleState() {
    return new DbRoleState(zeebeDb, transactionContext);
  }

  @Override
  public MutableGroupState groupState(final EngineConfiguration config) {
    return new DbGroupState(zeebeDb, transactionContext, config);
  }

  @Override
  public MutableTenantState tenantState() {
    return new DbTenantState(zeebeDb, transactionContext);
  }

  @Override
  public MutableMappingRuleState mappingRuleState() {
    return new DbMappingRuleState(zeebeDb, transactionContext);
  }

  @Override
  public MutableBatchOperationState batchOperationState() {
    return new DbBatchOperationState(zeebeDb, transactionContext);
  }

  @Override
  public MutableMembershipState membershipState() {
    return new DbMembershipState(zeebeDb, transactionContext);
  }

  @Override
  public MutableUsageMetricState usageMetricState() {
    return new DbUsageMetricState(zeebeDb, transactionContext);
  }

  @Override
  public MutableMultiInstanceState multiInstanceState() {
    return new DbMultiInstanceState(zeebeDb, transactionContext);
  }

  @Override
  public MutableAsyncRequestState asyncRequestState() {
    return new DbAsyncRequestState(zeebeDb, transactionContext);
  }

  @Override
  public MutableConditionalSubscriptionState conditionalSubscriptionState() {
    return new DbConditionalSubscriptionState(zeebeDb, transactionContext);
  }

  @Override
  public MutableGlobalListenersState globalListenersState() {
    return new DbGlobalListenersState(zeebeDb, transactionContext);
  }

  @Override
  public MutableSecretReferenceState secretReferenceState() {
    return new DbSecretReferenceState(zeebeDb, transactionContext);
  }

  @Override
  public MutableJobMetricsState jobMetricsState(
      final InstantSource clock, final EngineConfiguration config) {
    return new DbJobMetricsState(zeebeDb, transactionContext, clock, config);
  }
}
