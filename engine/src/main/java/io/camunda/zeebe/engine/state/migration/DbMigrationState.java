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
import io.camunda.zeebe.db.impl.DbForeignKey.MatchType;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
import io.camunda.zeebe.engine.state.deployment.PersistedDecisionRequirements;
import io.camunda.zeebe.engine.state.deployment.VersionInfo;
import io.camunda.zeebe.engine.state.immutable.PendingMessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.PendingProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.migration.to_8_3.DbDecisionMigrationState;
import io.camunda.zeebe.engine.state.migration.to_8_3.DbJobMigrationState;
import io.camunda.zeebe.engine.state.migration.to_8_3.DbMessageMigrationState;
import io.camunda.zeebe.engine.state.migration.to_8_3.DbMessageStartEventSubscriptionMigrationState;
import io.camunda.zeebe.engine.state.migration.to_8_3.DbMessageSubscriptionMigrationState;
import io.camunda.zeebe.engine.state.migration.to_8_3.DbProcessMessageSubscriptionMigrationState;
import io.camunda.zeebe.engine.state.migration.to_8_3.DbProcessMigrationState;
import io.camunda.zeebe.engine.state.migration.to_8_4.DbSignalSubscriptionMigrationState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableMigrationState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class DbMigrationState implements MutableMigrationState {

  private static final long NO_PARENT_KEY = -1L;

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

  private final DbLong dbDecisionRequirementsKey;
  private final DbForeignKey<DbLong> fkDecisionRequirements;
  private final PersistedDecisionRequirements dbPersistedDecisionRequirements;
  private final DbInt dbDecisionRequirementsVersion;
  private final DbString dbDecisionRequirementsId;
  private final ColumnFamily<DbLong, PersistedDecisionRequirements>
      decisionRequirementsByKeyColumnFamily;
  private final DbCompositeKey<DbString, DbInt> decisionRequirementsIdAndVersion;
  private final ColumnFamily<DbCompositeKey<DbString, DbInt>, DbForeignKey<DbLong>>
      decisionRequirementsKeyByIdAndVersionColumnFamily;

  private final DbLong elementInstanceKey;
  private final ElementInstance elementInstance;
  private final ColumnFamily<DbLong, ElementInstance> elementInstanceColumnFamily;
  private final DbLong processDefinitionKey;
  private final DbCompositeKey<DbLong, DbLong> processInstanceKeyByProcessDefinitionKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil>
      processInstanceKeyByProcessDefinitionKeyColumnFamily;

  // ZbColumnFamilies.ELEMENT_INSTANCE_PARENT_CHILD
  private final DbForeignKey<DbLong> parentKey;
  private final DbCompositeKey<DbForeignKey<DbLong>, DbForeignKey<DbLong>> parentChildKey;
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbLong>, DbForeignKey<DbLong>>, DbNil>
      parentChildColumnFamily;

  private final DbString processIdKey;
  private final ColumnFamily<DbString, VersionInfo> processVersionInfoColumnFamily;
  private final DbProcessMigrationState processMigrationState;
  private final DbDecisionMigrationState decisionMigrationState;
  private final DbMessageMigrationState messageMigrationState;
  private final DbMessageStartEventSubscriptionMigrationState
      messageStartEventSubscriptionMigrationState;
  private final DbMessageSubscriptionMigrationState messageSubscriptionMigrationState;
  private final DbProcessMessageSubscriptionMigrationState processMessageSubscriptionMigrationState;
  private final DbJobMigrationState jobMigrationState;
  private final DbSignalSubscriptionMigrationState signalSubscriptionMigrationState;

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
            ZbColumnFamilies.DEPRECATED_DMN_DECISIONS,
            transactionContext,
            dbDecisionKey,
            dbPersistedDecision);
    dbDecisionId = new DbString();
    fkDecision = new DbForeignKey<>(dbDecisionKey, ZbColumnFamilies.DEPRECATED_DMN_DECISIONS);
    dbDecisionVersion = new DbInt();
    decisionKeyAndVersion = new DbCompositeKey<>(dbDecisionId, dbDecisionVersion);
    decisionKeyByDecisionIdAndVersion =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPRECATED_DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION,
            transactionContext,
            decisionKeyAndVersion,
            fkDecision);

    //  ColumnFamilies for decision requirements migration
    dbDecisionRequirementsKey = new DbLong();
    fkDecisionRequirements =
        new DbForeignKey<>(
            dbDecisionRequirementsKey, ZbColumnFamilies.DEPRECATED_DMN_DECISION_REQUIREMENTS);
    dbPersistedDecisionRequirements = new PersistedDecisionRequirements();
    decisionRequirementsByKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPRECATED_DMN_DECISION_REQUIREMENTS,
            transactionContext,
            dbDecisionRequirementsKey,
            dbPersistedDecisionRequirements);
    dbDecisionRequirementsVersion = new DbInt();
    dbDecisionRequirementsId = new DbString();
    decisionRequirementsIdAndVersion =
        new DbCompositeKey<>(dbDecisionRequirementsId, dbDecisionRequirementsVersion);
    decisionRequirementsKeyByIdAndVersionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies
                .DEPRECATED_DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION,
            transactionContext,
            decisionRequirementsIdAndVersion,
            fkDecisionRequirements);

    // ColumnFamilies for process instance by process definition migration
    elementInstanceKey = new DbLong();
    elementInstance = new ElementInstance();
    elementInstanceColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ELEMENT_INSTANCE_KEY,
            transactionContext,
            elementInstanceKey,
            elementInstance);
    processDefinitionKey = new DbLong();
    processInstanceKeyByProcessDefinitionKey =
        new DbCompositeKey<>(processDefinitionKey, elementInstanceKey);
    processInstanceKeyByProcessDefinitionKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY,
            transactionContext,
            processInstanceKeyByProcessDefinitionKey,
            DbNil.INSTANCE);

    parentKey =
        new DbForeignKey<>(
            new DbLong(),
            ZbColumnFamilies.ELEMENT_INSTANCE_KEY,
            MatchType.Full,
            (k) -> k.getValue() == -1);
    parentChildKey =
        new DbCompositeKey<>(
            parentKey,
            new DbForeignKey<>(elementInstanceKey, ZbColumnFamilies.ELEMENT_INSTANCE_KEY));
    parentChildColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ELEMENT_INSTANCE_PARENT_CHILD,
            transactionContext,
            parentChildKey,
            DbNil.INSTANCE);

    processIdKey = new DbString();
    processVersionInfoColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPRECATED_PROCESS_VERSION,
            transactionContext,
            processIdKey,
            new VersionInfo());

    processMigrationState = new DbProcessMigrationState(zeebeDb, transactionContext);
    decisionMigrationState = new DbDecisionMigrationState(zeebeDb, transactionContext);
    messageMigrationState = new DbMessageMigrationState(zeebeDb, transactionContext);
    messageStartEventSubscriptionMigrationState =
        new DbMessageStartEventSubscriptionMigrationState(zeebeDb, transactionContext);
    messageSubscriptionMigrationState =
        new DbMessageSubscriptionMigrationState(zeebeDb, transactionContext);
    processMessageSubscriptionMigrationState =
        new DbProcessMessageSubscriptionMigrationState(zeebeDb, transactionContext);
    jobMigrationState = new DbJobMigrationState(zeebeDb, transactionContext);

    signalSubscriptionMigrationState =
        new DbSignalSubscriptionMigrationState(zeebeDb, transactionContext);
  }

  @Override
  public void migrateMessageSubscriptionSentTime(
      final MutableMessageSubscriptionState messageSubscriptionState,
      final PendingMessageSubscriptionState transientState) {

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
            transientState.onSent(
                elementInstanceKey,
                BufferUtil.bufferAsString(messageName),
                TenantOwned.DEFAULT_TENANT_IDENTIFIER,
                sentTime);
          }

          messageSubscriptionSentTimeColumnFamily.deleteExisting(key);
        });
  }

  @Override
  public void migrateProcessMessageSubscriptionSentTime(
      final MutableProcessMessageSubscriptionState persistentState,
      final PendingProcessMessageSubscriptionState transientState) {

    processSubscriptionSentTimeColumnFamily.forEach(
        (key, value) -> {
          final var sentTime = key.first().getValue();
          final var elementKeyAndMessageName = key.second();
          final var elementInstanceKey = elementKeyAndMessageName.first().getValue();
          final var messageName = elementKeyAndMessageName.second().getBuffer();

          final var processMessageSubscription =
              persistentState.getSubscription(
                  elementInstanceKey, messageName, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
          if (processMessageSubscription != null) {

            final var record = processMessageSubscription.getRecord();

            final ProcessMessageSubscriptionRecord exclusiveCopy =
                new ProcessMessageSubscriptionRecord();
            exclusiveCopy.wrap(record);

            if (processMessageSubscription.isOpening()) {
              // explicit call to put(..). This has the desired side-effect that the subscription
              // is added to transient state
              persistentState.updateToOpeningState(exclusiveCopy);
              transientState.onSent(exclusiveCopy, sentTime);
            } else if (processMessageSubscription.isClosing()) {
              // explicit call to updateToClosingState(..). This has the desired side-effect that
              // the subscription is added to transient state
              persistentState.updateToClosingState(exclusiveCopy);
              transientState.onSent(exclusiveCopy, sentTime);
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

  @Override
  public void migrateDrgPopulateDrgVersionByDrgIdAndKey() {
    decisionRequirementsByKeyColumnFamily.forEach(
        (key, value) -> {
          dbDecisionRequirementsId.wrapBuffer(value.getDecisionRequirementsId());
          dbDecisionRequirementsKey.wrapLong(value.getDecisionRequirementsKey());
          dbDecisionRequirementsVersion.wrapInt(value.getDecisionRequirementsVersion());
          decisionRequirementsKeyByIdAndVersionColumnFamily.insert(
              decisionRequirementsIdAndVersion, fkDecisionRequirements);
        });
  }

  @Override
  public void migrateElementInstancePopulateProcessInstanceByDefinitionKey() {
    parentKey.inner().wrapLong(NO_PARENT_KEY);
    parentChildColumnFamily.whileEqualPrefix(
        parentKey,
        (key, nil) -> {
          elementInstanceKey.wrapLong(key.second().inner().getValue());
          final ElementInstance processInstance =
              elementInstanceColumnFamily.get(elementInstanceKey);
          processDefinitionKey.wrapLong(processInstance.getValue().getProcessDefinitionKey());
          processInstanceKeyByProcessDefinitionKeyColumnFamily.upsert(
              processInstanceKeyByProcessDefinitionKey, DbNil.INSTANCE);
        });
  }

  @Override
  public void migrateProcessStateForMultiTenancy() {
    processMigrationState.migrateProcessStateForMultiTenancy();
  }

  @Override
  public void migrateDecisionStateForMultiTenancy() {
    decisionMigrationState.migrateDecisionStateForMultiTenancy();
  }

  @Override
  public void migrateMessageStateForMultiTenancy() {
    messageMigrationState.migrateMessageStateForMultiTenancy();
  }

  @Override
  public void migrateMessageStartEventSubscriptionForMultiTenancy() {
    messageStartEventSubscriptionMigrationState
        .migrateMessageStartEventSubscriptionForMultiTenancy();
  }

  @Override
  public void migrateMessageEventSubscriptionForMultiTenancy() {
    messageSubscriptionMigrationState.migrateMessageSubscriptionForMultiTenancy();
  }

  @Override
  public void migrateProcessMessageSubscriptionForMultiTenancy() {
    processMessageSubscriptionMigrationState.migrateProcessMessageSubscriptionForMultiTenancy();
  }

  @Override
  public void migrateJobStateForMultiTenancy() {
    jobMigrationState.migrateJobStateForMultiTenancy();
  }

  @Override
  public void migrateSignalSubscriptionStateForMultiTenancy() {
    signalSubscriptionMigrationState.migrateSignalSubscriptionStateForMultiTenancy();
  }

  @Override
  public boolean shouldRunElementInstancePopulateProcessInstanceByDefinitionKey() {
    parentKey.inner().wrapLong(NO_PARENT_KEY);
    return processInstanceKeyByProcessDefinitionKeyColumnFamily.isEmpty()
        || processInstanceKeyByProcessDefinitionKeyColumnFamily.count()
            != parentChildColumnFamily.countEqualPrefix(parentKey);
  }
}
