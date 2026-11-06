/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.streamprocessor.InstanceKeyResolver;
import io.camunda.zeebe.engine.processing.streamprocessor.InstanceKeyResolver.InstanceKeys;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class StorageOrdinalRestoreBehaviorTest {

  private static final int PARTITION_ID = 1;
  private static final long PROCESS_INSTANCE_KEY = Protocol.encodePartitionId(PARTITION_ID, 42L);
  private static final long JOB_KEY = Protocol.encodePartitionId(PARTITION_ID, 7L);
  private static final int ORDINAL = 1234;

  private ElementInstanceState elementInstanceState;
  private StorageOrdinalRestoreBehavior behavior;

  @BeforeEach
  void setUp() {
    elementInstanceState = mock(ElementInstanceState.class);
    behavior = new StorageOrdinalRestoreBehavior(PARTITION_ID, elementInstanceState);
  }

  @Test
  void shouldNotApplyToProcessorThatIsNotAware() {
    // given
    final var value = new JobRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY);
    final var command = command(value);

    // when
    final var applies = behavior.appliesTo(command, plainProcessor());

    // then
    assertThat(applies).isFalse();
    verifyNoInteractions(elementInstanceState);
  }

  @Test
  void shouldNotApplyToCommandThatAlreadyCarriesAnOrdinal() {
    // given
    final var value =
        new JobRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY).setStorageOrdinal(ORDINAL);
    final var command = command(value);
    final var processor = awareProcessor();
    final var keys = new InstanceKeys(PROCESS_INSTANCE_KEY, PROCESS_INSTANCE_KEY);
    processInstanceExists();

    // when
    final var applies = behavior.appliesTo(command, processor);
    behavior.restore(command, processor, keys);

    // then
    assertThat(applies).isFalse();
    verify(elementInstanceState, never()).getInstance(anyLong());
    verify(processor, never()).restoreStorageOrdinal(any(), anyInt());
    assertThat(value.getStorageOrdinal()).isEqualTo(ORDINAL);
  }

  @Test
  void shouldRestoreOrdinalOfTargetProcessInstance() {
    // given
    final var value = new JobRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY);
    final var command = command(value);
    final var processor = awareProcessor();
    final var keys = new InstanceKeys(PROCESS_INSTANCE_KEY, PROCESS_INSTANCE_KEY);
    processInstanceExists();

    // when
    final var applies = behavior.appliesTo(command, processor);
    behavior.restore(command, processor, keys);

    // then
    assertThat(applies).isTrue();
    verify(processor).restoreStorageOrdinal(command, ORDINAL);
    assertThat(value.getStorageOrdinal()).isEqualTo(ORDINAL);
  }

  @Test
  void shouldLeaveOrdinalWhenTargetProcessInstanceIsMissing() {
    // given
    final var value = new JobRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY);
    final var command = command(value);
    final var processor = awareProcessor();
    final var keys = new InstanceKeys(PROCESS_INSTANCE_KEY, PROCESS_INSTANCE_KEY);
    when(elementInstanceState.getInstance(PROCESS_INSTANCE_KEY)).thenReturn(null);

    // when
    final var applies = behavior.appliesTo(command, processor);
    behavior.restore(command, processor, keys);

    // then
    assertThat(applies).isTrue();
    verify(processor, never()).restoreStorageOrdinal(any(), anyInt());
    assertThat(value.getStorageOrdinal()).isZero();
  }

  @Test
  void shouldNotReadInstanceOfAnotherPartition() {
    // given - a message subscription command names an instance that lives on another partition
    final long foreignKey = Protocol.encodePartitionId(PARTITION_ID + 1, 42L);
    final var value = new JobRecord().setProcessInstanceKey(foreignKey);
    final var command = command(value);
    final var processor = awareProcessor();
    final var keys = new InstanceKeys(foreignKey, foreignKey);

    // when
    final var applies = behavior.appliesTo(command, processor);
    behavior.restore(command, processor, keys);

    // then
    assertThat(applies).isTrue();
    verify(elementInstanceState, never()).getInstance(anyLong());
    verify(processor, never()).restoreStorageOrdinal(any(), anyInt());
    assertThat(value.getStorageOrdinal()).isZero();
  }

  @Test
  void shouldRestoreOrdinalFromTheElementInstanceKeyedByTheCommand() {
    // given - a gateway cancel, suspend or resume carries the instance key only as record key
    final var value = new ProcessInstanceRecord();
    final var command = processInstanceCommand(value);
    when(command.getKey()).thenReturn(PROCESS_INSTANCE_KEY);
    final var processor = spy(new AwareProcessInstanceProcessor());
    processInstanceExists();

    // when
    final var applies = behavior.appliesTo(command, processor);
    behavior.restore(command, processor, InstanceKeyResolver.UNRESOLVED);

    // then
    assertThat(applies).isTrue();
    verify(elementInstanceState).getInstance(PROCESS_INSTANCE_KEY);
    verify(processor).restoreStorageOrdinal(command, ORDINAL);
    assertThat(value.getStorageOrdinal()).isEqualTo(ORDINAL);
  }

  @Test
  void shouldNotReadStateWhenJobCommandTargetIsUnresolved() {
    // given - a job command without resolved keys has no record key fallback
    final var value = new JobRecord();
    final var command = command(value);
    when(command.getKey()).thenReturn(JOB_KEY);
    final var processor = awareProcessor();

    // when
    final var applies = behavior.appliesTo(command, processor);
    behavior.restore(command, processor, InstanceKeyResolver.UNRESOLVED);

    // then
    assertThat(applies).isTrue();
    verify(elementInstanceState, never()).getInstance(anyLong());
    verify(processor, never()).restoreStorageOrdinal(any(), anyInt());
    assertThat(value.getStorageOrdinal()).isZero();
  }

  @Test
  void shouldNotRestoreOrdinalFromAnElementInstanceKeyedByAJobCommand() {
    // given - a job command whose bogus key matches an unrelated element instance
    final var value = new JobRecord();
    final var command = command(value);
    when(command.getKey()).thenReturn(PROCESS_INSTANCE_KEY);
    processInstanceExists();
    final var processor = awareProcessor();

    // when
    final var applies = behavior.appliesTo(command, processor);
    behavior.restore(command, processor, InstanceKeyResolver.UNRESOLVED);

    // then
    assertThat(applies).isTrue();
    verify(elementInstanceState, never()).getInstance(anyLong());
    verify(processor, never()).restoreStorageOrdinal(any(), anyInt());
    assertThat(value.getStorageOrdinal()).isZero();
  }

  private void processInstanceExists() {
    final var processInstance = mock(ElementInstance.class);
    when(processInstance.getValue())
        .thenReturn(new ProcessInstanceRecord().setStorageOrdinal(ORDINAL));
    when(elementInstanceState.getInstance(PROCESS_INSTANCE_KEY)).thenReturn(processInstance);
  }

  @SuppressWarnings("unchecked")
  private static TypedRecord<JobRecord> command(final JobRecord value) {
    final TypedRecord<JobRecord> command = mock(TypedRecord.class);
    when(command.getValue()).thenReturn(value);
    when(command.getValueType()).thenReturn(ValueType.JOB);
    return command;
  }

  @SuppressWarnings("unchecked")
  private static TypedRecord<ProcessInstanceRecord> processInstanceCommand(
      final ProcessInstanceRecord value) {
    final TypedRecord<ProcessInstanceRecord> command = mock(TypedRecord.class);
    when(command.getValue()).thenReturn(value);
    when(command.getValueType()).thenReturn(ValueType.PROCESS_INSTANCE);
    return command;
  }

  private static TypedRecordProcessor<JobRecord> plainProcessor() {
    return new TypedRecordProcessor<>() {};
  }

  private static AwareJobProcessor awareProcessor() {
    return spy(new AwareJobProcessor());
  }

  private static final class AwareJobProcessor
      implements TypedRecordProcessor<JobRecord>, StorageOrdinalAware<JobRecord> {

    @Override
    public void restoreStorageOrdinal(
        final TypedRecord<JobRecord> command, final int storageOrdinal) {
      command.getValue().setStorageOrdinal(storageOrdinal);
    }
  }

  private static final class AwareProcessInstanceProcessor
      implements TypedRecordProcessor<ProcessInstanceRecord>,
          StorageOrdinalAware<ProcessInstanceRecord> {

    @Override
    public void restoreStorageOrdinal(
        final TypedRecord<ProcessInstanceRecord> command, final int storageOrdinal) {
      command.getValue().setStorageOrdinal(storageOrdinal);
    }
  }
}
