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
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.asyncrequest.DbAsyncRequestState;
import io.camunda.zeebe.engine.state.authorization.DbAuthorizationState;
import io.camunda.zeebe.engine.state.authorization.DbMappingRuleState;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState;
import io.camunda.zeebe.engine.state.authorization.DbRoleState;
import io.camunda.zeebe.engine.state.batchoperation.DbBatchOperationState;
import io.camunda.zeebe.engine.state.clock.DbClockState;
import io.camunda.zeebe.engine.state.compensation.DbCompensationSubscriptionState;
import io.camunda.zeebe.engine.state.deployment.DbDecisionState;
import io.camunda.zeebe.engine.state.deployment.DbDeploymentState;
import io.camunda.zeebe.engine.state.deployment.DbFormState;
import io.camunda.zeebe.engine.state.deployment.DbProcessState;
import io.camunda.zeebe.engine.state.deployment.DbResourceState;
import io.camunda.zeebe.engine.state.distribution.DbDistributionState;
import io.camunda.zeebe.engine.state.group.DbGroupState;
import io.camunda.zeebe.engine.state.immutable.PendingMessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.PendingProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.instance.DbElementInstanceState;
import io.camunda.zeebe.engine.state.instance.DbEventScopeInstanceState;
import io.camunda.zeebe.engine.state.instance.DbIncidentState;
import io.camunda.zeebe.engine.state.instance.DbJobState;
import io.camunda.zeebe.engine.state.instance.DbTimerInstanceState;
import io.camunda.zeebe.engine.state.instance.DbUserTaskState;
import io.camunda.zeebe.engine.state.message.DbMessageCorrelationState;
import io.camunda.zeebe.engine.state.message.DbMessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.message.DbMessageState;
import io.camunda.zeebe.engine.state.message.DbMessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.DbProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.metrics.DbUsageMetricState;
import io.camunda.zeebe.engine.state.migration.DbMigrationState;
import io.camunda.zeebe.engine.state.multiinstance.DbMultiInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableAsyncRequestState;
import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutableBannedInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableBatchOperationState;
import io.camunda.zeebe.engine.state.mutable.MutableClockState;
import io.camunda.zeebe.engine.state.mutable.MutableCompensationSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableDecisionState;
import io.camunda.zeebe.engine.state.mutable.MutableDeploymentState;
import io.camunda.zeebe.engine.state.mutable.MutableDistributionState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableFormState;
import io.camunda.zeebe.engine.state.mutable.MutableGroupState;
import io.camunda.zeebe.engine.state.mutable.MutableIncidentState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableMappingRuleState;
import io.camunda.zeebe.engine.state.mutable.MutableMembershipState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageCorrelationState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartEventSubscriptionState;
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
import io.camunda.zeebe.engine.state.mutable.MutableSignalSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableTenantState;
import io.camunda.zeebe.engine.state.mutable.MutableTimerInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.engine.state.processing.DbBannedInstanceState;
import io.camunda.zeebe.engine.state.routing.DbRoutingState;
import io.camunda.zeebe.engine.state.signal.DbSignalSubscriptionState;
import io.camunda.zeebe.engine.state.tenant.DbTenantState;
import io.camunda.zeebe.engine.state.user.DbUserState;
import io.camunda.zeebe.engine.state.variable.DbVariableState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
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
  private final MutableDeploymentState deploymentState;
  private final MutableJobState jobState;
  private final MutableMessageState messageState;
  private final DbMessageSubscriptionState messageSubscriptionState;
  private final MutableMessageStartEventSubscriptionState messageStartEventSubscriptionState;
  private final DbProcessMessageSubscriptionState processMessageSubscriptionState;
  private final DbMessageCorrelationState messageCorrelationState;
  private final MutableIncidentState incidentState;
  private final MutableBannedInstanceState bannedInstanceState;
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
  private final int partitionId;

  public ProcessingDbState(
      final int partitionId,
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final KeyGenerator keyGenerator,
      final TransientPendingSubscriptionState transientMessageSubscriptionState,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState,
      final EngineConfiguration config,
      final InstantSource clock) {
    this.partitionId = partitionId;
    this.zeebeDb = zeebeDb;
    this.keyGenerator = Objects.requireNonNull(keyGenerator);

    variableState = new DbVariableState(zeebeDb, transactionContext);
    processState = new DbProcessState(zeebeDb, transactionContext, config, clock);
    timerInstanceState = new DbTimerInstanceState(zeebeDb, transactionContext);
    elementInstanceState = new DbElementInstanceState(zeebeDb, transactionContext, variableState);
    eventScopeInstanceState = new DbEventScopeInstanceState(zeebeDb, transactionContext);

    deploymentState = new DbDeploymentState(zeebeDb, transactionContext);
    jobState = new DbJobState(zeebeDb, transactionContext);
    messageState = new DbMessageState(zeebeDb, transactionContext, partitionId);
    messageSubscriptionState =
        new DbMessageSubscriptionState(
            zeebeDb, transactionContext, transientMessageSubscriptionState, clock);
    messageStartEventSubscriptionState =
        new DbMessageStartEventSubscriptionState(zeebeDb, transactionContext);
    processMessageSubscriptionState =
        new DbProcessMessageSubscriptionState(
            zeebeDb, transactionContext, transientProcessMessageSubscriptionState, clock);
    messageCorrelationState = new DbMessageCorrelationState(zeebeDb, transactionContext);
    incidentState = new DbIncidentState(zeebeDb, transactionContext, partitionId);
    bannedInstanceState = new DbBannedInstanceState(zeebeDb, transactionContext);
    decisionState = new DbDecisionState(zeebeDb, transactionContext, config);
    formState = new DbFormState(zeebeDb, transactionContext, config);
    resourceState = new DbResourceState(zeebeDb, transactionContext, config);
    signalSubscriptionState = new DbSignalSubscriptionState(zeebeDb, transactionContext);
    distributionState = new DbDistributionState(zeebeDb, transactionContext);
    mutableMigrationState = new DbMigrationState(zeebeDb, transactionContext);
    userTaskState = new DbUserTaskState(zeebeDb, transactionContext);
    compensationSubscriptionState =
        new DbCompensationSubscriptionState(zeebeDb, transactionContext);
    userState = new DbUserState(zeebeDb, transactionContext);
    clockState = new DbClockState(zeebeDb, transactionContext);
    authorizationState = new DbAuthorizationState(zeebeDb, transactionContext);
    routingState = new DbRoutingState(zeebeDb, transactionContext);
    roleState = new DbRoleState(zeebeDb, transactionContext);
    groupState = new DbGroupState(zeebeDb, transactionContext);
    tenantState = new DbTenantState(zeebeDb, transactionContext);
    mappingRuleState = new DbMappingRuleState(zeebeDb, transactionContext);
    batchOperationState = new DbBatchOperationState(zeebeDb, transactionContext);
    membershipState = new DbMembershipState(zeebeDb, transactionContext);
    usageMetricState =
        new DbUsageMetricState(zeebeDb, transactionContext, config.getUsageMetricsExportInterval());
    multiInstanceState = new DbMultiInstanceState(zeebeDb, transactionContext);
    asyncRequestState = new DbAsyncRequestState(zeebeDb, transactionContext);
    this.transientProcessMessageSubscriptionState = transientProcessMessageSubscriptionState;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    messageSubscriptionState.onRecovered(context);
    processMessageSubscriptionState.onRecovered(context);
    bannedInstanceState.onRecovered(context);
    messageState.onRecovered(context);
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
  public MutableVariableState getVariableState() {
    return variableState;
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
