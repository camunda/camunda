/*
 * Copyright 2015-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.storage.log.entry;

import static com.google.common.base.MoreObjects.toStringHelper;

import io.atomix.primitive.operation.PrimitiveOperation;
import io.atomix.utils.misc.TimestampPrinter;

/**
 * Stores a state machine operation.
 *
 * <p>Each state machine operation is stored with a client-provided {@link #sequenceNumber()
 * sequence number}. The sequence number is used by state machines to apply client operations in the
 * order in which they were submitted by the client (FIFO order). Additionally, each operation is
 * written with the leader's {@link #timestamp() timestamp} at the time the entry was logged. This
 * gives state machines an approximation of time with which to react to the application of
 * operations to the state machine.
 */
public abstract class OperationEntry extends SessionEntry {

  protected final long sequence;
  protected final PrimitiveOperation operation;

  public OperationEntry(
      final long term,
      final long timestamp,
      final long session,
      final long sequence,
      final PrimitiveOperation operation) {
    super(term, timestamp, session);
    this.sequence = sequence;
    this.operation = operation;
  }

  /**
   * Returns the entry operation.
   *
   * @return The entry operation.
   */
  public PrimitiveOperation operation() {
    return operation;
  }

  /**
   * Returns the operation sequence number.
   *
   * @return The operation sequence number.
   */
  public long sequenceNumber() {
    return sequence;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("term", term)
        .add("timestamp", new TimestampPrinter(timestamp))
        .add("session", session)
        .add("sequence", sequence)
        .add("operation", operation)
        .toString();
  }
}
