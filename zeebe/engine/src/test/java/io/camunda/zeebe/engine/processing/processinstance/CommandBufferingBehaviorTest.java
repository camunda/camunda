/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.metrics.SuspensionMetrics;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The BUFFERED record must carry the ordinal of the suspended instance. External commands arrive
 * with the default 0, so the ordinal comes from the root element instance, and only from the
 * command value when that instance is gone.
 */
final class CommandBufferingBehaviorTest {

  private static final long PROCESS_INSTANCE_KEY = 100L;
  private static final long COMMAND_KEY = 200L;
  private static final long BUFFERED_COMMAND_KEY = 300L;
  private static final int INSTANCE_ORDINAL = 7;
  private static final int COMMAND_ORDINAL = 1234;

  private final ElementInstanceState elementInstanceState = mock(ElementInstanceState.class);
  private final StateWriter stateWriter = mock(StateWriter.class);
  private CommandBufferingBehavior behavior;

  @BeforeEach
  void setUp() {
    final var keyGenerator = mock(KeyGenerator.class);
    when(keyGenerator.nextKey()).thenReturn(BUFFERED_COMMAND_KEY);
    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    behavior =
        new CommandBufferingBehavior(
            keyGenerator,
            writers,
            elementInstanceState,
            new SuspensionMetrics(new SimpleMeterRegistry()));
  }

  @Test
  void shouldAssignOrdinalOfRootElementInstanceWhenCommandCarriesNone() {
    // given: an external JOB command arrives with the default ordinal 0
    rootElementInstanceWithOrdinal(INSTANCE_ORDINAL);

    // when
    behavior.bufferCommand(
        COMMAND_KEY, ValueType.JOB, JobIntent.COMPLETE, new JobRecord(), PROCESS_INSTANCE_KEY);

    // then
    assertThat(bufferedRecord().getStorageOrdinal()).isEqualTo(INSTANCE_ORDINAL);
  }

  @Test
  void shouldAssignOrdinalOfCommandWhenInstanceIsGone() {
    // given: no root element instance, and the command carries its own ordinal
    final var command =
        new ProcessInstanceRecord()
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setStorageOrdinal(COMMAND_ORDINAL);

    // when
    behavior.bufferCommand(
        COMMAND_KEY,
        ValueType.PROCESS_INSTANCE,
        ProcessInstanceIntent.COMPLETE_ELEMENT,
        command,
        PROCESS_INSTANCE_KEY);

    // then
    assertThat(bufferedRecord().getStorageOrdinal()).isEqualTo(COMMAND_ORDINAL);
  }

  @Test
  void shouldAssignMainIndexWhenInstanceIsGoneAndCommandCarriesNoOrdinal() {
    // given: no root element instance, and a command value without an ordinal field
    final UnifiedRecordValue command =
        new MessageRecord().setName("ordinal-less").setCorrelationKey("key").setTimeToLive(0L);

    // when
    behavior.bufferCommand(
        COMMAND_KEY, ValueType.MESSAGE, MessageIntent.PUBLISH, command, PROCESS_INSTANCE_KEY);

    // then
    assertThat(bufferedRecord().getStorageOrdinal()).isEqualTo(StorageOrdinalRelated.MAIN_INDEX);
  }

  private void rootElementInstanceWithOrdinal(final int storageOrdinal) {
    final var root =
        new ElementInstance(
            PROCESS_INSTANCE_KEY,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            new ProcessInstanceRecord()
                .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
                .setStorageOrdinal(storageOrdinal));
    when(elementInstanceState.getInstance(PROCESS_INSTANCE_KEY)).thenReturn(root);
  }

  private BufferedCommandRecord bufferedRecord() {
    final var captor = ArgumentCaptor.forClass(BufferedCommandRecord.class);
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(BUFFERED_COMMAND_KEY), eq(BufferedCommandIntent.BUFFERED), captor.capture());
    return captor.getValue();
  }
}
