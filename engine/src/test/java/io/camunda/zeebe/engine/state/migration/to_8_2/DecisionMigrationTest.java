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

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.immutable.DecisionState;
import io.camunda.zeebe.engine.state.immutable.ZeebeState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.engine.util.ZeebeStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import java.util.Optional;
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
      when(mockZeebeState.isEmpty(ZbColumnFamilies.DMN_DECISION_VERSION_BY_DECISION_ID_AND_KEY))
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
      when(mockZeebeState.isEmpty(ZbColumnFamilies.DMN_DECISION_VERSION_BY_DECISION_ID_AND_KEY))
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
      when(mockZeebeState.isEmpty(ZbColumnFamilies.DMN_DECISION_VERSION_BY_DECISION_ID_AND_KEY))
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
    private DecisionState decisionState;

    @BeforeEach
    public void setup() {
      legacyDecisionState = new LegacyDecisionState(zeebeDb, transactionContext);
      decisionState = zeebeState.getDecisionState();
    }

    @Test
    public void afterMigrationRunNoFurtherMigrationIsNeeded() {
      // given
      final long key = 123L;
      legacyDecisionState.putDecision(key, sampleDecisionRecord());

      // when
      sutMigration.runMigration(zeebeState);
      final var shouldRun = sutMigration.needsToRun(zeebeState);

      // then
      assertThat(shouldRun).isFalse();
    }

    @Test
    public void shouldFindCorrectDecisionKeyAfterMigration() {
      // given
      final DecisionRecord decision1 = sampleDecisionRecord().setVersion(1);
      final DecisionRecord decision2 = sampleDecisionRecord().setVersion(2);
      legacyDecisionState.putDecision(111L, decision1);
      legacyDecisionState.putDecision(222L, decision2);

      // when
      sutMigration.runMigration(zeebeState);

      // then
      final Optional<Long> previousVersionDecisionKey =
          decisionState.findPreviousVersionDecisionKey(wrapString("decision-id"), 2);
      assertThat(previousVersionDecisionKey).isNotEmpty();
      assertThat(previousVersionDecisionKey.get()).isEqualTo(111L);
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
  }
}
