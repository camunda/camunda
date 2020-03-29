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

import io.atomix.primitive.operation.PrimitiveOperation;

/**
 * Represents a state machine query.
 *
 * <p>The {@code QueryEntry} is a special entry that is typically not ever written to the Raft log.
 * Query entries are simply used to represent the context within which a query is applied to the
 * state machine. Query entry {@link #sequenceNumber() sequence} numbers and indexes are used to
 * sequence queries as they're applied to the user state machine.
 */
public class QueryEntry extends OperationEntry {

  public QueryEntry(
      final long term,
      final long timestamp,
      final long session,
      final long sequence,
      final PrimitiveOperation operation) {
    super(term, timestamp, session, sequence, operation);
  }
}
