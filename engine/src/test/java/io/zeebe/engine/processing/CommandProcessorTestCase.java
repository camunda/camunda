/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.zeebe.engine.processing.streamprocessor.CommandProcessor.CommandControl;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.util.MockTypedRecord;
import io.zeebe.engine.util.ZeebeStateRule;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import org.junit.ClassRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

/**
 * Abstract test class that command processor implementation can use for their unit tests.
 *
 * <p>Implementations should use {@link org.mockito.junit.MockitoJUnitRunner.StrictStubs} as a
 * runner, or instantiate the mocks themselves.
 */
public abstract class CommandProcessorTestCase<T extends UnifiedRecordValue> {
  @ClassRule public static final ZeebeStateRule ZEEBE_STATE_RULE = new ZeebeStateRule();

  @Mock(name = "CommandControl")
  protected CommandControl<T> controller;

  @Mock(name = "TypedStreamWriter")
  protected TypedStreamWriter streamWriter;

  @Mock(name = "StateWriter")
  protected StateWriter stateWriter;

  @Captor protected ArgumentCaptor<T> acceptedRecordCaptor;

  protected T getAcceptedRecord(final Intent intent) {
    assertAccepted(intent);
    return acceptedRecordCaptor.getValue();
  }

  protected void assertAccepted(final Intent intent, final T record) {
    assertThat(getAcceptedRecord(intent)).isEqualTo(record);
  }

  protected void assertAccepted(final Intent intent) {
    verify(controller, times(1)).accept(eq(intent), acceptedRecordCaptor.capture());
  }

  protected void refuteAccepted() {
    verify(controller, never()).accept(any(), any());
  }

  protected void assertRejected(final RejectionType type) {
    verify(controller, times(1)).reject(eq(type), anyString());
  }

  protected void refuteRejected() {
    verify(controller, never()).reject(any(), anyString());
  }

  protected TypedRecord<T> newCommand(final Class<T> clazz) {
    final RecordMetadata metadata =
        new RecordMetadata()
            .intent(WorkflowInstanceCreationIntent.CREATE)
            .valueType(ValueType.WORKFLOW_INSTANCE_CREATION)
            .recordType(RecordType.COMMAND);
    final T value;

    try {
      value = clazz.newInstance();
    } catch (final InstantiationException | IllegalAccessException e) {
      throw new AssertionError("Failed to create new record", e);
    }

    return new MockTypedRecord<>(-1, metadata, value);
  }

  protected void verifyElementActivatingPublished(
      final long instanceKey, final ElementInstance instance) {
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(instanceKey),
            eq(WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            eq(instance.getValue()));
  }
}
