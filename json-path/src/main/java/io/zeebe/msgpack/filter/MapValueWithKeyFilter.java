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
package io.zeebe.msgpack.filter;

import io.zeebe.msgpack.query.MsgPackTraversalContext;
import io.zeebe.msgpack.spec.MsgPackToken;
import io.zeebe.msgpack.spec.MsgPackType;
import io.zeebe.msgpack.util.ByteUtil;
import java.nio.charset.StandardCharsets;
import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** Only works for maps that have scalar values as keys */
public class MapValueWithKeyFilter implements MsgPackFilter {
  public static final int NO_MATCHING_VALUE = -1;

  @Override
  public boolean matches(
      MsgPackTraversalContext ctx, DirectBuffer filterContext, MsgPackToken value) {

    if (ctx.hasElements() && ctx.isMap()) {
      final MutableDirectBuffer dynamicContext = ctx.dynamicContext();

      final int currentElement = ctx.currentElement();
      if (currentElement == 0) {
        // initialization
        dynamicContext.putInt(0, NO_MATCHING_VALUE);
      }

      final int matchingValueIndex = dynamicContext.getInt(0);
      final int queryLength = filterContext.getInt(0);

      if (currentElement == matchingValueIndex) {
        dynamicContext.putInt(0, NO_MATCHING_VALUE);
        return true;
      }
      if (ctx.currentElement() % 2 == 0
          && // map keys have even positions
          value.getType() == MsgPackType.STRING
          && ByteUtil.equal(
              filterContext,
              BitUtil.SIZE_OF_INT,
              queryLength,
              value.getValueBuffer(),
              0,
              value.getValueBuffer().capacity())) {
        dynamicContext.putInt(0, currentElement + 1);
      }
    }

    return false;
  }

  public static void encodeDynamicContext(
      MutableDirectBuffer contextBuffer, DirectBuffer keyBuffer, int keyOffset, int keyLength) {
    contextBuffer.putInt(0, keyLength);
    contextBuffer.putBytes(BitUtil.SIZE_OF_INT, keyBuffer, keyOffset, keyLength);
  }

  public static void encodeDynamicContext(MutableDirectBuffer contextBuffer, String key) {
    final UnsafeBuffer keyBuffer = new UnsafeBuffer(key.getBytes(StandardCharsets.UTF_8));
    encodeDynamicContext(contextBuffer, keyBuffer, 0, keyBuffer.capacity());
  }
}
