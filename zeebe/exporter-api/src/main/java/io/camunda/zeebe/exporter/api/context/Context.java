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
   * <p>This interface is used in two distinct places:
   *
   * <ul>
   *   <li><b>Broker-level prefiltering</b> (in the Zeebe broker, e.g. {@code ExporterDirector}):
   *       only the {@link #acceptType(RecordType)}, {@link #acceptValue(ValueType)}, and {@link
   *       #acceptIntent(Intent)} methods are used there. The broker combines all exporters' filters
   *       to decide, based on <i>metadata only</i>, which record types/value types/intents are ever
   *       interesting to any exporter. This lets the broker skip deserialization and exporting work
   *       for clearly irrelevant records.
   *   <li><b>Exporter-level filtering</b> (in exporter implementations or test harnesses): the
   *       {@link #acceptRecord(Record)} method is called when the full {@link Record} is available.
   *       Exporters can override this method to apply richer, record-level logic (e.g. broker
   *       version, variable names/values, payload content, etc.).
   * </ul>
   *
   * <p>The default {@link #acceptRecord(Record)} implementation exists for backward compatibility:
   * it simply combines {@code acceptType}, {@code acceptValue}, and {@code acceptIntent} with a
   * logical AND, so existing filters continue to behave as before without any changes.
   */
  interface RecordFilter {

    /**
     * Filters by {@link RecordType}.
     *
     * <p>Used by:
     *
     * <ul>
     *   <li>the broker (e.g. ExporterDirector) to build a global, metadata-only prefilter that
     *       decides which record types are ever exported by any exporter
     *   <li>the default {@link #acceptRecord(Record)} implementation
     * </ul>
     */
    boolean acceptType(final RecordType recordType);

    /**
     * Filters by {@link ValueType}.
     *
     * <p>Used by:
     *
     * <ul>
     *   <li>the broker to skip records whose value type no exporter is interested in
     *   <li>the default {@link #acceptRecord(Record)} implementation
     * </ul>
     */
    boolean acceptValue(final ValueType valueType);

    /**
     * Filters by {@link Intent}.
     *
     * <p>Default is {@code true} for backward compatibility (no intent filtering).
     *
     * <p>Used by:
     *
     * <ul>
     *   <li>the broker to prefilter intents at metadata level
     *   <li>the default {@link #acceptRecord(Record)} implementation
     * </ul>
     */
    default boolean acceptIntent(final Intent intent) {
      return true;
    }

    /**
     * Filters a fully deserialized {@link Record}.
     *
     * <p>Used by:
     *
     * <ul>
     *   <li>exporter runtimes/tests when the full record is available
     *   <li>custom exporters that need richer, record-level logic
     * </ul>
     *
     * <p>Default behavior is:
     *
     * <pre>{@code
     * acceptType(record.getRecordType())
     *     && acceptValue(record.getValueType())
     *     && acceptIntent(record.getIntent());
     * }</pre>
     *
     * which preserves the historic combination of type + value type + intent.
     */
    default boolean acceptRecord(final Record<?> record) {
      return acceptType(record.getRecordType())
          && acceptValue(record.getValueType())
          && acceptIntent(record.getIntent());
    }
  }
}
