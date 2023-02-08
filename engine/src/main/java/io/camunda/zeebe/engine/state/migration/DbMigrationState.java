/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableMigrationState;
import io.camunda.zeebe.engine.state.mutable.MutablePendingMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutablePendingProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class DbMigrationState implements MutableMigrationState {

  // ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_SENT_TIME
  // (sentTime, elementInstanceKey, messageName) => \0
  private final DbLong messageSubscriptionSentTime;
  private final DbLong messageSubscriptionElementInstanceKey;
  private final DbString messageSubscriptionMessageName;

  private final DbCompositeKey<DbLong, DbString> messageSubscriptionElementKeyAndMessageName;
  private final DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbString>>
      messageSubscriptionSentTimeCompositeKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbString>>, DbNil>
      messageSubscriptionSentTimeColumnFamily;

  // ZbColumnFamilies.PROCESS_SUBSCRIPTION_BY_SENT_TIME,
  // (sentTime, elementInstanceKey, messageName) => \0
  private final DbLong processSubscriptionSentTime;
  private final DbLong processSubscriptionElementInstanceKey;
  private final DbString processSubscriptionMessageName;

  private final DbCompositeKey<DbLong, DbString> processSubscriptionElementKeyAndMessageName;
  private final DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbString>>
      processSubscriptionSentTimeCompositeKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbString>>, DbNil>
      processSubscriptionSentTimeColumnFamily;

  private final ColumnFamily<DbLong, TemporaryVariables> temporaryVariableColumnFamily;

  private final DbLong dbDecisionKey;
  private final PersistedDecision dbPersistedDecision;
  private final DbString dbDecisionId;
  private final DbForeignKey<DbLong> fkDecision;
  private final DbInt dbDecisionVersion;
  private final ColumnFamily<DbLong, PersistedDecision> decisionsByKeyColumnFamily;
  private final DbCompositeKey<DbString, DbInt> decisionKeyAndVersion;
  private final ColumnFamily<DbCompositeKey<DbString, DbInt>, DbForeignKey<DbLong>>
      decisionKeyByDecisionIdAndVersion;

  public DbMigrationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

    messageSubscriptionElementInstanceKey = new DbLong();
    messageSubscriptionMessageName = new DbString();
    messageSubscriptionElementKeyAndMessageName =
        new DbCompositeKey<>(messageSubscriptionElementInstanceKey, messageSubscriptionMessageName);

    messageSubscriptionSentTime = new DbLong();
    messageSubscriptionSentTimeCompositeKey =
        new DbCompositeKey<>(
            messageSubscriptionSentTime, messageSubscriptionElementKeyAndMessageName);
    messageSubscriptionSentTimeColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_SENT_TIME,
            transactionContext,
            messageSubscriptionSentTimeCompositeKey,
            DbNil.INSTANCE);

    processSubscriptionElementInstanceKey = new DbLong();
    processSubscriptionMessageName = new DbString();
    processSubscriptionElementKeyAndMessageName =
        new DbCompositeKey<>(processSubscriptionElementInstanceKey, processSubscriptionMessageName);

    processSubscriptionSentTime = new DbLong();
    processSubscriptionSentTimeCompositeKey =
        new DbCompositeKey<>(
            processSubscriptionSentTime, processSubscriptionElementKeyAndMessageName);
    processSubscriptionSentTimeColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_SUBSCRIPTION_BY_SENT_TIME,
            transactionContext,
            processSubscriptionSentTimeCompositeKey,
            DbNil.INSTANCE);

    final DbLong temporaryVariablesKeyInstance = new DbLong();
    final TemporaryVariables temporaryVariablesValue = new TemporaryVariables();
    temporaryVariableColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.TEMPORARY_VARIABLE_STORE,
            transactionContext,
            temporaryVariablesKeyInstance,
            temporaryVariablesValue);

    //  ColumnFamilies for decision migration
    dbDecisionKey = new DbLong();
    dbPersistedDecision = new PersistedDecision();
    decisionsByKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_DECISIONS, transactionContext, dbDecisionKey, dbPersistedDecision);
    dbDecisionId = new DbString();
    fkDecision = new DbForeignKey<>(dbDecisionKey, ZbColumnFamilies.DMN_DECISIONS);
    dbDecisionVersion = new DbInt();
    decisionKeyAndVersion = new DbCompositeKey<>(dbDecisionId, dbDecisionVersion);
    decisionKeyByDecisionIdAndVersion =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION,
            transactionContext,
            decisionKeyAndVersion,
            fkDecision);
  }

  @Override
  public void migrateMessageSubscriptionSentTime(
      final MutableMessageSubscriptionState messageSubscriptionState,
      final MutablePendingMessageSubscriptionState transientState) {

    messageSubscriptionSentTimeColumnFamily.forEach(
        (key, value) -> {
          final var sentTime = key.first().getValue();
          final var elementKeyAndMessageName = key.second();
          final var elementInstanceKey = elementKeyAndMessageName.first().getValue();
          final var messageName = elementKeyAndMessageName.second().getBuffer();

          final var messageSubscription =
              messageSubscriptionState.get(elementInstanceKey, messageName);
          if (messageSubscription != null) {
            messageSubscriptionState.updateToCorrelatingState(messageSubscription.getRecord());
            transientState.updateCommandSentTime(messageSubscription.getRecord(), sentTime);
          }

          messageSubscriptionSentTimeColumnFamily.deleteExisting(key);
        });
  }

  @Override
  public void migrateProcessMessageSubscriptionSentTime(
      final MutableProcessMessageSubscriptionState persistentState,
      final MutablePendingProcessMessageSubscriptionState transientState) {

    processSubscriptionSentTimeColumnFamily.forEach(
        (key, value) -> {
          final var sentTime = key.first().getValue();
          final var elementKeyAndMessageName = key.second();
          final var elementInstanceKey = elementKeyAndMessageName.first().getValue();
          final var messageName = elementKeyAndMessageName.second().getBuffer();

          final var processMessageSubscription =
              persistentState.getSubscription(elementInstanceKey, messageName);
          if (processMessageSubscription != null) {

            final var record = processMessageSubscription.getRecord();

            final ProcessMessageSubscriptionRecord exclusiveCopy =
                new ProcessMessageSubscriptionRecord();
            exclusiveCopy.wrap(record);

            if (processMessageSubscription.isOpening()) {
              // explicit call to put(..). This has the desired side-effect that the subscription
              // is added to transient state
              persistentState.updateToOpeningState(exclusiveCopy);
              transientState.updateSentTime(exclusiveCopy, sentTime);
            } else if (processMessageSubscription.isClosing()) {
              // explicit call to updateToClosingState(..). This has the desired side-effect that
              // the subscription is added to transient state
              persistentState.updateToClosingState(exclusiveCopy);
              transientState.updateSentTime(exclusiveCopy, sentTime);
            }
          }

          processSubscriptionSentTimeColumnFamily.deleteExisting(key);
        });
  }

  @Override
  public void migrateTemporaryVariables(
      final MutableEventScopeInstanceState eventScopeInstanceState,
      final MutableElementInstanceState elementInstanceState) {
    temporaryVariableColumnFamily.forEach(
        (key, value) -> {
          // Event key can be a static value. Together with the key this will always be a unique
          // value
          final long eventKey = -1L;
          // Element id will not be used, therefore a dummy id will be generated.
          final String elementId = "migrated-variable-" + key.getValue();
          final DirectBuffer elementIdBuffer = BufferUtil.wrapString(elementId);

          final var elementInstance = elementInstanceState.getInstance(key.getValue());
          if (elementInstance != null
              && elementInstance
                  .getValue()
                  .getBpmnElementType()
                  .equals(BpmnElementType.EVENT_SUB_PROCESS)) {
            final var flowScopeKey = elementInstance.getValue().getFlowScopeKey();
            // We always use triggerStartEvent() here. This is because this method creates an event
            // trigger with the passed parameters without doing any checks beforehand. This is
            // sufficient for this migration.
            eventScopeInstanceState.triggerStartEvent(
                flowScopeKey,
                eventKey,
                elementIdBuffer,
                value.get(),
                elementInstance.getValue().getProcessInstanceKey());
            while (eventScopeInstanceState.pollEventTrigger(key.getValue()) != null) {
              // We don't need to do anything because we want to delete the event trigger, which is
              // what the pollEventTrigger does
            }
          } else {
            // We always use triggerStartEvent() here. This is because this method creates an event
            // trigger with the passed parameters without doing any checks beforehand. This is
            // sufficient for this migration.
            eventScopeInstanceState.triggerStartEvent(
                key.getValue(), eventKey, elementIdBuffer, value.get(), -1L);
          }

          temporaryVariableColumnFamily.deleteExisting(key);
        });
  }

  @Override
  public void migrateDecisionsPopulateDecisionVersionByDecisionIdAndDecisionKey() {
    decisionsByKeyColumnFamily.forEach(
        (key, value) -> {
          dbDecisionId.wrapBuffer(value.getDecisionId());
          dbDecisionKey.wrapLong(value.getDecisionKey());
          dbDecisionVersion.wrapInt(value.getVersion());
          decisionKeyByDecisionIdAndVersion.insert(decisionKeyAndVersion, fkDecision);
        });
  }
}
