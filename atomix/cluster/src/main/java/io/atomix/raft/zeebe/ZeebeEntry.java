/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.raft.zeebe;

import static com.google.common.base.MoreObjects.toStringHelper;

import io.atomix.raft.storage.log.entry.TimestampedEntry;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Stores an entry that contains serialized records, ordered by their position; the lowestPosition
 * and highestPosition metadata allow for fast binary search over a collection of entries to quickly
 * find a particular record.
 *
 * <p>Each entry is written with the leader's {@link #timestamp() timestamp} at the time the entry
 * was logged This gives state machines an approximation of time with which to react to the
 * application of entries to the state machine.
 */
public class ZeebeEntry extends TimestampedEntry {

  private final long lowestPosition;
  private final long highestPosition;
  private final ByteBuffer data;

  public ZeebeEntry(
      final long term,
      final long timestamp,
      final long lowestPosition,
      final long highestPosition,
      final ByteBuffer data) {
    super(term, timestamp);
    this.lowestPosition = lowestPosition;
    this.highestPosition = highestPosition;
    this.data = data;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), lowestPosition(), highestPosition(), data());
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }

    if (!super.equals(other)) {
      return false;
    }

    if (!(other instanceof ZeebeEntry)) {
      return false;
    }

    final ZeebeEntry that = (ZeebeEntry) other;
    return lowestPosition() == that.lowestPosition()
        && highestPosition() == that.highestPosition()
        && data().equals(that.data());
  }

  public long lowestPosition() {
    return lowestPosition;
  }

  public long highestPosition() {
    return highestPosition;
  }

  public ByteBuffer data() {
    return data;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("term", term())
        .add("timestamp", timestamp())
        .add("lowestPosition", lowestPosition())
        .add("highestPosition", highestPosition())
        .toString();
  }
}
