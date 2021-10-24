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
import io.camunda.zeebe.engine.processing.streamprocessor.ReadonlyProcessingContext;
import io.camunda.zeebe.engine.state.deployment.DbDeploymentState;
import io.camunda.zeebe.engine.state.deployment.DbProcessState;
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
import io.camunda.zeebe.engine.state.mutable.MutableDeploymentState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableIncidentState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableLastProcessedPositionState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableMigrationState;
import io.camunda.zeebe.engine.state.mutable.MutablePendingMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutablePendingProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableTimerInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.engine.state.processing.DbBlackListState;
import io.camunda.zeebe.engine.state.processing.DbKeyGenerator;
import io.camunda.zeebe.engine.state.processing.DbLastProcessedPositionState;
import io.camunda.zeebe.engine.state.variable.DbVariableState;
import io.camunda.zeebe.protocol.Protocol;
import java.util.function.BiConsumer;

public class ZeebeDbState implements MutableZeebeState {

  private final ZeebeDb<ZbColumnFamilies> zeebeDb;
  private final DbKeyGenerator keyGenerator;

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
  private final MutableLastProcessedPositionState lastProcessedPositionState;
  private final MutableMigrationState mutableMigrationState;

  private final int partitionId;

  public ZeebeDbState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    this(Protocol.DEPLOYMENT_PARTITION, zeebeDb, transactionContext);
  }

  public ZeebeDbState(
      final int partitionId,
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext) {
    this.partitionId = partitionId;
    this.zeebeDb = zeebeDb;
    keyGenerator = new DbKeyGenerator(partitionId, zeebeDb, transactionContext);

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
    lastProcessedPositionState = new DbLastProcessedPositionState(zeebeDb, transactionContext);

    mutableMigrationState = new DbMigrationState(zeebeDb, transactionContext);
  }

  @Override
  public void onRecovered(final ReadonlyProcessingContext context) {
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
  public MutableLastProcessedPositionState getLastProcessedPositionState() {
    return lastProcessedPositionState;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public synchronized boolean isEmpty(final ZbColumnFamilies column) {
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
  public synchronized <KeyType extends DbKey, ValueType extends DbValue> void forEach(
      final ZbColumnFamilies columnFamily,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final BiConsumer<KeyType, ValueType> visitor) {

    final var newContext = zeebeDb.createContext();

    zeebeDb
        .createColumnFamily(columnFamily, newContext, keyInstance, valueInstance)
        .forEach(visitor);
  }

  public KeyGeneratorControls getKeyGeneratorControls() {
    return keyGenerator;
  }
}
