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
package io.zeebe.msgpack.value;

import io.zeebe.msgpack.Recyclable;
import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;

public abstract class BaseValue implements Recyclable {
  public abstract void writeJSON(StringBuilder builder);

  public abstract void write(MsgPackWriter writer);

  public abstract void read(MsgPackReader reader);

  public abstract int getEncodedLength();

  @Override
  public String toString() {
    final StringBuilder stringBuilder = new StringBuilder();
    writeJSON(stringBuilder);
    return stringBuilder.toString();
  }
}
