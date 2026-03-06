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
package io.camunda.zeebe.protocol.record.value;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.OrdinalIntent;
import org.immutables.value.Value;

/**
 * Represents an ordinal tick event or command.
 *
 * <p>The ordinal is a monotonically incrementing {@code int} counter that is incremented once per
 * minute. The {@code dateTime} field records the epoch-millisecond wall-clock time at which the
 * ordinal was last incremented, allowing callers to derive the exact date and minute represented by
 * any ordinal value.
 *
 * <p>See {@link OrdinalIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableOrdinalRecordValue.Builder.class)
public interface OrdinalRecordValue extends RecordValue {

  /**
   * Returns the current ordinal value. The ordinal is a monotonically incrementing {@code int}
   * (stored as {@code int} for msgpack compatibility) that increments by 1 every minute.
   */
  int getOrdinal();

  /**
   * Returns the epoch-millisecond timestamp corresponding to the minute at which this ordinal was
   * assigned. Together with {@link #getOrdinal()}, this allows mapping any ordinal to an exact date
   * and minute.
   */
  long getDateTime();
}
