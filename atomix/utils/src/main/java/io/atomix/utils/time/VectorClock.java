/*
 * Copyright 2017-present Open Networking Foundation
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
package io.atomix.utils.time;

import static com.google.common.base.MoreObjects.toStringHelper;

import com.google.common.annotations.Beta;
import io.atomix.utils.Identifier;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Vector clock. */
@Beta
public class VectorClock<T extends Identifier> implements Clock<VectorTimestamp<T>> {
  private final T localIdentifier;
  private final Map<T, VectorTimestamp<T>> vector = new HashMap<>();

  public VectorClock(final T localIdentifier) {
    this(new VectorTimestamp<T>(localIdentifier, 0));
  }

  public VectorClock(final VectorTimestamp<T> localTimestamp) {
    this(localTimestamp, Collections.emptyList());
  }

  public VectorClock(
      final VectorTimestamp<T> localTimestamp, final Collection<VectorTimestamp<T>> vector) {
    this.localIdentifier = localTimestamp.identifier();
    this.vector.put(localTimestamp.identifier(), localTimestamp);
    for (final VectorTimestamp<T> timestamp : vector) {
      this.vector.put(timestamp.identifier(), timestamp);
    }
  }

  @Override
  public VectorTimestamp<T> getTime() {
    return vector.get(localIdentifier);
  }

  /**
   * Returns the local logical timestamp.
   *
   * @return the logical timestamp for the local identifier
   */
  public LogicalTimestamp getLocalTimestamp() {
    return getTime();
  }

  /**
   * Returns the logical timestamp for the given identifier.
   *
   * @param identifier the identifier for which to return the timestamp
   * @return the logical timestamp for the given identifier
   */
  public LogicalTimestamp getTimestamp(final T identifier) {
    return vector.get(identifier);
  }

  /**
   * Returns a collection of identifier-timestamp pairs.
   *
   * @return a collection of identifier-timestamp pairs
   */
  public Collection<VectorTimestamp<T>> getTimestamps() {
    return vector.values();
  }

  /**
   * Updates the given timestamp.
   *
   * @param timestamp the timestamp to update
   */
  public void update(final VectorTimestamp<T> timestamp) {
    final VectorTimestamp<T> currentTimestamp = vector.get(timestamp.identifier());
    if (currentTimestamp == null || currentTimestamp.value() < timestamp.value()) {
      vector.put(timestamp.identifier(), timestamp);
    }
  }

  /**
   * Updates the vector clock.
   *
   * @param clock the vector clock with which to update this clock
   */
  public void update(final VectorClock<T> clock) {
    for (final VectorTimestamp<T> timestamp : clock.vector.values()) {
      update(timestamp);
    }
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("time", getTime()).add("vector", getTimestamps()).toString();
  }
}
