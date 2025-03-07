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
package io.camunda.zeebe.exporter.api;

import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;

/**
 * Minimal interface to be implemented by concrete exporters.
 *
 * <p>A concrete implementation should always provide a default, no-arguments constructor. It is not
 * recommended to do anything in the constructor, but rather use the provided callbacks for
 * configuration, setup, resource allocation, etc.
 */
public interface Exporter {
  /**
   * Use the provided configuration at this point to configure your exporter.
   *
   * <p>This method is called in two difference contexts: 1. right before opening the exporter, to
   * configure the exporter 2. at startup, to allow validation of the exporter configuration.
   *
   * <p>To fail-fast at startup time (e.g. database endpoint is missing), for now you must throw an
   * exception.
   *
   * <p>Note that the instance configured at startup will be discarded immediately.
   *
   * @param context the exporter context
   */
  default void configure(final Context context) throws Exception {}

  /**
   * Hook to perform any setup for a given exporter. This method is the first method called during
   * the lifecycle of an exporter, and should be use to create, allocate or configure resources.
   * After this is called, records will be published to this exporter.
   *
   * @param controller specific controller for this exporter
   */
  default void open(final Controller controller) {}

  /**
   * Hook to perform any tear down. This is method is called exactly once at the end of the
   * lifecycle of an exporter, and should be used to close and free any remaining resources.
   */
  default void close() {}

  /**
   * Called at least once for every record to be exported. Once a record is guaranteed to have been
   * exported, implementations should call {@link Controller#updateLastExportedRecordPosition(long)}
   * to signal that this record should not be received here ever again.
   *
   * <p>Should the export method throw an unexpected {@link RuntimeException}, the method will be
   * called indefinitely until it terminates without any exception. It is up to the implementation
   * to handle errors properly, to implement retry strategies, etc.
   *
   * <p>Given Record just wraps the underlying internal buffer. This means if the implementation
   * needs to collect multiple records it either has to call {@link Record#toJson()} to get the
   * serialized version of the record or {@link Record#clone()} to get a deep copy.
   *
   * @param record the record to export
   */
  void export(Record<?> record);

  /**
   * Hook to perform a data purge.
   *
   * <p>When this method is called, the exporter <b>deletes all data</b> that has been exported so
   * far. It is called, when a cluster purge operation is executed.
   *
   * <p>This method should be blocking and only return when all data has been deleted. It may be
   * retried and therefore <i>must</i> be idempotent.
   */
  default void purge() throws Exception {}

  default long getExportedPosition() {
    return -1;
  }
}
