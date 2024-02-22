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
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.DbElementInstanceState;
import io.camunda.zeebe.engine.state.instance.EventTrigger;
import io.camunda.zeebe.engine.state.migration.TemporaryVariableMigration;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.variable.DbVariableState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

public class TemporaryVariableMigrationTest {

  private static final long EVENT_SCOPE_KEY = 100L;
  private static final DirectBuffer VARIABLES = BufferUtil.wrapString("variable");

  final TemporaryVariableMigration sutMigration = new TemporaryVariableMigration();

  @Nested
  public class MockBasedTests {

    @Test
    public void noMigrationNeededWhenColumnIsEmpty() {
      // given
      final var mockProcessingState = mock(ProcessingState.class);

      // when
      when(mockProcessingState.isEmpty(ZbColumnFamilies.TEMPORARY_VARIABLE_STORE)).thenReturn(true);
      final var actual = sutMigration.needsToRun(mockProcessingState);

      // then
      assertThat(actual).isFalse();
    }

    @Test
    public void migrationNeededWhenColumnIsNotEmpty() {
      // given
      final var mockProcessingState = mock(ProcessingState.class);

      // when
      when(mockProcessingState.isEmpty(ZbColumnFamilies.TEMPORARY_VARIABLE_STORE))
          .thenReturn(false);
      final var actual = sutMigration.needsToRun(mockProcessingState);

      // then
      assertThat(actual).isTrue();
    }

    @Test
    public void migrationCallsMethodInMigrationState() {
      // given
      final var mockProcessingState = mock(MutableProcessingState.class, RETURNS_DEEP_STUBS);

      // when
      sutMigration.runMigration(mockProcessingState);

      // then
      verify(mockProcessingState.getMigrationState())
          .migrateTemporaryVariables(
              mockProcessingState.getEventScopeInstanceState(),
              mockProcessingState.getElementInstanceState());

      verifyNoMoreInteractions(mockProcessingState.getMigrationState());
    }
  }

  @Nested
  @ExtendWith(ProcessingStateExtension.class)
  public class BlackboxTest {

    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private MutableProcessingState processingState;
    private TransactionContext transactionContext;
    private LegacyDbTemporaryVariablesState legacyTemporaryVariablesState;
    private DbVariableState variableState;
    private DbElementInstanceState elementInstanceState;

    @BeforeEach
    public void setUp() {
      // given database with legacy records
      legacyTemporaryVariablesState =
          new LegacyDbTemporaryVariablesState(zeebeDb, transactionContext);
      legacyTemporaryVariablesState.put(EVENT_SCOPE_KEY, VARIABLES);
      variableState = new DbVariableState(zeebeDb, transactionContext);
      elementInstanceState = new DbElementInstanceState(zeebeDb, transactionContext, variableState);
    }

    @Test
    public void migrationNeedsToRun() {
      // given database with legacy records

      // when
      final var actual = sutMigration.needsToRun(processingState);

      // then
      assertThat(actual).describedAs("Migration should run").isTrue();
      assertThat(legacyTemporaryVariablesState.isEmpty()).isFalse();
    }

    @Test
    public void afterMigrationRunNoFurtherMigrationIsNeeded() {
      // given database with legacy records

      // when
      sutMigration.runMigration(processingState);
      final var actual = sutMigration.needsToRun(processingState);

      // then
      assertThat(actual).describedAs("Migration should run").isFalse();
      assertThat(legacyTemporaryVariablesState.isEmpty()).isTrue();
      final EventTrigger eventTrigger =
          processingState.getEventScopeInstanceState().peekEventTrigger(EVENT_SCOPE_KEY);
      assertThat(eventTrigger.getVariables()).isEqualTo(VARIABLES);
      assertThat(eventTrigger.getEventKey()).isEqualTo(-1L);
      assertThat(eventTrigger.getElementId())
          .isEqualTo(BufferUtil.wrapString(String.format("migrated-variable-%d", EVENT_SCOPE_KEY)));
    }

    @Test
    public void eventSubProcessGetsMigratedCorrectly() {
      // given
      final long flowScopeKey = 200L;
      final ProcessInstanceRecord processInstanceRecord = new ProcessInstanceRecord();
      processInstanceRecord.setBpmnElementType(BpmnElementType.EVENT_SUB_PROCESS);
      processInstanceRecord.setFlowScopeKey(flowScopeKey);
      elementInstanceState.newInstance(
          EVENT_SCOPE_KEY, processInstanceRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);

      // when
      sutMigration.runMigration(processingState);

      // then
      final EventTrigger oldEventTrigger =
          processingState.getEventScopeInstanceState().peekEventTrigger(EVENT_SCOPE_KEY);
      final EventTrigger newEventTrigger =
          processingState.getEventScopeInstanceState().peekEventTrigger(flowScopeKey);
      assertThat(oldEventTrigger).isNull();
      assertThat(newEventTrigger).isNotNull();
      assertThat(newEventTrigger.getEventKey()).isEqualTo(-1L);
      assertThat(newEventTrigger.getElementId())
          .isEqualTo(BufferUtil.wrapString(String.format("migrated-variable-%d", EVENT_SCOPE_KEY)));
      assertThat(legacyTemporaryVariablesState.isEmpty()).isTrue();
    }
  }
}
