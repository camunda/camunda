/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static io.camunda.zeebe.engine.util.RecordToWrite.command;
import static io.camunda.zeebe.engine.util.RecordToWrite.event;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.api.EmptyProcessingResult;
import io.camunda.zeebe.engine.api.RecordProcessor;
import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.engine.util.StreamPlatform;
import io.camunda.zeebe.engine.util.StreamPlatformExtension;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.verification.VerificationWithTimeout;

@ExtendWith(StreamPlatformExtension.class)
final class StreamProcessorMultipleProcessorsTest {

  private static final long TIMEOUT_MILLIS = 2_000L;
  private static final VerificationWithTimeout TIMEOUT = timeout(TIMEOUT_MILLIS);

  private static final ProcessInstanceRecord RECORD = Records.processInstance(1);
  private static final JobRecord JOB_RECORD = Records.job(1);

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  @Test
  void shouldChooseTheRightProcessorToProcess() {
    // given
    final var processInstanceProcessor = createRecordProcessorFor(ValueType.PROCESS_INSTANCE);
    final var jobProcessor = createRecordProcessorFor(ValueType.JOB);

    streamPlatform
        .withRecordProcessors(List.of(processInstanceProcessor, jobProcessor))
        .startStreamProcessor();

    // when
    streamPlatform.writeBatch(command().processInstance(ACTIVATE_ELEMENT, RECORD).causedBy(0));

    // then
    verify(processInstanceProcessor, TIMEOUT).process(matches(ValueType.PROCESS_INSTANCE), any());

    // when
    streamPlatform.writeBatch(command().job(JobIntent.COMPLETE, JOB_RECORD).causedBy(0));

    // then
    verify(jobProcessor, TIMEOUT).process(matches(ValueType.JOB), any());
  }

  private TypedRecord<?> matches(final ValueType processInstance) {
    return Mockito.argThat(record -> record.getValueType().equals(processInstance));
  }

  @Test
  void shouldChooseTheRightProcessorToReplay() {
    // given
    final var processInstanceProcessor = createRecordProcessorFor(ValueType.PROCESS_INSTANCE);
    final var jobProcessor = createRecordProcessorFor(ValueType.JOB);

    // when
    streamPlatform.writeBatch(
        command().processInstance(ACTIVATE_ELEMENT, RECORD),
        event().processInstance(ELEMENT_ACTIVATING, RECORD).causedBy(0));

    streamPlatform
        .withRecordProcessors(List.of(processInstanceProcessor, jobProcessor))
        .startStreamProcessor();

    // then
    verify(processInstanceProcessor, TIMEOUT).replay(matches(ValueType.PROCESS_INSTANCE));
    verify(jobProcessor, never()).replay(any());
  }

  private RecordProcessor createRecordProcessorFor(final ValueType valueType) {
    final RecordProcessor recordProcessor = mock(RecordProcessor.class);
    when(recordProcessor.process(any(), any())).thenReturn(EmptyProcessingResult.INSTANCE);
    when(recordProcessor.onProcessingError(any(), any(), any()))
        .thenReturn(EmptyProcessingResult.INSTANCE);
    when(recordProcessor.accepts(valueType)).thenReturn(true);
    return recordProcessor;
  }
}
