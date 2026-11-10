/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.deployment.model.BpmnFactory;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.engine.state.globallistener.MutableGlobalListenersState;
import io.camunda.zeebe.engine.state.immutable.PendingMessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.PendingProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.PendingSubscriptionRecovery;
import io.camunda.zeebe.engine.state.jobmetrics.NoopJobMetricsState;
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
import io.camunda.zeebe.engine.state.mutable.MutableMessageCorrelationState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartProcessInstanceAskState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartProcessInstanceDedupState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableMigrationState;
import io.camunda.zeebe.engine.state.mutable.MutableMultiInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
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
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Objects;
import java.util.function.BiConsumer;

public class ProcessingDbState implements MutableProcessingState {
  private final ZeebeDb<ZbColumnFamilies> zeebeDb;
  private final KeyGenerator keyGenerator;
  private final MutableProcessState processState;
  private final MutableTimerInstanceState timerInstanceState;
  private final MutableElementInstanceState elementInstanceState;
  private final MutableEventScopeInstanceState eventScopeInstanceState;
  private final MutableVariableState variableState;
  private final MutableClusterVariableState clusterVariableState;
  private final MutableAgentHistoryState agentHistoryState;
  private final MutableAgentInstanceState agentInstanceState;
  private final MutableAgentDefinitionState agentDefinitionState;
  private final MutableDeploymentState deploymentState;
  private final MutableJobState jobState;
  private final MutableMessageState messageState;
  private final DbMessageSubscriptionState messageSubscriptionState;
  private final MutableMessageStartEventSubscriptionState messageStartEventSubscriptionState;
  private final DbMessageStartProcessInstanceDedupState messageStartProcessInstanceDedupState;
  private final DbMessageStartProcessInstanceAskState messageStartProcessInstanceAskState;
  private final DbProcessMessageSubscriptionState processMessageSubscriptionState;
  private final DbMessageCorrelationState messageCorrelationState;
  private final MutableIncidentState incidentState;
  private final MutableBannedInstanceState bannedInstanceState;
  private final MutableSuspensionState suspensionState;
  private final MutableMigrationState mutableMigrationState;
  private final MutableDecisionState decisionState;
  private final MutableFormState formState;
  private final MutableResourceState resourceState;
  private final MutableSignalSubscriptionState signalSubscriptionState;
  private final MutableDistributionState distributionState;
  private final MutableUserTaskState userTaskState;
  private final MutableCompensationSubscriptionState compensationSubscriptionState;
  private final MutableUserState userState;
  private final MutableClockState clockState;
  private final MutableAuthorizationState authorizationState;
  private final MutableRoutingState routingState;
  private final MutableTenantState tenantState;
  private final MutableRoleState roleState;
  private final MutableGroupState groupState;
  private final MutableMappingRuleState mappingRuleState;
  private final MutableBatchOperationState batchOperationState;
  private final MutableMembershipState membershipState;
  private final MutableUsageMetricState usageMetricState;
  private final MutableAsyncRequestState asyncRequestState;
  private final MutableMultiInstanceState multiInstanceState;
  private final TransientPendingSubscriptionState transientProcessMessageSubscriptionState;
  private final MutableConditionalSubscriptionState conditionalSubscriptionState;
  private final MutableGlobalListenersState globalListenersState;
  private final MutableJobMetricsState jobMetricsState;
  private final MutableSecretReferenceState secretReferenceState;
  private final int partitionId;
  private final int pendingSubscriptionRecoveryBatchLimit;

  public ProcessingDbState(
      final int partitionId,
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final KeyGenerator keyGenerator,
      final TransientPendingSubscriptionState transientMessageSubscriptionState,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState,
      final TransientPendingMessageStartProcessInstanceAskState transientAskState,
      final EngineConfiguration config,
      final InstantSource clock,
      final ExpressionLanguageMetrics expressionLanguageMetrics) {
    this(
        partitionId,
        zeebeDb,
        new RocksDbStateStores(zeebeDb, transactionContext),
        keyGenerator,
        transientMessageSubscriptionState,
        transientProcessMessageSubscriptionState,
        transientAskState,
        config,
        clock,
        expressionLanguageMetrics);
  }

  public ProcessingDbState(
      final int partitionId,
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final ProcessingStateStores stores,
      final KeyGenerator keyGenerator,
      final TransientPendingSubscriptionState transientMessageSubscriptionState,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState,
      final TransientPendingMessageStartProcessInstanceAskState transientAskState,
      final EngineConfiguration config,
      final InstantSource clock,
      final ExpressionLanguageMetrics expressionLanguageMetrics) {
    this.partitionId = partitionId;
    this.zeebeDb = zeebeDb;
    pendingSubscriptionRecoveryBatchLimit =
        config.getMessagesPendingSubscriptionRecoveryBatchLimit();
    this.keyGenerator = Objects.requireNonNull(keyGenerator);

    variableState = stores.variableState();
    clusterVariableState = stores.clusterVariableState();
    agentHistoryState = stores.agentHistoryState();
    agentInstanceState = stores.agentInstanceState();
    agentDefinitionState = stores.agentDefinitionState();
    // Transformation only parses expressions — the clock is never consulted. A fixed epoch
    // clock satisfies the ExpressionLanguage constructor contract while keeping cache-miss
    // rebuilds deterministic during replay.
    final BpmnTransformer stateTransformer =
        BpmnFactory.createTransformer(
            InstantSource.fixed(Instant.EPOCH), expressionLanguageMetrics, Integer.MAX_VALUE);
    processState = stores.processState(config, stateTransformer);
    timerInstanceState = stores.timerInstanceState();
    elementInstanceState = stores.elementInstanceState(variableState);
    eventScopeInstanceState = stores.eventScopeInstanceState();

    deploymentState = stores.deploymentState();
    jobState = stores.jobState();
    messageState = stores.messageState(partitionId);
    messageSubscriptionState =
        stores.messageSubscriptionState(transientMessageSubscriptionState, clock);
    messageStartEventSubscriptionState = stores.messageStartEventSubscriptionState();
    messageStartProcessInstanceDedupState = stores.messageStartProcessInstanceDedupState();
    messageStartProcessInstanceAskState =
        stores.messageStartProcessInstanceAskState(transientAskState);
    processMessageSubscriptionState =
        stores.processMessageSubscriptionState(transientProcessMessageSubscriptionState, clock);
    messageCorrelationState = stores.messageCorrelationState();
    incidentState = stores.incidentState();
    bannedInstanceState = stores.bannedInstanceState();
    suspensionState = stores.suspensionState();
    decisionState = stores.decisionState(config);
    formState = stores.formState(config);
    resourceState = stores.resourceState(config);
    signalSubscriptionState = stores.signalSubscriptionState();
    distributionState = stores.distributionState();
    mutableMigrationState = stores.migrationState();
    userTaskState = stores.userTaskState();
    compensationSubscriptionState = stores.compensationSubscriptionState();
    userState = stores.userState();
    clockState = stores.clockState();
    authorizationState = stores.authorizationState();
    routingState = stores.routingState();
    roleState = stores.roleState();
    groupState = stores.groupState(config);
    tenantState = stores.tenantState();
    mappingRuleState = stores.mappingRuleState();
    batchOperationState = stores.batchOperationState();
    membershipState = stores.membershipState();
    usageMetricState = stores.usageMetricState();
    multiInstanceState = stores.multiInstanceState();
    asyncRequestState = stores.asyncRequestState();
    conditionalSubscriptionState = stores.conditionalSubscriptionState();
    this.transientProcessMessageSubscriptionState = transientProcessMessageSubscriptionState;
    globalListenersState = stores.globalListenersState();
    secretReferenceState = stores.secretReferenceState();
    if (config.isJobMetricsExportEnabled()) {
      jobMetricsState = stores.jobMetricsState(clock, config);
    } else {
      jobMetricsState = new NoopJobMetricsState();
    }
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    recoverPending(messageSubscriptionState);
    recoverPending(processMessageSubscriptionState);
    bannedInstanceState.onRecovered(context);
    messageState.onRecovered(context);
    messageStartProcessInstanceAskState.onRecovered(context);
    messageStartProcessInstanceDedupState.onRecovered(context);
  }

  private void recoverPending(final PendingSubscriptionRecovery recovery) {
    long next = PendingSubscriptionRecovery.NO_ELEMENT_INSTANCE;
    do {
      next = recovery.recoverPending(next, pendingSubscriptionRecoveryBatchLimit);
    } while (next != PendingSubscriptionRecovery.NO_ELEMENT_INSTANCE);
  }

  @Override
  public MutableDeploymentState getDeploymentState() {
    return deploymentState;
  }

  @Override
  public MutableProcessState getProcessState() {
    return processState;
  }

  @Override
  public MutableJobState getJobState() {
    return jobState;
  }

  @Override
  public MutableMessageState getMessageState() {
    return messageState;
  }

  @Override
  public MutableMessageSubscriptionState getMessageSubscriptionState() {
    return messageSubscriptionState;
  }

  @Override
  public MutableMessageStartEventSubscriptionState getMessageStartEventSubscriptionState() {
    return messageStartEventSubscriptionState;
  }

  @Override
  public MutableMessageStartProcessInstanceDedupState getMessageStartProcessInstanceDedupState() {
    return messageStartProcessInstanceDedupState;
  }

  @Override
  public MutableMessageStartProcessInstanceAskState getMessageStartProcessInstanceAskState() {
    return messageStartProcessInstanceAskState;
  }

  @Override
  public MutableProcessMessageSubscriptionState getProcessMessageSubscriptionState() {
    return processMessageSubscriptionState;
  }

  @Override
  public MutableMessageCorrelationState getMessageCorrelationState() {
    return messageCorrelationState;
  }

  @Override
  public MutableIncidentState getIncidentState() {
    return incidentState;
  }

  @Override
  public MutableBannedInstanceState getBannedInstanceState() {
    return bannedInstanceState;
  }

  @Override
  public MutableSuspensionState getSuspensionState() {
    return suspensionState;
  }

  @Override
  public MutableVariableState getVariableState() {
    return variableState;
  }

  @Override
  public MutableClusterVariableState getClusterVariableState() {
    return clusterVariableState;
  }

  @Override
  public MutableAgentHistoryState getAgentHistoryState() {
    return agentHistoryState;
  }

  @Override
  public MutableAgentInstanceState getAgentInstanceState() {
    return agentInstanceState;
  }

  @Override
  public MutableAgentDefinitionState getAgentDefinitionState() {
    return agentDefinitionState;
  }

  @Override
  public MutableTimerInstanceState getTimerState() {
    return timerInstanceState;
  }

  @Override
  public MutableElementInstanceState getElementInstanceState() {
    return elementInstanceState;
  }

  @Override
  public MutableEventScopeInstanceState getEventScopeInstanceState() {
    return eventScopeInstanceState;
  }

  @Override
  public MutableDecisionState getDecisionState() {
    return decisionState;
  }

  @Override
  public MutableFormState getFormState() {
    return formState;
  }

  @Override
  public MutableResourceState getResourceState() {
    return resourceState;
  }

  @Override
  public MutableSignalSubscriptionState getSignalSubscriptionState() {
    return signalSubscriptionState;
  }

  @Override
  public MutableDistributionState getDistributionState() {
    return distributionState;
  }

  @Override
  public MutableMigrationState getMigrationState() {
    return mutableMigrationState;
  }

  @Override
  public MutableUserTaskState getUserTaskState() {
    return userTaskState;
  }

  @Override
  public MutableCompensationSubscriptionState getCompensationSubscriptionState() {
    return compensationSubscriptionState;
  }

  @Override
  public MutableUserState getUserState() {
    return userState;
  }

  @Override
  public MutableAuthorizationState getAuthorizationState() {
    return authorizationState;
  }

  @Override
  public MutableRoutingState getRoutingState() {
    return routingState;
  }

  @Override
  public MutableClockState getClockState() {
    return clockState;
  }

  @Override
  public MutableRoleState getRoleState() {
    return roleState;
  }

  @Override
  public MutableGroupState getGroupState() {
    return groupState;
  }

  @Override
  public MutableTenantState getTenantState() {
    return tenantState;
  }

  @Override
  public MutableMappingRuleState getMappingRuleState() {
    return mappingRuleState;
  }

  @Override
  public MutableBatchOperationState getBatchOperationState() {
    return batchOperationState;
  }

  @Override
  public MutableMembershipState getMembershipState() {
    return membershipState;
  }

  @Override
  public MutableUsageMetricState getUsageMetricState() {
    return usageMetricState;
  }

  @Override
  public MutableAsyncRequestState getAsyncRequestState() {
    return asyncRequestState;
  }

  @Override
  public MutableMultiInstanceState getMultiInstanceState() {
    return multiInstanceState;
  }

  @Override
  public MutableConditionalSubscriptionState getConditionalSubscriptionState() {
    return conditionalSubscriptionState;
  }

  @Override
  public MutableGlobalListenersState getGlobalListenersState() {
    return globalListenersState;
  }

  @Override
  public MutableJobMetricsState getJobMetricsState() {
    return jobMetricsState;
  }

  @Override
  public MutableSecretReferenceState getSecretReferenceState() {
    return secretReferenceState;
  }

  @Override
  public KeyGenerator getKeyGenerator() {
    return keyGenerator;
  }

  @Override
  public PendingMessageSubscriptionState getPendingMessageSubscriptionState() {
    return messageSubscriptionState;
  }

  @Override
  public PendingProcessMessageSubscriptionState getPendingProcessMessageSubscriptionState() {
    return processMessageSubscriptionState;
  }

  @Override
  public TransientPendingSubscriptionState getTransientPendingSubscriptionState() {
    return transientProcessMessageSubscriptionState;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public boolean isEmpty(final ZbColumnFamilies column) {
    final var newContext = zeebeDb.createContext();
    return zeebeDb.isEmpty(column, newContext);
  }

  /**
   * Iterates over all entries for a given column family and presents each entry to the consumer.
   *
   * <p><strong>Hint</strong> Should only be used in tests.
   *
   * @param columnFamily the enum instance of the column family
   * @param keyInstance this instance defines the type of the column family key type
   * @param valueInstance this instance defines the type of the column family value type
   * @param visitor the visitor that will be called for each entry
   * @param <KeyType> the key type of the column family
   * @param <ValueType> the value type of the column family
   */
  public <KeyType extends DbKey, ValueType extends DbValue> void forEach(
      final ZbColumnFamilies columnFamily,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final BiConsumer<KeyType, ValueType> visitor) {

    final var newContext = zeebeDb.createContext();

    zeebeDb
        .createColumnFamily(columnFamily, newContext, keyInstance, valueInstance)
        .forEach(visitor);
  }
}
