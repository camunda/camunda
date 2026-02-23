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
package io.camunda.zeebe.protocol.record;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.Map;
import org.immutables.value.Value;

/** Represents a record published to the log stream. */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableRecord.Builder.class)
public interface Record<T extends RecordValue> extends JsonSerializable {
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

  /**
   * @return the unix timestamp at which the record was published on the partition.
   */
  long getTimestamp();

  /**
   * @return the intent of the record
   */
  Intent getIntent();

  /**
   * @return the partition ID on which the record was published
   */
  int getPartitionId();

  /**
   * @return the type of the record (event, command or command rejection)
   */
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

  /**
   * @return the version of the broker that wrote this record
   */
  String getBrokerVersion();

  /**
   * Provides the authorization data of the user the triggered the creation of this record. The
   * following entries may be available:
   *
   * <ul>
   *   <li>Key: <code>authorized_tenants</code>; Value: a List of Strings defining the user's
   *       authorized tenants.
   *   <li>Key: <code>authorized_user_key</code>; Value: the Long representation of the
   *       authenticated user's key
   * </ul>
   *
   * @return a Map of authorization data for this record or an empty Map if not set.
   */
  Map<String, Object> getAuthorizations();

  /**
   * Provides agent information that is associated with the record, e.g. the id and name of the
   * agent that produced the record within an adhoc subprocess
   *
   * @return the agent information associated with this record, or {@code null} if no agent
   */
  @Nullable
  Agent getAgent();

  /**
   * A record version is an integer starting from 1. The version of a record is defined when it is
   * written. It allows different versions of the same record to be processed or applied
   * differently.
   *
   * <p>For example, it allows us to apply an older event in the same way as when it was originally
   * written, while allowing newer events to be applied differently.
   *
   * @return the version of the record when written
   */
  int getRecordVersion();

  /**
   * @return the type of the record (e.g. job, process, process instance, etc.)
   */
  ValueType getValueType();

  /**
   * Returns the raw value of the record, which should implement one of the interfaces in the {@link
   * io.camunda.zeebe.protocol.record.value} package.
   *
   * <p>The record value is essentially the record specific data, e.g. for a process instance
   * creation event, it would contain information relevant to the process instance being created.
   *
   * @return record value
   */
  T getValue();

  /**
   * The operationReference is an id passed from clients to correlate operations with resulted
   * records
   *
   * @return the reference for the operation that produced this record
   */
  long getOperationReference();

  /**
   * The batchOperationKey indicates the batch operation this record was produced by.
   *
   * @return the batch operation key
   */
  @Value.Default
  default long getBatchOperationReference() {
    return RecordMetadataDecoder.batchOperationReferenceNullValue();
  }

  /**
   * Creates a deep copy of the current record. Can be used to collect records.
   *
   * @return a deep copy of this record
   */
  @Value.NonAttribute
  default Record<T> copyOf() {
    throw new UnsupportedOperationException(
        "Failed to create a deep copy of this record; this implementation does not support this out"
            + " of the box");
  }
}
