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
package io.atomix.utils.time;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ComparisonChain;
import io.atomix.utils.misc.TimestampPrinter;
import java.util.Objects;

/**
 * A Timestamp that derives its value from the prevailing wallclock time on the controller where it
 * is generated.
 */
public class WallClockTimestamp implements Timestamp {

  private final long unixTimestamp;

  public WallClockTimestamp() {
    unixTimestamp = System.currentTimeMillis();
  }

  public WallClockTimestamp(final long timestamp) {
    unixTimestamp = timestamp;
  }

  /**
   * Returns a new wall clock timestamp for the given unix timestamp.
   *
   * @param unixTimestamp the unix timestamp for which to create a new wall clock timestamp
   * @return the wall clock timestamp
   */
  public static WallClockTimestamp from(final long unixTimestamp) {
    return new WallClockTimestamp(unixTimestamp);
  }

  @Override
  public int compareTo(final Timestamp o) {
    checkArgument(o instanceof WallClockTimestamp, "Must be WallClockTimestamp", o);
    final WallClockTimestamp that = (WallClockTimestamp) o;

    return ComparisonChain.start().compare(this.unixTimestamp, that.unixTimestamp).result();
  }

  @Override
  public int hashCode() {
    return Long.hashCode(unixTimestamp);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof WallClockTimestamp)) {
      return false;
    }
    final WallClockTimestamp that = (WallClockTimestamp) obj;
    return Objects.equals(this.unixTimestamp, that.unixTimestamp);
  }

  @Override
  public String toString() {
    return new TimestampPrinter(unixTimestamp).toString();
  }

  /**
   * Returns the unixTimestamp.
   *
   * @return unix timestamp
   */
  public long unixTimestamp() {
    return unixTimestamp;
  }
}
