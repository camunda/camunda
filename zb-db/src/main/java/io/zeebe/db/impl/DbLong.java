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
package io.zeebe.db.impl;

import static io.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER;

import io.zeebe.db.DbKey;
import io.zeebe.db.DbValue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class DbLong implements DbKey, DbValue {

  private long longValue;

  public void wrapLong(long value) {
    longValue = value;
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    longValue = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
  }

  @Override
  public int getLength() {
    return Long.BYTES;
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    buffer.putLong(offset, longValue, ZB_DB_BYTE_ORDER);
  }

  public long getValue() {
    return longValue;
  }
}
