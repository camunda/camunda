/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_8_2;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
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
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

public class DecisionRequirementsMigrationTest {

  final DecisionRequirementsMigration sutMigration = new DecisionRequirementsMigration();

  @Nested
  public class MockBasedTests {
    @Test
    public void noMigrationNeededWhenDecisionsRequirementsColumnFamilyIsEmpty() {
      // given
      final var mockZeebeState = mock(ZeebeState.class);

      // when
      when(mockZeebeState.isEmpty(ZbColumnFamilies.DMN_DECISION_REQUIREMENTS)).thenReturn(true);
      when(mockZeebeState.isEmpty(
              ZbColumnFamilies
                  .DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION))
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
      when(mockZeebeState.isEmpty(ZbColumnFamilies.DMN_DECISION_REQUIREMENTS)).thenReturn(false);
      when(mockZeebeState.isEmpty(
              ZbColumnFamilies
                  .DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION))
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
      when(mockZeebeState.isEmpty(ZbColumnFamilies.DMN_DECISION_REQUIREMENTS)).thenReturn(false);
      when(mockZeebeState.isEmpty(
              ZbColumnFamilies
                  .DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION))
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
      verify(mockZeebeState.getMigrationState()).migrateDrgPopulateDrgVersionByDrgIdAndKey();

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

    private DbLong dbDecisionRequirementsKey;
    private DbForeignKey<DbLong> fkDecisionRequirements;
    private DbString dbDecisionRequirementsId;
    private DbInt dbDecisionRequirementsVersion;
    private DbCompositeKey<DbString, DbInt> decisionRequirementsIdAndVersion;
    private ColumnFamily<DbCompositeKey<DbString, DbInt>, DbForeignKey<DbLong>>
        decisionRequirementsKeyByIdAndVersion;

    @BeforeEach
    public void setup() {
      legacyDecisionState = new LegacyDecisionState(zeebeDb, transactionContext);
      dbDecisionRequirementsKey = new DbLong();
      fkDecisionRequirements =
          new DbForeignKey<>(dbDecisionRequirementsKey, ZbColumnFamilies.DMN_DECISION_REQUIREMENTS);
      dbDecisionRequirementsId = new DbString();
      dbDecisionRequirementsVersion = new DbInt();
      decisionRequirementsIdAndVersion =
          new DbCompositeKey<>(dbDecisionRequirementsId, dbDecisionRequirementsVersion);
      decisionRequirementsKeyByIdAndVersion =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION,
              transactionContext,
              decisionRequirementsIdAndVersion,
              fkDecisionRequirements);
    }

    @Test
    public void afterMigrationRunNoFurtherMigrationIsNeeded() {
      // given
      final long key = 123L;
      legacyDecisionState.putDecisionRequirements(
          key, sampleDecisionRequirementsRecord().setDecisionRequirementsKey(key));

      // when
      sutMigration.runMigration(zeebeState);
      final var shouldRun = sutMigration.needsToRun(zeebeState);

      // then
      assertThat(shouldRun).isFalse();
    }

    @Test
    public void shouldFindCorrectDrgKeyAfterMigration() {
      // given
      final var drg1 =
          sampleDecisionRequirementsRecord()
              .setDecisionRequirementsVersion(1)
              .setDecisionRequirementsKey(111L);
      final var drg2 =
          sampleDecisionRequirementsRecord()
              .setDecisionRequirementsVersion(2)
              .setDecisionRequirementsKey(222L);
      legacyDecisionState.putDecisionRequirements(drg1.getDecisionRequirementsKey(), drg1);
      legacyDecisionState.putDecisionRequirements(drg2.getDecisionRequirementsKey(), drg2);

      // when
      sutMigration.runMigration(zeebeState);

      // then
      assertContainsDecisionRequirements(drg1);
      assertContainsDecisionRequirements(drg2);
    }

    private DecisionRequirementsRecord sampleDecisionRequirementsRecord() {
      return new DecisionRequirementsRecord()
          .setDecisionRequirementsId("drg-id")
          .setDecisionRequirementsName("drg-name")
          .setDecisionRequirementsVersion(1)
          .setDecisionRequirementsKey(1L)
          .setNamespace("namespace")
          .setResourceName("resource-name")
          .setChecksum(wrapString("checksum"))
          .setResource(wrapString("dmn-resource"));
    }

    private void assertContainsDecisionRequirements(final DecisionRequirementsRecord drgRecord) {
      dbDecisionRequirementsId.wrapString(drgRecord.getDecisionRequirementsId());
      dbDecisionRequirementsKey.wrapLong(drgRecord.getDecisionRequirementsKey());
      dbDecisionRequirementsVersion.wrapInt(drgRecord.getDecisionRequirementsVersion());
      assertThat(decisionRequirementsKeyByIdAndVersion.exists(decisionRequirementsIdAndVersion))
          .isTrue();
      assertThat(
              decisionRequirementsKeyByIdAndVersion
                  .get(decisionRequirementsIdAndVersion)
                  .inner()
                  .getValue())
          .isEqualTo(drgRecord.getDecisionRequirementsKey());
    }
  }
}
