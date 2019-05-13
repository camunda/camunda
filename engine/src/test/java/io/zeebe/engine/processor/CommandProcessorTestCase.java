/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.WorkflowInstanceCreationIntent;
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
public abstract class CommandProcessorTestCase<T extends UnpackedObject> {
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
