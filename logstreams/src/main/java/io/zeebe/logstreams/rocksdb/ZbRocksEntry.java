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
import org.agrona.DirectBuffer;

public class ZbRocksEntry implements Entry<DirectBuffer, DirectBuffer> {
  private DirectBuffer key;
  private DirectBuffer value;

  public ZbRocksEntry() {}

  public ZbRocksEntry(final DirectBuffer key, final DirectBuffer value) {
    wrap(key, value);
  }

  public ZbRocksEntry wrap(final DirectBuffer key, final DirectBuffer value) {
    this.key = key;
    this.value = value;

    return this;
  }

  @Override
  public DirectBuffer getKey() {
    return key;
  }

  @Override
  public DirectBuffer getValue() {
    return value;
  }

  @Override
  public DirectBuffer setValue(DirectBuffer value) {
    this.value = value;
    return this.value;
  }
}
