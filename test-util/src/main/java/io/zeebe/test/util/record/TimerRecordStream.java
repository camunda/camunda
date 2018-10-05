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

import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.TimerRecordValue;
import java.util.stream.Stream;

public class TimerRecordStream extends ExporterRecordStream<TimerRecordValue, TimerRecordStream> {

  public TimerRecordStream(final Stream<Record<TimerRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected TimerRecordStream supply(final Stream<Record<TimerRecordValue>> wrappedStream) {
    return new TimerRecordStream(wrappedStream);
  }

  public TimerRecordStream withActivityInstanceId(final long activityInstanceId) {
    return valueFilter(v -> v.getActivityInstanceKey() == activityInstanceId);
  }

  public TimerRecordStream withDueDate(final long dueDate) {
    return valueFilter(v -> v.getDueDate() == dueDate);
  }
}
