/*
 * Copyright 2018-present Open Networking Foundation
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
package io.atomix.primitive.log;

import static com.google.common.base.MoreObjects.toStringHelper;

import io.atomix.utils.misc.ArraySizeHashPrinter;

/**
 * Distributed log protocol record.
 *
 * <p>A log record represents an entry in a distributed log. The record includes an {@link #index()}
 * and a {@link #timestamp()} at which the entry was committed to the log in addition to the {@link
 * #value()} of the entry.
 */
public class LogRecord {
  private final long index;
  private final long timestamp;
  private final byte[] value;

  public LogRecord(final long index, final long timestamp, final byte[] value) {
    this.index = index;
    this.timestamp = timestamp;
    this.value = value;
  }

  /**
   * Returns the record index.
   *
   * @return the record index
   */
  public long index() {
    return index;
  }

  /**
   * Returns the record timestamp.
   *
   * @return the record timestamp
   */
  public long timestamp() {
    return timestamp;
  }

  /**
   * Returns the record value.
   *
   * @return the record value
   */
  public byte[] value() {
    return value;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("index", index())
        .add("timestamp", timestamp())
        .add("value", ArraySizeHashPrinter.of(value()))
        .toString();
  }
}
