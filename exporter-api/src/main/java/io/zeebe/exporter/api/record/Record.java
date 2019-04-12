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
package io.zeebe.exporter.api.record;

import java.time.Instant;

/** Represents a record published to the log stream. */
public interface Record<T extends RecordValue> {
  /**
   * Retrieves the position of the record. Positions are locally unique to the partition, and
   * monotonically increasing. Records are then ordered on the partition by their positions, i.e.
   * lower position means the record was published earlier.
   *
   * @return position the record
   */
  long getPosition();

  /**
   * Returns the position of the source record. The source record denotes the record which caused
   * the current record. It can be unset (meaning there is no source record), at which point the
   * position returned here will be -1. Anything >= 0 implies a source record.
   *
   * @return position of the source record
   */
  long getSourceRecordPosition();

  /** @return the id of the producer which produced this event */
  int getProducerId();

  /**
   * Retrieves the key of the record.
   *
   * <p>Multiple records can have the same key if they belongs to the same logical entity. Keys are
   * unique for the combination of partition and record type.
   *
   * @return the key of the record
   */
  long getKey();

  /**
   * Returns the instant at which the record was published on the partition.
   *
   * @return timestamp of the event
   */
  Instant getTimestamp();

  /**
   * Retrieves relevant metadata of the record, such as the type of the value ({@link
   * io.zeebe.protocol.clientapi.ValueType}), the type of record ({@link
   * io.zeebe.protocol.clientapi.RecordType}), etc.
   *
   * @return record metadata
   */
  RecordMetadata getMetadata();

  /**
   * Returns the raw value of the record, which should implement one of the interfaces in the {@link
   * io.zeebe.exporter.record.value} package.
   *
   * <p>The record value is essentially the record specific data, e.g. for a workflow instance
   * creation event, it would contain information relevant to the workflow instance being created.
   *
   * @return record value
   */
  T getValue();

  /** @return a JSON marshaled representation of this record */
  String toJson();
}
