/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.deployment.DbDecisionState;
import io.camunda.zeebe.engine.state.deployment.DbDeploymentState;
import io.camunda.zeebe.engine.state.deployment.DbProcessState;
import io.camunda.zeebe.engine.state.distribution.DbDistributionState;
import io.camunda.zeebe.engine.state.instance.DbElementInstanceState;
import io.camunda.zeebe.engine.state.instance.DbEventScopeInstanceState;
import io.camunda.zeebe.engine.state.instance.DbIncidentState;
import io.camunda.zeebe.engine.state.instance.DbJobState;
import io.camunda.zeebe.engine.state.instance.DbTimerInstanceState;
import io.camunda.zeebe.engine.state.message.DbMessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.message.DbMessageState;
import io.camunda.zeebe.engine.state.message.DbMessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.DbProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.migration.DbMigrationState;
import io.camunda.zeebe.engine.state.mutable.MutableBlackListState;
import io.camunda.zeebe.engine.state.mutable.MutableDecisionState;
import io.camunda.zeebe.engine.state.mutable.MutableDeploymentState;
import io.camunda.zeebe.engine.state.mutable.MutableDistributionState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableIncidentState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableMigrationState;
import io.camunda.zeebe.engine.state.mutable.MutablePendingMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutablePendingProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableSignalSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableTimerInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.engine.state.processing.DbBlackListState;
import io.camunda.zeebe.engine.state.signal.DbSignalSubscriptionState;
import io.camunda.zeebe.engine.state.variable.DbVariableState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
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
  private final MutableIncidentState incidentState;
  private final MutableBlackListState blackListState;
  private final MutableMigrationState mutableMigrationState;
  private final MutableDecisionState decisionState;
  private final MutableSignalSubscriptionState signalSubscriptionState;
  private final MutableDistributionState distributionState;

  private final int partitionId;

  public ProcessingDbState(
      final int partitionId,
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final KeyGenerator keyGenerator) {
    this.partitionId = partitionId;
    this.zeebeDb = zeebeDb;
    this.keyGenerator = Objects.requireNonNull(keyGenerator);

    variableState = new DbVariableState(zeebeDb, transactionContext);
    processState = new DbProcessState(zeebeDb, transactionContext);
    timerInstanceState = new DbTimerInstanceState(zeebeDb, transactionContext);
    elementInstanceState = new DbElementInstanceState(zeebeDb, transactionContext, variableState);
    eventScopeInstanceState = new DbEventScopeInstanceState(zeebeDb, transactionContext);

    deploymentState = new DbDeploymentState(zeebeDb, transactionContext);
    jobState = new DbJobState(zeebeDb, transactionContext, partitionId);
    messageState = new DbMessageState(zeebeDb, transactionContext);
    messageSubscriptionState = new DbMessageSubscriptionState(zeebeDb, transactionContext);
    messageStartEventSubscriptionState =
        new DbMessageStartEventSubscriptionState(zeebeDb, transactionContext);
    processMessageSubscriptionState =
        new DbProcessMessageSubscriptionState(zeebeDb, transactionContext);
    incidentState = new DbIncidentState(zeebeDb, transactionContext, partitionId);
    blackListState = new DbBlackListState(zeebeDb, transactionContext, partitionId);
    decisionState = new DbDecisionState(zeebeDb, transactionContext);
    signalSubscriptionState = new DbSignalSubscriptionState(zeebeDb, transactionContext);
    distributionState = new DbDistributionState(zeebeDb, transactionContext);

    mutableMigrationState = new DbMigrationState(zeebeDb, transactionContext);
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    messageSubscriptionState.onRecovered(context);
    processMessageSubscriptionState.onRecovered(context);
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
  public MutableIncidentState getIncidentState() {
    return incidentState;
  }

  @Override
  public MutableBlackListState getBlackListState() {
    return blackListState;
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
  public MutablePendingMessageSubscriptionState getPendingMessageSubscriptionState() {
    return messageSubscriptionState;
  }

  @Override
  public MutablePendingProcessMessageSubscriptionState getPendingProcessMessageSubscriptionState() {
    return processMessageSubscriptionState;
  }

  @Override
  public KeyGenerator getKeyGenerator() {
    return keyGenerator;
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
