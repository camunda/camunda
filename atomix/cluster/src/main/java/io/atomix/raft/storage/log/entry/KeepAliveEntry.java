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

import io.atomix.utils.misc.ArraySizeHashPrinter;
import io.atomix.utils.misc.TimestampPrinter;

/** Stores a client keep-alive request. */
public class KeepAliveEntry extends TimestampedEntry {

  private final long[] sessionIds;
  private final long[] commandSequences;
  private final long[] eventIndexes;

  public KeepAliveEntry(
      final long term,
      final long timestamp,
      final long[] sessionIds,
      final long[] commandSequences,
      final long[] eventIndexes) {
    super(term, timestamp);
    this.sessionIds = sessionIds;
    this.commandSequences = commandSequences;
    this.eventIndexes = eventIndexes;
  }

  /**
   * Returns the session identifiers.
   *
   * @return The session identifiers.
   */
  public long[] sessionIds() {
    return sessionIds;
  }

  /**
   * Returns the command sequence numbers.
   *
   * @return The command sequence numbers.
   */
  public long[] commandSequenceNumbers() {
    return commandSequences;
  }

  /**
   * Returns the event indexes.
   *
   * @return The event indexes.
   */
  public long[] eventIndexes() {
    return eventIndexes;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("term", term)
        .add("timestamp", new TimestampPrinter(timestamp))
        .add("sessionIds", ArraySizeHashPrinter.of(sessionIds))
        .add("commandSequences", ArraySizeHashPrinter.of(commandSequences))
        .add("eventIndexes", ArraySizeHashPrinter.of(eventIndexes))
        .toString();
  }
}
