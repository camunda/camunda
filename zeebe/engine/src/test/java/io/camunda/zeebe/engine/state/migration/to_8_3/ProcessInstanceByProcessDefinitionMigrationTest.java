/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_3;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbForeignKey.MatchType;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.migration.MigrationTaskContextImpl;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.stream.impl.ClusterContextImpl;
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

    private final ColumnFamily<DbCompositeKey<DbForeignKey<DbLong>, DbForeignKey<DbLong>>, DbNil>
        parentChildColumnFamily;
    private final DbCompositeKey<DbForeignKey<DbLong>, DbForeignKey<DbLong>> parentChildKey;
    private final DbForeignKey<DbLong> parentKey;

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
    }

    public void insertElementInstance(final long key, final ElementInstance elementInstance) {
      elementInstanceKey.wrapLong(key);
      elementInstanceColumnFamily.insert(elementInstanceKey, elementInstance);
      parentKey.inner().wrapLong(elementInstance.getParentKey());
      parentChildColumnFamily.insert(parentChildKey, DbNil.INSTANCE);
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
          processInstanceKey,
          createElementInstance(processInstanceKey, processDefinitionKey, BpmnElementType.PROCESS));

      // when
      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

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
      final long processInstanceKey = 101L;
      final long processDefinitionKey = 102L;
      legacyState.insertElementInstance(
          processInstanceKey,
          createElementInstance(processInstanceKey, processDefinitionKey, BpmnElementType.PROCESS));
      legacyState.insertElementInstance(
          elementInstanceKey,
          createElementInstance(
              elementInstanceKey, processDefinitionKey, BpmnElementType.START_EVENT));

      // when
      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then
      this.elementInstanceKey.wrapLong(elementInstanceKey);
      this.processDefinitionKey.wrapLong(processDefinitionKey);
      assertThat(
              processInstanceKeyByProcessDefinitionKeyColumnFamily.exists(
                  processInstanceKeyByProcessDefinitionKey))
          .isFalse();
    }

    @Test
    void migrationNeededWhenPIByDefinitionIsEmpty() {
      // given

      // when
      final var actual =
          sut.needsToRun(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then
      assertThat(actual).isTrue();
    }

    @Test
    void noMigrationNeededWhenPIByDefinitionIsNotEmptyAndCountIsEqual() {
      // given
      final long processInstanceKey = 100L;
      final long processDefinitionKey = 101L;

      elementInstanceKey.wrapLong(processInstanceKey);
      this.processDefinitionKey.wrapLong(processDefinitionKey);
      processInstanceKeyByProcessDefinitionKeyColumnFamily.insert(
          processInstanceKeyByProcessDefinitionKey, DbNil.INSTANCE);

      // to make the count equal, we need an entry in both column families
      legacyState.insertElementInstance(
          processInstanceKey,
          createElementInstance(processInstanceKey, processDefinitionKey, BpmnElementType.PROCESS));

      // when
      final var actual =
          sut.needsToRun(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then
      assertThat(actual).isFalse();
    }

    @Test
    void migrationNeededWhenPIByDefinitionIsNotEmptyAndCountIsNotEqual() {
      // given
      final long processInstanceKey = 100L;
      final long processDefinitionKey = 101L;

      elementInstanceKey.wrapLong(processInstanceKey);
      this.processDefinitionKey.wrapLong(processDefinitionKey);
      processInstanceKeyByProcessDefinitionKeyColumnFamily.insert(
          processInstanceKeyByProcessDefinitionKey, DbNil.INSTANCE);

      // to make the count unequal, we need two entries in the old column family
      legacyState.insertElementInstance(
          processInstanceKey,
          createElementInstance(processInstanceKey, processDefinitionKey, BpmnElementType.PROCESS));
      legacyState.insertElementInstance(
          102L, createElementInstance(102L, processDefinitionKey, BpmnElementType.PROCESS));

      // when
      final var actual =
          sut.needsToRun(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then
      assertThat(actual).isTrue();
    }

    private ElementInstance createElementInstance(
        final long elementInstanceKey,
        final long processDefinitionKey,
        final BpmnElementType elementType) {
      final ElementInstance parent =
          elementType == BpmnElementType.PROCESS
              ? null
              : createElementInstance(
                  elementInstanceKey + 1, processDefinitionKey, BpmnElementType.PROCESS);
      final var value =
          new ProcessInstanceRecord()
              .setProcessDefinitionKey(processDefinitionKey)
              .setBpmnElementType(elementType);
      return new ElementInstance(
          elementInstanceKey, parent, ProcessInstanceIntent.ELEMENT_ACTIVATED, value);
    }
  }
}
