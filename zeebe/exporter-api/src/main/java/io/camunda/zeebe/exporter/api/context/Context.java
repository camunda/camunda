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
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.InstantSource;
import org.slf4j.Logger;

/** Encapsulates context associated with the exporter on open. */
public interface Context {

  MeterRegistry getMeterRegistry();

  /**
   * @return pre-configured logger for this exporter
   */
  Logger getLogger();

  /**
   * A clock that provides the current time. Use this instead of system time to ensure that time is
   * controllable. Especially relevant when comparing the "current" time against record timestamps.
   *
   * <p>This clock is not used by the {@link Controller} to trigger scheduled tasks, these still
   * rely on uncontrollable system time.
   */
  InstantSource clock();

  /**
   * @return configuration for this exporter
   */
  Configuration getConfiguration();

  /**
   * Gets the partition id of the exporter context. During the loading phase, while the
   * configuration for each exporter is being validated, this method will return a null value since
   * on instantiating the Exporter Context, we pass a null partition id, which will get replaced by
   * a valid one at runtime.
   *
   * <p>* @return the partition id for this exporter.
   */
  int getPartitionId();

  /**
   * Apply the given filter to limit the records which are exported.
   *
   * @param filter the filter to apply.
   */
  void setFilter(RecordFilter filter);

  /**
   * A filter to limit the records which are exported.
   *
   * <p>This interface is used in two distinct phases of filtering:
   *
   * <ul>
   *   <li>filtering on metadata to avoid deserialization (faster but limited data to filter on)
   *   <li>filtering on fully deserialized records (slower but richer data to filter on)
   * </ul>
   */
  interface RecordFilter {

    /**
     * Should export records of the given type?
     *
     * @param recordType the type of the record.
     * @return {@code true} if records of this type should be exporter.
     */
    boolean acceptType(RecordType recordType);

    /**
     * Should export records with a value of the given type?
     *
     * @param valueType the type of the record value.
     * @return {@code true} if records with this type of value should be exported.
     */
    boolean acceptValue(ValueType valueType);

    /**
     * Should export records with the given intent?
     *
     * @param intent the intent of the record.
     * @return {@code true} if records with this intent should be exported.
     */
    default boolean acceptIntent(final Intent intent) {
      // default implementation accepts all intents
      return true;
    }

    /**
     * Filters a fully deserialized {@link Record}.
     *
     * <p>Use this when filtering needs access to the complete record data instead of just its
     * metadata. This is less efficient than metadata-based filtering because it runs in the second
     * phase of the filtering pipeline:
     *
     * <ul>
     *   <li>Phase 1: filter on metadata to avoid deserialization (faster but limited information)
     *   <li>Phase 2: filter on fully deserialized records (slower but with richer information)
     * </ul>
     */
    default boolean acceptRecord(final Record<?> record) {
      return true;
    }
  }
}
