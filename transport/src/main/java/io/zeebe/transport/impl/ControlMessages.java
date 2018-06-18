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
package io.zeebe.transport.impl;

import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import java.nio.ByteOrder;
import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ControlMessages {
  // do not change; must be stable for backwards/forwards compatibility
  public static final ByteOrder CONTROL_MESSAGE_BYTEORDER =
      TransportHeaderDescriptor.HEADER_BYTE_ORDER;

  public static final int KEEP_ALIVE_TYPE = 0;

  public static final DirectBuffer KEEP_ALIVE;

  static {
    final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();

    final int messageLength = BitUtil.SIZE_OF_INT;
    final int transportFramedLength = TransportHeaderDescriptor.framedLength(messageLength);

    final UnsafeBuffer buf =
        new UnsafeBuffer(new byte[DataFrameDescriptor.alignedFramedLength(transportFramedLength)]);
    final int dataFrameHeaderOffset = 0;
    final int transportHeaderOffset = dataFrameHeaderOffset + DataFrameDescriptor.HEADER_LENGTH;
    final int messageOffset = transportHeaderOffset + TransportHeaderDescriptor.HEADER_LENGTH;

    buf.putInt(
        DataFrameDescriptor.lengthOffset(dataFrameHeaderOffset),
        DataFrameDescriptor.framedLength(transportFramedLength));
    buf.putShort(
        DataFrameDescriptor.typeOffset(dataFrameHeaderOffset), DataFrameDescriptor.TYPE_MESSAGE);

    transportHeaderDescriptor
        .wrap(buf, transportHeaderOffset)
        .protocolId(TransportHeaderDescriptor.CONTROL_MESSAGE);

    buf.putInt(messageOffset, KEEP_ALIVE_TYPE, CONTROL_MESSAGE_BYTEORDER);
    KEEP_ALIVE = buf;
  }
}
