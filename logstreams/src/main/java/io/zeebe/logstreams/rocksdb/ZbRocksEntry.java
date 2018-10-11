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
package io.zeebe.logstreams.rocksdb;

import java.util.Map.Entry;
import java.util.Objects;
import org.agrona.DirectBuffer;

public class ZbRocksEntry implements Entry<DirectBuffer, DirectBuffer> {
  private DirectBuffer key;
  private DirectBuffer value;

  public ZbRocksEntry() {}

  public ZbRocksEntry(final ZbRocksEntry other) {
    this(other.getKey(), other.getValue());
  }

  public ZbRocksEntry(final DirectBuffer key, final DirectBuffer value) {
    wrap(key, value);
  }

  public ZbRocksEntry wrap(final ZbRocksEntry entry) {
    return wrap(entry.getKey(), entry.getValue());
  }

  public ZbRocksEntry wrap(final DirectBuffer key, final DirectBuffer value) {
    this.setKey(key);
    this.setValue(value);

    return this;
  }

  @Override
  public DirectBuffer getKey() {
    return key;
  }

  public DirectBuffer setKey(DirectBuffer key) {
    final DirectBuffer oldKey = this.key;
    this.key = key;
    return oldKey;
  }

  @Override
  public DirectBuffer getValue() {
    return value;
  }

  @Override
  public DirectBuffer setValue(DirectBuffer value) {
    final DirectBuffer oldValue = this.value;
    this.value = value;
    return oldValue;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof ZbRocksEntry)) {
      return false;
    }

    final ZbRocksEntry that = (ZbRocksEntry) o;

    return Objects.equals(getKey(), that.getKey()) && Objects.equals(getValue(), that.getValue());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getKey(), getValue());
  }
}
