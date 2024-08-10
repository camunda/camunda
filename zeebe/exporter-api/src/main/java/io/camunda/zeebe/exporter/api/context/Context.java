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

import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;

/** Encapsulates context associated with the exporter on open. */
public interface Context {

  MeterRegistry getMeterRegistry();

  /**
   * @return pre-configured logger for this exporter
   */
  Logger getLogger();

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

  /** A filter to limit the records which are exported. */
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
  }
}
