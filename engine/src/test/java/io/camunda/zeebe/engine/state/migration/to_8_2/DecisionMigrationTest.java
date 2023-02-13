/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_8_2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.immutable.ZeebeState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.engine.util.ZeebeStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

public class DecisionMigrationTest {

  final DecisionMigration sutMigration = new DecisionMigration();

  @Nested
  public class MockBasedTests {
    @Test
    public void noMigrationNeededWhenDecisionsColumnFamilyIsEmpty() {
      // given
      final var mockZeebeState = mock(ZeebeState.class);

      // when
      when(mockZeebeState.isEmpty(ZbColumnFamilies.DMN_DECISIONS)).thenReturn(true);
      when(mockZeebeState.isEmpty(ZbColumnFamilies.DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION))
          .thenReturn(true);
      final var actual = sutMigration.needsToRun(mockZeebeState);

      // then
      assertThat(actual).isFalse();
    }

    @Test
    public void noMigrationNeededWhenVersionColumnFamilyIsPopulated() {
      // given
      final var mockZeebeState = mock(ZeebeState.class);

      // when
      when(mockZeebeState.isEmpty(ZbColumnFamilies.DMN_DECISIONS)).thenReturn(false);
      when(mockZeebeState.isEmpty(ZbColumnFamilies.DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION))
          .thenReturn(false);
      final var actual = sutMigration.needsToRun(mockZeebeState);

      // then
      assertThat(actual).isFalse();
    }

    @Test
    public void migrationNeededWhenDecisionHaveNotBeenMigratedYet() {
      // given
      final var mockZeebeState = mock(ZeebeState.class);

      // when
      when(mockZeebeState.isEmpty(ZbColumnFamilies.DMN_DECISIONS)).thenReturn(false);
      when(mockZeebeState.isEmpty(ZbColumnFamilies.DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION))
          .thenReturn(true);
      final var actual = sutMigration.needsToRun(mockZeebeState);

      // then
      assertThat(actual).isTrue();
    }

    @Test
    public void migrationCallsMethodInMigrationState() {
      // given
      final var mockZeebeState = mock(MutableZeebeState.class, RETURNS_DEEP_STUBS);

      // when
      sutMigration.runMigration(mockZeebeState);

      // then
      verify(mockZeebeState.getMigrationState())
          .migrateDecisionsPopulateDecisionVersionByDecisionIdAndDecisionKey();

      verifyNoMoreInteractions(mockZeebeState.getMigrationState());
    }
  }

  @Nested
  @ExtendWith(ZeebeStateExtension.class)
  public class BlackboxTest {
    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private MutableZeebeState zeebeState;
    private TransactionContext transactionContext;
    private LegacyDecisionState legacyDecisionState;

    private DbString dbDecisionId;
    private DbLong dbDecisionKey;
    private DbForeignKey<DbLong> fkDecision;
    private DbInt dbDecisionVersion;
    private DbCompositeKey<DbString, DbInt> decisionIdAndVersion;
    private ColumnFamily<DbCompositeKey<DbString, DbInt>, DbForeignKey<DbLong>>
        decisionKeyByDecisionIdAndVersion;

    @BeforeEach
    public void setup() {
      legacyDecisionState = new LegacyDecisionState(zeebeDb, transactionContext);
      dbDecisionKey = new DbLong();
      fkDecision = new DbForeignKey<>(dbDecisionKey, ZbColumnFamilies.DMN_DECISIONS);
      dbDecisionId = new DbString();
      dbDecisionVersion = new DbInt();
      decisionIdAndVersion = new DbCompositeKey<>(dbDecisionId, dbDecisionVersion);
      decisionKeyByDecisionIdAndVersion =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION,
              transactionContext,
              decisionIdAndVersion,
              fkDecision);
    }

    @Test
    public void afterMigrationRunNoFurtherMigrationIsNeeded() {
      // given
      final long key = 123L;
      legacyDecisionState.putDecision(key, sampleDecisionRecord().setDecisionKey(key));

      // when
      sutMigration.runMigration(zeebeState);
      final var shouldRun = sutMigration.needsToRun(zeebeState);

      // then
      assertThat(shouldRun).isFalse();
    }

    @Test
    public void shouldFindCorrectDecisionKeyAfterMigration() {
      // given
      final DecisionRecord decision1 = sampleDecisionRecord().setVersion(1).setDecisionKey(111L);
      final DecisionRecord decision2 = sampleDecisionRecord().setVersion(2).setDecisionKey(222L);
      legacyDecisionState.putDecision(decision1.getDecisionKey(), decision1);
      legacyDecisionState.putDecision(decision2.getDecisionKey(), decision2);

      // when
      sutMigration.runMigration(zeebeState);

      // then
      assertContainsDecision(decision1);
      assertContainsDecision(decision2);
    }

    private DecisionRecord sampleDecisionRecord() {
      return new DecisionRecord()
          .setDecisionId("decision-id")
          .setDecisionName("decision-name")
          .setVersion(1)
          .setDecisionKey(1L)
          .setDecisionRequirementsId("drg-id")
          .setDecisionRequirementsKey(1L);
    }

    private void assertContainsDecision(final DecisionRecord decisionRecord) {
      dbDecisionId.wrapString(decisionRecord.getDecisionId());
      dbDecisionKey.wrapLong(decisionRecord.getDecisionKey());
      dbDecisionVersion.wrapInt(decisionRecord.getVersion());
      assertThat(decisionKeyByDecisionIdAndVersion.exists(decisionIdAndVersion)).isTrue();
      assertThat(decisionKeyByDecisionIdAndVersion.get(decisionIdAndVersion).inner().getValue())
          .isEqualTo(decisionRecord.getDecisionKey());
    }
  }
}
