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
package io.zeebe.msgpack.spec;

import java.nio.ByteOrder;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;

public class MsgPackUtil {

  public static DirectBuffer encodeMsgPack(CheckedConsumer<MessageBufferPacker> msgWriter) {
    final MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
    try {
      msgWriter.accept(packer);
      packer.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    final byte[] bytes = packer.toByteArray();
    return new UnsafeBuffer(bytes);
  }

  @FunctionalInterface
  public interface CheckedConsumer<T> {
    void accept(T t) throws Exception;
  }

  public static byte[] toByte(long value) {
    final UnsafeBuffer buf = new UnsafeBuffer(new byte[8]);
    buf.putLong(0, value, ByteOrder.BIG_ENDIAN);
    return buf.byteArray();
  }

  public static byte[] toByte(float value) {
    final UnsafeBuffer buf = new UnsafeBuffer(new byte[4]);
    buf.putFloat(0, value, ByteOrder.BIG_ENDIAN);
    return buf.byteArray();
  }

  public static byte[] toByte(double value) {
    final UnsafeBuffer buf = new UnsafeBuffer(new byte[8]);
    buf.putDouble(0, value, ByteOrder.BIG_ENDIAN);
    return buf.byteArray();
  }
}
