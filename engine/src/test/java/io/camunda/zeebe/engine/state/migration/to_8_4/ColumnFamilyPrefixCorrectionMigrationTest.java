/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_8_4;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.message.DbMessageState;
import io.camunda.zeebe.engine.state.migration.MigrationTaskState;
import io.camunda.zeebe.engine.state.migration.MigrationTaskState.State;
import io.camunda.zeebe.engine.state.migration.to_8_4.corrections.ColumnFamily48Corrector;
import io.camunda.zeebe.engine.state.migration.to_8_4.corrections.ColumnFamily49Corrector;
import io.camunda.zeebe.engine.state.migration.to_8_4.corrections.ColumnFamily50Corrector;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.signal.SignalSubscription;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalSubscriptionRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SuppressWarnings("deprecation") // we need to use deprecated column families
public class ColumnFamilyPrefixCorrectionMigrationTest {

  public static final String EXAMPLE_IDENTIFIER =
      new ColumnFamilyPrefixCorrectionMigration().getIdentifier();

  /**
   * Test correction from DEPRECATED_DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION -> MESSAGE_STATS
   */
  @Nested
  @ExtendWith(ProcessingStateExtension.class)
  class ColumnFamily48CorrectorTestTest {
    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private MutableProcessingState processingState;
    private TransactionContext transactionContext;

    private ColumnFamily48Corrector sut;

    private ColumnFamily<DbString, DbLong> wrongMessageStatsColumnFamily;
    private DbString messagesDeadlineCountKey;
    private DbLong messagesDeadlineCount;

    private ColumnFamily<DbString, DbLong> correctMessageStatsColumnFamily;

    private ColumnFamily<DbCompositeKey<DbString, DbInt>, DbLong> correctDecisionColumnFamily;
    private DbString decisionId;
    private DbInt decisionVersion;
    private DbCompositeKey<DbString, DbInt> decisionIdAndVersion;
    private DbLong decisionKey;

    @BeforeEach
    void setup() {
      sut = new ColumnFamily48Corrector(zeebeDb, transactionContext);

      messagesDeadlineCountKey = new DbString();
      messagesDeadlineCountKey.wrapString(DbMessageState.DEADLINE_MESSAGE_COUNT_KEY);
      messagesDeadlineCount = new DbLong();
      wrongMessageStatsColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.DEPRECATED_DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION,
              transactionContext,
              messagesDeadlineCountKey,
              messagesDeadlineCount);

      correctMessageStatsColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.MESSAGE_STATS, transactionContext, new DbString(), new DbLong());

      decisionId = new DbString();
      decisionVersion = new DbInt();
      decisionIdAndVersion = new DbCompositeKey<>(decisionId, decisionVersion);
      decisionKey = new DbLong();
      correctDecisionColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.DEPRECATED_DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION,
              transactionContext,
              decisionIdAndVersion,
              decisionKey);
    }

    @Test
    void shouldMoveMessageStatsToCorrectColumnFamily() {
      // given
      messagesDeadlineCount.wrapLong(123);
      wrongMessageStatsColumnFamily.insert(messagesDeadlineCountKey, messagesDeadlineCount);

      // when
      sut.correctColumnFamilyPrefix();

      // then
      Assertions.assertThat(wrongMessageStatsColumnFamily.isEmpty()).isTrue();
      Assertions.assertThat(correctMessageStatsColumnFamily.get(messagesDeadlineCountKey))
          .isNotNull()
          .extracting(DbLong::getValue)
          .isEqualTo(123L);
    }

    @Test
    void shouldMergeWithCorrectMessageStats() {
      // given
      messagesDeadlineCount.wrapLong(123);
      wrongMessageStatsColumnFamily.insert(messagesDeadlineCountKey, messagesDeadlineCount);
      messagesDeadlineCount.wrapLong(456);
      correctMessageStatsColumnFamily.insert(messagesDeadlineCountKey, messagesDeadlineCount);

      // when
      sut.correctColumnFamilyPrefix();

      // then
      Assertions.assertThat(wrongMessageStatsColumnFamily.isEmpty()).isTrue();
      Assertions.assertThat(correctMessageStatsColumnFamily.get(messagesDeadlineCountKey))
          .isNotNull()
          .extracting(DbLong::getValue)
          .isEqualTo(123L + 456L);
    }

    @Test
    void shouldIgnoreDecisionKeyEntries() {
      // given
      decisionId.wrapString("decision");
      decisionVersion.wrapInt(1);
      decisionKey.wrapLong(123);
      correctDecisionColumnFamily.insert(decisionIdAndVersion, decisionKey);

      messagesDeadlineCount.wrapLong(123);
      wrongMessageStatsColumnFamily.insert(messagesDeadlineCountKey, messagesDeadlineCount);

      decisionId.wrapString("decision2");
      decisionVersion.wrapInt(2);
      decisionKey.wrapLong(234);
      correctDecisionColumnFamily.insert(decisionIdAndVersion, decisionKey);

      Assertions.assertThat(correctDecisionColumnFamily.count()).isEqualTo(3);

      // when
      sut.correctColumnFamilyPrefix();

      // then
      // we can no longer use wrongMessageStatsColumnFamily.isEmpty() as there are entries in there
      // just no longer message stats entries, but we can simply count the entries
      Assertions.assertThat(correctDecisionColumnFamily.count()).isEqualTo(2);

      decisionId.wrapString("decision");
      decisionVersion.wrapInt(1);
      Assertions.assertThat(correctDecisionColumnFamily.get(decisionIdAndVersion))
          .isNotNull()
          .extracting(DbLong::getValue)
          .isEqualTo(123L);

      decisionId.wrapString("decision2");
      decisionVersion.wrapInt(2);
      Assertions.assertThat(correctDecisionColumnFamily.get(decisionIdAndVersion))
          .isNotNull()
          .extracting(DbLong::getValue)
          .isEqualTo(234L);

      Assertions.assertThat(correctMessageStatsColumnFamily.get(messagesDeadlineCountKey))
          .isNotNull()
          .extracting(DbLong::getValue)
          .isEqualTo(123L);
    }
  }

  /**
   * Test correction from
   * DEPRECATED_DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION ->
   * PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY
   */
  @Nested
  @ExtendWith(ProcessingStateExtension.class)
  class ColumnFamily49CorrectorTestTest {
    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private MutableProcessingState processingState;
    private TransactionContext transactionContext;

    private ColumnFamily49Corrector sut;

    private DbLong elementInstanceKey;
    private DbLong processDefinitionKey;
    private DbCompositeKey<DbLong, DbLong> processInstanceKeyByProcessDefinitionKey;

    private ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil> wrongPiKeyByProcDefKeyColumnFamily;
    private ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil>
        correctPiKeyByProcDefKeyColumnFamily;

    private DbString decisionRequirementsId;
    private DbInt decisionRequirementsVersion;
    private DbCompositeKey<DbString, DbInt> decisionRequirementsIdAndVersion;
    private DbLong decisionRequirementsKey;

    private ColumnFamily<DbCompositeKey<DbString, DbInt>, DbLong>
        correctDecisionRequirementsKeyColumnFamily;

    @BeforeEach
    void setup() {
      sut = new ColumnFamily49Corrector(zeebeDb, transactionContext);

      elementInstanceKey = new DbLong();
      processDefinitionKey = new DbLong();
      processInstanceKeyByProcessDefinitionKey =
          new DbCompositeKey<>(processDefinitionKey, elementInstanceKey);
      wrongPiKeyByProcDefKeyColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies
                  .DEPRECATED_DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION,
              transactionContext,
              processInstanceKeyByProcessDefinitionKey,
              DbNil.INSTANCE);

      correctPiKeyByProcDefKeyColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY,
              transactionContext,
              processInstanceKeyByProcessDefinitionKey,
              DbNil.INSTANCE);

      decisionRequirementsId = new DbString();
      decisionRequirementsVersion = new DbInt();
      decisionRequirementsIdAndVersion =
          new DbCompositeKey<>(decisionRequirementsId, decisionRequirementsVersion);
      decisionRequirementsKey = new DbLong();
      correctDecisionRequirementsKeyColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies
                  .DEPRECATED_DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION,
              transactionContext,
              decisionRequirementsIdAndVersion,
              decisionRequirementsKey);
    }

    @Test
    void shouldMovePiKeyByProcDefKeyToCorrectColumnFamily() {
      // given
      elementInstanceKey.wrapLong(123);
      processDefinitionKey.wrapLong(456);
      wrongPiKeyByProcDefKeyColumnFamily.insert(
          processInstanceKeyByProcessDefinitionKey, DbNil.INSTANCE);

      // when
      sut.correctColumnFamilyPrefix();

      // then
      Assertions.assertThat(wrongPiKeyByProcDefKeyColumnFamily.isEmpty()).isTrue();
      Assertions.assertThat(
              correctPiKeyByProcDefKeyColumnFamily.exists(processInstanceKeyByProcessDefinitionKey))
          .isTrue();
    }

    @Test
    void shouldIgnoreProcessInstanceKeyByDefinitionKeyEntries() {
      // given
      decisionRequirementsId.wrapString("decisionRequirements");
      decisionRequirementsVersion.wrapInt(1);
      decisionRequirementsKey.wrapLong(543);
      correctDecisionRequirementsKeyColumnFamily.insert(
          decisionRequirementsIdAndVersion, decisionRequirementsKey);

      elementInstanceKey.wrapLong(123);
      processDefinitionKey.wrapLong(456);
      wrongPiKeyByProcDefKeyColumnFamily.insert(
          processInstanceKeyByProcessDefinitionKey, DbNil.INSTANCE);

      decisionRequirementsId.wrapString("decisionRequirements2");
      decisionRequirementsVersion.wrapInt(2);
      decisionRequirementsKey.wrapLong(987);
      correctDecisionRequirementsKeyColumnFamily.insert(
          decisionRequirementsIdAndVersion, decisionRequirementsKey);

      Assertions.assertThat(correctDecisionRequirementsKeyColumnFamily.count()).isEqualTo(3);

      // when
      sut.correctColumnFamilyPrefix();

      // then
      // we can no longer use wrongPiKeyByProcDefKeyColumnFamily.isEmpty() as there are entries in
      // there just no longer process instance keys by process definition key entries, but we can
      // simply count the entries
      Assertions.assertThat(correctDecisionRequirementsKeyColumnFamily.count()).isEqualTo(2);

      elementInstanceKey.wrapLong(123);
      processDefinitionKey.wrapLong(456);
      Assertions.assertThat(
              correctPiKeyByProcDefKeyColumnFamily.exists(processInstanceKeyByProcessDefinitionKey))
          .isTrue();
    }
  }

  /** Test correction from DEPRECATED_SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY -> MIGRATIONS_STATE */
  @Nested
  @ExtendWith(ProcessingStateExtension.class)
  class ColumnFamily50CorrectorTest {
    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private MutableProcessingState processingState;
    private TransactionContext transactionContext;

    private ColumnFamily50Corrector sut;

    private DbString migrationIdentifier;
    private MigrationTaskState migrationTaskState;

    private ColumnFamily<DbString, MigrationTaskState> wrongMigrationStateColumnFamily;

    private ColumnFamily<DbString, MigrationTaskState> correctMigrationStateColumnFamily;

    private DbLong subscriptionKey;
    private DbString signalName;
    private DbCompositeKey<DbString, DbLong> signalNameAndSubscriptionKey;
    private SignalSubscription signalSubscription;

    private ColumnFamily<DbCompositeKey<DbString, DbLong>, SignalSubscription>
        correctSignalSubscriptionColumnFamily;

    @BeforeEach
    void setup() {
      sut = new ColumnFamily50Corrector(zeebeDb, transactionContext);

      migrationIdentifier = new DbString();
      migrationTaskState = new MigrationTaskState();
      wrongMigrationStateColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY,
              transactionContext,
              migrationIdentifier,
              migrationTaskState);

      correctMigrationStateColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.MIGRATIONS_STATE,
              transactionContext,
              migrationIdentifier,
              migrationTaskState);

      subscriptionKey = new DbLong();
      signalName = new DbString();
      signalNameAndSubscriptionKey = new DbCompositeKey<>(signalName, subscriptionKey);
      signalSubscription = new SignalSubscription();

      correctSignalSubscriptionColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY,
              transactionContext,
              signalNameAndSubscriptionKey,
              signalSubscription);
    }

    @Test
    void shouldMoveMigrationStateToCorrectColumnFamily() {
      // given
      migrationIdentifier.wrapString(EXAMPLE_IDENTIFIER);
      migrationTaskState.setState(State.FINISHED);
      wrongMigrationStateColumnFamily.insert(migrationIdentifier, migrationTaskState);

      // when
      sut.correctColumnFamilyPrefix();

      // then
      Assertions.assertThat(wrongMigrationStateColumnFamily.isEmpty()).isTrue();
      Assertions.assertThat(correctMigrationStateColumnFamily.get(migrationIdentifier))
          .isNotNull()
          .extracting(MigrationTaskState::getState)
          .isEqualTo(State.FINISHED);
    }

    @Test
    void shouldMergeWithCorrectMigrationStateOverwritingWhenFinished() {
      // given
      migrationIdentifier.wrapString(EXAMPLE_IDENTIFIER);
      migrationTaskState.setState(State.FINISHED);
      wrongMigrationStateColumnFamily.insert(migrationIdentifier, migrationTaskState);

      migrationIdentifier.wrapString(EXAMPLE_IDENTIFIER);
      migrationTaskState.setState(State.NOT_STARTED);
      correctMigrationStateColumnFamily.insert(migrationIdentifier, migrationTaskState);

      // when
      sut.correctColumnFamilyPrefix();

      // then
      Assertions.assertThat(wrongMigrationStateColumnFamily.isEmpty()).isTrue();
      Assertions.assertThat(correctMigrationStateColumnFamily.get(migrationIdentifier))
          .isNotNull()
          .extracting(MigrationTaskState::getState)
          .isEqualTo(State.FINISHED);
    }

    @Test
    void shouldMergeWithCorrectMigrationStateNotOverwritingAlreadyFinishedState() {
      // given
      migrationIdentifier.wrapString(EXAMPLE_IDENTIFIER);
      migrationTaskState.setState(State.NOT_STARTED);
      wrongMigrationStateColumnFamily.insert(migrationIdentifier, migrationTaskState);

      migrationIdentifier.wrapString(EXAMPLE_IDENTIFIER);
      migrationTaskState.setState(State.FINISHED);
      correctMigrationStateColumnFamily.insert(migrationIdentifier, migrationTaskState);

      // when
      sut.correctColumnFamilyPrefix();

      // then
      Assertions.assertThat(wrongMigrationStateColumnFamily.isEmpty()).isTrue();
      Assertions.assertThat(correctMigrationStateColumnFamily.get(migrationIdentifier))
          .isNotNull()
          .extracting(MigrationTaskState::getState)
          .isEqualTo(State.FINISHED);
    }

    @Test
    void shouldMergeWithCorrectMigrationStateNotOverwritingDifferentIdentifiers() {
      // given
      migrationIdentifier.wrapString(EXAMPLE_IDENTIFIER);
      migrationTaskState.setState(State.NOT_STARTED);
      wrongMigrationStateColumnFamily.insert(migrationIdentifier, migrationTaskState);

      migrationIdentifier.wrapString(EXAMPLE_IDENTIFIER + "2");
      migrationTaskState.setState(State.FINISHED);
      correctMigrationStateColumnFamily.insert(migrationIdentifier, migrationTaskState);

      // when
      sut.correctColumnFamilyPrefix();

      // then
      Assertions.assertThat(wrongMigrationStateColumnFamily.isEmpty()).isTrue();
      migrationIdentifier.wrapString(EXAMPLE_IDENTIFIER);
      Assertions.assertThat(correctMigrationStateColumnFamily.get(migrationIdentifier))
          .isNotNull()
          .extracting(MigrationTaskState::getState)
          .isEqualTo(State.NOT_STARTED);
      migrationIdentifier.wrapString(EXAMPLE_IDENTIFIER + "2");
      Assertions.assertThat(correctMigrationStateColumnFamily.get(migrationIdentifier))
          .isNotNull()
          .extracting(MigrationTaskState::getState)
          .isEqualTo(State.FINISHED);
    }

    @Test
    void shouldIgnoreSignalSubscriptionEntries() {
      // given
      signalName.wrapString("signal");
      subscriptionKey.wrapLong(123);
      signalSubscription
          .setKey(123)
          .setRecord(new SignalSubscriptionRecord().setSignalName(BufferUtil.wrapString("signal")));
      correctSignalSubscriptionColumnFamily.insert(
          signalNameAndSubscriptionKey, signalSubscription);

      migrationIdentifier.wrapString(EXAMPLE_IDENTIFIER);
      migrationTaskState.setState(State.FINISHED);
      wrongMigrationStateColumnFamily.insert(migrationIdentifier, migrationTaskState);

      signalName.wrapString("signal2");
      subscriptionKey.wrapLong(234);
      signalSubscription
          .setKey(234)
          .setRecord(
              new SignalSubscriptionRecord().setSignalName(BufferUtil.wrapString("signal2")));
      correctSignalSubscriptionColumnFamily.insert(
          signalNameAndSubscriptionKey, signalSubscription);

      Assertions.assertThat(correctSignalSubscriptionColumnFamily.count()).isEqualTo(3);

      // when
      sut.correctColumnFamilyPrefix();

      // then
      // we can no longer use wrongMigrationStateColumnFamily.isEmpty() as there are entries in
      // there
      // just no longer migration state entries, but we can simply count the entries
      Assertions.assertThat(correctSignalSubscriptionColumnFamily.count()).isEqualTo(2);

      signalName.wrapString("signal");
      subscriptionKey.wrapLong(123);
      Assertions.assertThat(correctSignalSubscriptionColumnFamily.get(signalNameAndSubscriptionKey))
          .isNotNull()
          .extracting(SignalSubscription::getKey, s -> s.getRecord().getSignalName())
          .isEqualTo(List.of(123L, "signal"));

      signalName.wrapString("signal2");
      subscriptionKey.wrapLong(234);
      Assertions.assertThat(correctSignalSubscriptionColumnFamily.get(signalNameAndSubscriptionKey))
          .isNotNull()
          .extracting(SignalSubscription::getKey, s -> s.getRecord().getSignalName())
          .isEqualTo(List.of(234L, "signal2"));

      Assertions.assertThat(correctMigrationStateColumnFamily.get(migrationIdentifier))
          .isNotNull()
          .extracting(MigrationTaskState::getState)
          .isEqualTo(State.FINISHED);
    }
  }
}
