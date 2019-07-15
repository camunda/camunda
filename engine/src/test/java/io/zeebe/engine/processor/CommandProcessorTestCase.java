/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.zeebe.engine.processor.CommandProcessor.CommandControl;
import io.zeebe.engine.util.MockTypedRecord;
import io.zeebe.engine.util.ZeebeStateRule;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
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
  @ClassRule public static ZeebeStateRule zeebeStateRule = new ZeebeStateRule();

  @Mock(name = "CommandControl")
  protected CommandControl<T> controller;

  @Mock(name = "TypedStreamWriter")
  protected TypedStreamWriter streamWriter;

  @Captor protected ArgumentCaptor<T> acceptedRecordCaptor;

  protected T getAcceptedRecord(Intent intent) {
    assertAccepted(intent);
    return acceptedRecordCaptor.getValue();
  }

  protected void assertAccepted(Intent intent, T record) {
    assertThat(getAcceptedRecord(intent)).isEqualTo(record);
  }

  protected void assertAccepted(Intent intent) {
    verify(controller, times(1)).accept(eq(intent), acceptedRecordCaptor.capture());
  }

  protected void refuteAccepted() {
    verify(controller, never()).accept(any(), any());
  }

  protected void assertRejected(RejectionType type) {
    verify(controller, times(1)).reject(eq(type), anyString());
  }

  protected void refuteRejected() {
    verify(controller, never()).reject(any(), anyString());
  }

  protected TypedRecord<T> newCommand(Class<T> clazz) {
    final RecordMetadata metadata =
        new RecordMetadata()
            .intent(WorkflowInstanceCreationIntent.CREATE)
            .valueType(ValueType.WORKFLOW_INSTANCE_CREATION)
            .recordType(RecordType.COMMAND);
    final T value;

    try {
      value = clazz.newInstance();
    } catch (InstantiationException | IllegalAccessException e) {
      throw new AssertionError("Failed to create new record", e);
    }

    return new MockTypedRecord<>(-1, metadata, value);
  }
}
