/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.test.util.record;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.TimerRecordValue;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;

public class TimerRecordStream extends ExporterRecordStream<TimerRecordValue, TimerRecordStream> {

  public TimerRecordStream(final Stream<Record<TimerRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected TimerRecordStream supply(final Stream<Record<TimerRecordValue>> wrappedStream) {
    return new TimerRecordStream(wrappedStream);
  }

  public TimerRecordStream withElementInstanceKey(final long elementInstanceKey) {
    return valueFilter(v -> v.getElementInstanceKey() == elementInstanceKey);
  }

  public TimerRecordStream withDueDate(final long dueDate) {
    return valueFilter(v -> v.getDueDate() == dueDate);
  }

  public TimerRecordStream withHandlerNodeId(final String handlerNodeId) {
    return valueFilter(v -> v.getHandlerFlowNodeId().equals(handlerNodeId));
  }

  public TimerRecordStream withHandlerNodeId(final DirectBuffer handlerNodeId) {
    return withHandlerNodeId(bufferAsString(handlerNodeId));
  }

  public TimerRecordStream withRepetitions(final int repetitions) {
    return valueFilter(v -> v.getRepetitions() == repetitions);
  }

  public TimerRecordStream withWorkflowKey(final long workflowKey) {
    return valueFilter(v -> v.getWorkflowKey() == workflowKey);
  }

  public TimerRecordStream withWorkflowInstanceKey(final long workflowInstanceKey) {
    return valueFilter(v -> v.getWorkflowInstanceKey() == workflowInstanceKey);
  }
}
