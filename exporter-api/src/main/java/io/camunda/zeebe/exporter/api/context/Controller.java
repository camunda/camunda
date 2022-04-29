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
package io.camunda.zeebe.exporter.api.context;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;

/** Controls various aspect of the exporting process. */
public interface Controller {
  /**
   * Signals to the broker that the exporter has successfully exported all records up to and
   * including the record at {@param position}.
   *
   * @param position the latest successfully exported record position
   */
  void updateLastExportedRecordPosition(long position);

  /**
   * Schedules a cancellable {@param task} to be ran after {@param delay} has expired.
   *
   * @param delay time to wait until the task is ran
   * @param task the task to run
   * @return cancellable task.
   */
  ScheduledTask scheduleCancellableTask(final Duration delay, final Runnable task);

  /**
   * Serializes the given record to JSON into the given output stream.
   *
   * @param record the record to serialize to JSON
   * @param outputStream the destination output stream
   * @param <T> the type of the record value
   * @throws IOException on serialization error
   */
  <T extends RecordValue> void serializeToJson(
      final Record<T> record, final OutputStream outputStream) throws IOException;
}
