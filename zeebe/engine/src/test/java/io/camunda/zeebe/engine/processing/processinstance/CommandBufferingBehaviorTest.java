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
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The BUFFERED record must carry the ordinal of the suspended instance. The engine entry restores
 * the ordinal on the command value before the suspension gate buffers the command, so the behavior
 * reads the value and never the state.
 */
final class CommandBufferingBehaviorTest {

  private static final long PROCESS_INSTANCE_KEY = 100L;
  private static final long COMMAND_KEY = 200L;
  private static final long BUFFERED_COMMAND_KEY = 300L;
  private static final int COMMAND_ORDINAL = 1234;

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
            keyGenerator, writers, new SuspensionMetrics(new SimpleMeterRegistry()));
  }

  @Test
  void shouldAssignOrdinalOfCommandValue() {
    // given: an external JOB command whose ordinal the engine entry restored
    final var command = new JobRecord().setStorageOrdinal(COMMAND_ORDINAL);

    // when
    behavior.bufferCommand(
        COMMAND_KEY, ValueType.JOB, JobIntent.COMPLETE, command, PROCESS_INSTANCE_KEY);

    // then
    assertThat(bufferedRecord().getStorageOrdinal()).isEqualTo(COMMAND_ORDINAL);
  }

  @Test
  void shouldAssignMainIndexWhenCommandCarriesNoOrdinal() {
    // given: a command value without an ordinal field
    final UnifiedRecordValue command =
        new MessageRecord().setName("ordinal-less").setCorrelationKey("key").setTimeToLive(0L);

    // when
    behavior.bufferCommand(
        COMMAND_KEY, ValueType.MESSAGE, MessageIntent.PUBLISH, command, PROCESS_INSTANCE_KEY);

    // then
    assertThat(bufferedRecord().getStorageOrdinal()).isEqualTo(StorageOrdinalRelated.MAIN_INDEX);
  }

  private BufferedCommandRecord bufferedRecord() {
    final var captor = ArgumentCaptor.forClass(BufferedCommandRecord.class);
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(BUFFERED_COMMAND_KEY), eq(BufferedCommandIntent.BUFFERED), captor.capture());
    return captor.getValue();
  }
}
