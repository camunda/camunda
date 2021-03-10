/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state;

import io.zeebe.db.DbKey;
import io.zeebe.db.DbValue;
import io.zeebe.db.TransactionContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.engine.state.deployment.DbDeploymentState;
import io.zeebe.engine.state.deployment.DbProcessState;
import io.zeebe.engine.state.instance.DbElementInstanceState;
import io.zeebe.engine.state.instance.DbEventScopeInstanceState;
import io.zeebe.engine.state.instance.DbIncidentState;
import io.zeebe.engine.state.instance.DbJobState;
import io.zeebe.engine.state.instance.DbTimerInstanceState;
import io.zeebe.engine.state.message.DbMessageStartEventSubscriptionState;
import io.zeebe.engine.state.message.DbMessageState;
import io.zeebe.engine.state.message.DbMessageSubscriptionState;
import io.zeebe.engine.state.message.DbProcessInstanceSubscriptionState;
import io.zeebe.engine.state.mutable.MutableBlackListState;
import io.zeebe.engine.state.mutable.MutableDeploymentState;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.zeebe.engine.state.mutable.MutableIncidentState;
import io.zeebe.engine.state.mutable.MutableJobState;
import io.zeebe.engine.state.mutable.MutableLastProcessedPositionState;
import io.zeebe.engine.state.mutable.MutableMessageStartEventSubscriptionState;
import io.zeebe.engine.state.mutable.MutableMessageState;
import io.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.zeebe.engine.state.mutable.MutableTimerInstanceState;
import io.zeebe.engine.state.mutable.MutableVariableState;
import io.zeebe.engine.state.mutable.MutableProcessInstanceSubscriptionState;
import io.zeebe.engine.state.mutable.MutableProcessState;
import io.zeebe.engine.state.processing.DbBlackListState;
import io.zeebe.engine.state.processing.DbKeyGenerator;
import io.zeebe.engine.state.processing.DbLastProcessedPositionState;
import io.zeebe.engine.state.variable.DbVariableState;
import io.zeebe.protocol.Protocol;
import java.util.function.BiConsumer;

public class ZeebeDbState implements ZeebeState {

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
  private final MutableMessageSubscriptionState messageSubscriptionState;
  private final MutableMessageStartEventSubscriptionState messageStartEventSubscriptionState;
  private final MutableProcessInstanceSubscriptionState processInstanceSubscriptionState;
  private final MutableIncidentState incidentState;
  private final MutableBlackListState blackListState;
  private final MutableLastProcessedPositionState lastProcessedPositionState;

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
    processInstanceSubscriptionState =
        new DbProcessInstanceSubscriptionState(zeebeDb, transactionContext);
    incidentState = new DbIncidentState(zeebeDb, transactionContext, partitionId);
    blackListState = new DbBlackListState(zeebeDb, transactionContext);
    lastProcessedPositionState = new DbLastProcessedPositionState(zeebeDb, transactionContext);
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
  public MutableProcessInstanceSubscriptionState getProcessInstanceSubscriptionState() {
    return processInstanceSubscriptionState;
  }

  @Override
  public MutableIncidentState getIncidentState() {
    return incidentState;
  }

  @Override
  public KeyGenerator getKeyGenerator() {
    return keyGenerator;
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
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public boolean isEmpty(final ZbColumnFamilies column) {
    final var newContext = zeebeDb.createContext();
    return zeebeDb.isEmpty(column, newContext);
  }

  @Override
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

  public KeyGeneratorControls getKeyGeneratorControls() {
    return keyGenerator;
  }

  public MutableLastProcessedPositionState getLastProcessedPositionState() {
    return lastProcessedPositionState;
  }
}
