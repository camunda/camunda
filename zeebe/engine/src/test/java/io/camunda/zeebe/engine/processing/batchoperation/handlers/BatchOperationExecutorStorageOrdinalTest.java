/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationJobUpdatePlan;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceMigrationPlan;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceModificationPlan;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RecordValue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BatchOperationExecutorStorageOrdinalTest {

  private static final long ITEM_KEY = 11L;
  private static final int STORAGE_ORDINAL = 7;

  private final TypedCommandWriter commandWriter = mock(TypedCommandWriter.class);
  private final BrokerRequestAuthorizationConverter authorizationConverter =
      mock(BrokerRequestAuthorizationConverter.class);
  private final PersistedBatchOperation batchOperation =
      new PersistedBatchOperation().setKey(42L).setStorageOrdinal(1234);

  @Test
  void shouldStampCancelCommand() {
    // given
    final var executor =
        new CancelProcessInstanceBatchOperationExecutor(commandWriter, authorizationConverter);

    // when
    executor.execute(ITEM_KEY, STORAGE_ORDINAL, batchOperation);

    // then
    assertThat(writtenRecord(ProcessInstanceRecord.class).getStorageOrdinal())
        .isEqualTo(STORAGE_ORDINAL);
  }

  @Test
  void shouldStampSuspendCommand() {
    // given
    final var executor =
        new SuspendProcessInstanceBatchOperationExecutor(commandWriter, authorizationConverter);

    // when
    executor.execute(ITEM_KEY, STORAGE_ORDINAL, batchOperation);

    // then
    assertThat(writtenRecord(ProcessInstanceRecord.class).getStorageOrdinal())
        .isEqualTo(STORAGE_ORDINAL);
  }

  @Test
  void shouldStampResumeCommand() {
    // given
    final var executor =
        new ResumeProcessInstanceBatchOperationExecutor(commandWriter, authorizationConverter);

    // when
    executor.execute(ITEM_KEY, STORAGE_ORDINAL, batchOperation);

    // then
    assertThat(writtenRecord(ProcessInstanceRecord.class).getStorageOrdinal())
        .isEqualTo(STORAGE_ORDINAL);
  }

  @Test
  void shouldStampMigrateCommand() {
    // given a migration plan with a target definition and no mapping instructions
    batchOperation.setMigrationPlan(
        new BatchOperationProcessInstanceMigrationPlan().setTargetProcessDefinitionKey(99L));
    final var executor =
        new MigrateProcessInstanceBatchOperationExecutor(
            commandWriter, mock(BatchOperationState.class), authorizationConverter);

    // when
    executor.execute(ITEM_KEY, STORAGE_ORDINAL, batchOperation);

    // then
    assertThat(writtenRecord(ProcessInstanceMigrationRecord.class).getStorageOrdinal())
        .isEqualTo(STORAGE_ORDINAL);
  }

  @Test
  void shouldStampModifyCommand() {
    // given
    batchOperation.setModificationPlan(new BatchOperationProcessInstanceModificationPlan());
    final var executor =
        new ModifyProcessInstanceBatchOperationExecutor(commandWriter, authorizationConverter);

    // when
    executor.execute(ITEM_KEY, STORAGE_ORDINAL, batchOperation);

    // then
    assertThat(writtenRecord(ProcessInstanceModificationRecord.class).getStorageOrdinal())
        .isEqualTo(STORAGE_ORDINAL);
  }

  @Test
  void shouldStampUpdateJobCommand() {
    // given
    batchOperation.setJobUpdatePlan(new BatchOperationJobUpdatePlan().setRetries(3));
    final var executor = new UpdateJobBatchOperationExecutor(commandWriter, authorizationConverter);

    // when
    executor.execute(ITEM_KEY, STORAGE_ORDINAL, batchOperation);

    // then
    assertThat(writtenRecord(JobRecord.class).getStorageOrdinal()).isEqualTo(STORAGE_ORDINAL);
  }

  @Test
  void shouldStampResolveIncidentCommands() {
    // given a job incident so both the job retries update and the incident resolve are written
    final var incidentState = mock(IncidentState.class);
    final var incident = new IncidentRecord().setJobKey(5L);
    when(incidentState.getIncidentRecord(ITEM_KEY)).thenReturn(incident);
    when(incidentState.isJobIncident(incident)).thenReturn(true);
    final var executor =
        new ResolveIncidentBatchOperationExecutor(
            commandWriter, incidentState, authorizationConverter);

    // when
    executor.execute(ITEM_KEY, STORAGE_ORDINAL, batchOperation);

    // then
    final var captor = ArgumentCaptor.forClass(RecordValue.class);
    verify(commandWriter, times(2))
        .appendFollowUpCommand(anyLong(), any(), captor.capture(), any());
    final var written = captor.getAllValues();
    assertThat(((JobRecord) written.get(0)).getStorageOrdinal()).isEqualTo(STORAGE_ORDINAL);
    assertThat(((IncidentRecord) written.get(1)).getStorageOrdinal()).isEqualTo(STORAGE_ORDINAL);
  }

  private <T extends RecordValue> T writtenRecord(final Class<T> type) {
    final var captor = ArgumentCaptor.forClass(RecordValue.class);
    verify(commandWriter).appendFollowUpCommand(eq(ITEM_KEY), any(), captor.capture(), any());
    return type.cast(captor.getValue());
  }
}
