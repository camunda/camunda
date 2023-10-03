/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_8_3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

class ProcessInstanceByProcessDefinitionMigrationTest {

  final ProcessInstanceByProcessDefinitionMigration sut =
      new ProcessInstanceByProcessDefinitionMigration();

  private static final class LegacyElementInstanceState {
    private final DbLong elementInstanceKey;
    private final ElementInstance elementInstance;
    private final ColumnFamily<DbLong, ElementInstance> elementInstanceColumnFamily;

    public LegacyElementInstanceState(
        final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
      elementInstanceKey = new DbLong();
      elementInstance = new ElementInstance();
      elementInstanceColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.ELEMENT_INSTANCE_KEY,
              transactionContext,
              elementInstanceKey,
              elementInstance);
    }

    public void insertElementInstance(final long key, final ElementInstance elementInstance) {
      elementInstanceKey.wrapLong(key);
      elementInstanceColumnFamily.insert(elementInstanceKey, elementInstance);
    }
  }

  @Nested
  class MockBasedTests {
    @Test
    void noMigrationNeededWhenElementInstanceColumnFamilyIsEmptyAndPIByDefinitionIsEmpty() {
      // given
      final var mockProcessingState = mock(ProcessingState.class);
      when(mockProcessingState.isEmpty(ZbColumnFamilies.ELEMENT_INSTANCE_KEY)).thenReturn(true);
      when(mockProcessingState.isEmpty(ZbColumnFamilies.PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY))
          .thenReturn(true);

      // when
      final var actual = sut.needsToRun(mockProcessingState);

      // then
      assertThat(actual).isFalse();
    }

    @Test
    void noMigrationNeededWhenElementInstanceColumnFamilyIsNotEmptyAndPIByDefinitionIsNotEmpty() {
      // given
      final var mockProcessingState = mock(ProcessingState.class);
      when(mockProcessingState.isEmpty(ZbColumnFamilies.ELEMENT_INSTANCE_KEY)).thenReturn(false);
      when(mockProcessingState.isEmpty(ZbColumnFamilies.PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY))
          .thenReturn(false);

      // when
      final var actual = sut.needsToRun(mockProcessingState);

      // then
      assertThat(actual).isFalse();
    }

    @Test
    void noMigrationNeededWhenElementInstanceColumnFamilyIsEmptyAndPIByDefinitionIsNotEmpty() {
      // given
      final var mockProcessingState = mock(ProcessingState.class);
      when(mockProcessingState.isEmpty(ZbColumnFamilies.ELEMENT_INSTANCE_KEY)).thenReturn(true);
      when(mockProcessingState.isEmpty(ZbColumnFamilies.PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY))
          .thenReturn(false);

      // when
      final var actual = sut.needsToRun(mockProcessingState);

      // then
      assertThat(actual).isFalse();
    }

    @Test
    void migrationNeededWhenElementInstanceColumnFamilyIsNotEmptyAndPIByDefinitionIsEmpty() {
      // given
      final var mockProcessingState = mock(ProcessingState.class);
      when(mockProcessingState.isEmpty(ZbColumnFamilies.ELEMENT_INSTANCE_KEY)).thenReturn(false);
      when(mockProcessingState.isEmpty(ZbColumnFamilies.PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY))
          .thenReturn(true);

      // when
      final var actual = sut.needsToRun(mockProcessingState);

      // then
      assertThat(actual).isTrue();
    }
  }

  @Nested
  @ExtendWith(ProcessingStateExtension.class)
  class ProcessInstanceKeyByProcessDefinitionKeyTest {
    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private MutableProcessingState processingState;
    private TransactionContext transactionContext;
    private LegacyElementInstanceState legacyState;
    private DbLong elementInstanceKey;
    private ElementInstance elementInstance;
    private ColumnFamily<DbLong, ElementInstance> elementInstanceColumnFamily;
    private DbLong processDefinitionKey;
    private DbCompositeKey<DbLong, DbLong> processInstanceKeyByProcessDefinitionKey;

    /** [process definition key | process instance key] => [Nil] */
    private ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil>
        processInstanceKeyByProcessDefinitionKeyColumnFamily;

    @BeforeEach
    void setup() {
      legacyState = new LegacyElementInstanceState(zeebeDb, transactionContext);
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
    }

    @Test
    void shouldInsertIntoProcessInstanceByProcessDefinition() {
      // given
      final long processInstanceKey = 100L;
      final long processDefinitionKey = 101L;
      legacyState.insertElementInstance(
          processInstanceKey, createElementInstance(processDefinitionKey, BpmnElementType.PROCESS));

      // when
      sut.runMigration(processingState);

      // then
      elementInstanceKey.wrapLong(processInstanceKey);
      this.processDefinitionKey.wrapLong(processDefinitionKey);
      assertThat(
              processInstanceKeyByProcessDefinitionKeyColumnFamily.exists(
                  processInstanceKeyByProcessDefinitionKey))
          .isTrue();
    }

    @Test
    void shouldNotMigrateElementInstancesOfTypeOtherThanProcess() {
      // given
      final long elementInstanceKey = 100L;
      final long processDefinitionKey = 101L;
      legacyState.insertElementInstance(
          elementInstanceKey,
          createElementInstance(processDefinitionKey, BpmnElementType.START_EVENT));

      // when
      sut.runMigration(processingState);

      // then
      this.elementInstanceKey.wrapLong(elementInstanceKey);
      this.processDefinitionKey.wrapLong(processDefinitionKey);
      assertThat(
              processInstanceKeyByProcessDefinitionKeyColumnFamily.exists(
                  processInstanceKeyByProcessDefinitionKey))
          .isFalse();
    }

    private ElementInstance createElementInstance(
        final long processDefinitionKey, final BpmnElementType elementType) {
      final var elementInstance = new ElementInstance();
      elementInstance.setValue(
          new ProcessInstanceRecord()
              .setProcessDefinitionKey(processDefinitionKey)
              .setBpmnElementType(elementType));
      elementInstance.setState(ProcessInstanceIntent.ELEMENT_ACTIVATED);
      return elementInstance;
    }
  }
}
