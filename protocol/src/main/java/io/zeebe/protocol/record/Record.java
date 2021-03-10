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
package io.zeebe.protocol.record;

import io.zeebe.protocol.record.intent.Intent;

/** Represents a record published to the log stream. */
public interface Record<T extends RecordValue> extends JsonSerializable, Cloneable {
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

  /**
   * Retrieves the key of the record.
   *
   * <p>Multiple records can have the same key if they belongs to the same logical entity. Keys are
   * unique for the combination of partition and record type.
   *
   * @return the key of the record
   */
  long getKey();

  /** @return the unix timestamp at which the record was published on the partition. */
  long getTimestamp();

  /** @return the intent of the record */
  Intent getIntent();

  /** @return the partition ID on which the record was published */
  int getPartitionId();

  /** @return the type of the record (event, command or command rejection) */
  RecordType getRecordType();

  /**
   * @return the type of rejection if {@link #getRecordType()} returns {@link
   *     RecordType#COMMAND_REJECTION} or else <code>null</code>.
   */
  RejectionType getRejectionType();

  /**
   * @return the reason why a command was rejected if {@link #getRecordType()} returns {@link
   *     RecordType#COMMAND_REJECTION} or else <code>null</code>.
   */
  String getRejectionReason();

  /** @return the version of the broker that wrote this record */
  String getBrokerVersion();

  /** @return the type of the record (e.g. job, process, process instance, etc.) */
  ValueType getValueType();

  /**
   * Returns the raw value of the record, which should implement one of the interfaces in the {@link
   * io.zeebe.exporter.record.value} package.
   *
   * <p>The record value is essentially the record specific data, e.g. for a process instance
   * creation event, it would contain information relevant to the process instance being created.
   *
   * @return record value
   */
  T getValue();

  /**
   * Creates a deep copy of the current record. Can be used to collect records.
   *
   * @return a deep copy of this record
   */
  Record<T> clone();
}
