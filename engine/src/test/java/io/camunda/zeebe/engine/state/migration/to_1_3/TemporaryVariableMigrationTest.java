/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_1_3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.immutable.ZeebeState;
import io.camunda.zeebe.engine.state.migration.TemporaryVariableMigration;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.engine.util.ZeebeStateExtension;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

public class TemporaryVariableMigrationTest {

  final TemporaryVariableMigration sutMigration = new TemporaryVariableMigration();

  @Nested
  public class MockBasedTests {

    @Test
    public void noMigrationNeededWhenColumnIsEmpty() {
      // given
      final var mockZeebeState = mock(ZeebeState.class);

      // when
      when(mockZeebeState.isEmpty(ZbColumnFamilies.TEMPORARY_VARIABLE_STORE)).thenReturn(true);
      final var actual = sutMigration.needsToRun(mockZeebeState);

      // then
      assertThat(actual).isFalse();
    }

    @Test
    public void migrationNeededWhenColumnIsNotEmpty() {
      // given
      final var mockZeebeState = mock(ZeebeState.class);

      // when
      when(mockZeebeState.isEmpty(ZbColumnFamilies.TEMPORARY_VARIABLE_STORE)).thenReturn(false);
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
          .migrateTemporaryVariables(mockZeebeState.getEventScopeInstanceState());

      verifyNoMoreInteractions(mockZeebeState.getMigrationState());
    }
  }

  @Nested
  @ExtendWith(ZeebeStateExtension.class)
  public class BlackboxTest {

    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private MutableZeebeState zeebeState;
    private TransactionContext transactionContext;
    private LegacyDbTemporaryVariablesState legacyTemporaryVariablesState;

    @BeforeEach
    public void setUp() {
      // given database with legacy records
      legacyTemporaryVariablesState =
          new LegacyDbTemporaryVariablesState(zeebeDb, transactionContext);
      final DirectBuffer variables = new UnsafeBuffer("variables".getBytes());
      legacyTemporaryVariablesState.put(variables);
    }

    @Test
    public void migrationNeedsToRun() {
      // given database with legacy records

      // when
      final var actual = sutMigration.needsToRun(zeebeState);

      // then
      assertThat(actual).describedAs("Migration should run").isTrue();
      assertThat(legacyTemporaryVariablesState.isEmpty()).isFalse();
    }

    @Test
    public void afterMigrationRunNoFurtherMigrationIsNeeded() {
      // given database with legacy records

      // when
      sutMigration.runMigration(zeebeState);
      final var actual = sutMigration.needsToRun(zeebeState);

      // then
      assertThat(actual).describedAs("Migration should run").isFalse();
      assertThat(legacyTemporaryVariablesState.isEmpty()).isTrue();
    }
  }
}
