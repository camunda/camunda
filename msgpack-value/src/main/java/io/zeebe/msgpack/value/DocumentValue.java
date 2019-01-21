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

import io.zeebe.msgpack.spec.MsgPackCodes;
import io.zeebe.msgpack.spec.MsgPackFormat;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.msgpack.spec.MsgPackType;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class DocumentValue extends BinaryValue {
  public static final DirectBuffer EMPTY_DOCUMENT = new UnsafeBuffer(MsgPackHelper.EMTPY_OBJECT);

  public DocumentValue() {}

  public DocumentValue(DirectBuffer initialValue, int offset, int length) {
    super(initialValue, offset, length);
  }

  @Override
  public void wrap(DirectBuffer buff, int offset, int length) {
    final boolean documentIsNil =
        length == 0 || (length == 1 && buff.getByte(offset) == MsgPackCodes.NIL);

    if (documentIsNil) {
      buff = EMPTY_DOCUMENT;
      offset = 0;
      length = EMPTY_DOCUMENT.capacity();
    }

    final byte firstByte = buff.getByte(offset);
    final MsgPackFormat format = MsgPackFormat.valueOf(firstByte);
    final boolean isValid = format.getType() == MsgPackType.MAP;

    if (!isValid) {
      throw new IllegalArgumentException(
          String.format(
              "Expected document to be a root level object, but was '%s'",
              format.getType().name()));
    }

    super.wrap(buff, offset, length);
  }
}
