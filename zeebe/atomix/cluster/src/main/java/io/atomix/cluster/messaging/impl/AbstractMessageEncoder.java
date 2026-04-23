/*
 * Copyright 2019-present Open Networking Foundation
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
package io.atomix.cluster.messaging.impl;

import io.atomix.utils.net.Address;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Encode InternalMessage out into a byte buffer. */
abstract class AbstractMessageEncoder extends MessageToMessageEncoder<Object> {
  // Effectively MessageToByteEncoder<InternalMessage>,
  // had to specify <Object> to avoid Class Loader not being able to find some classes.

  protected final Address address;
  private final Logger log = LoggerFactory.getLogger(getClass());
  private boolean addressWritten;

  AbstractMessageEncoder(final Address address) {
    super();
    this.address = address;
  }

  protected abstract void encodeAddress(ProtocolMessage message, ByteBuf buffer);

  protected abstract void encodeMessage(ProtocolMessage message, ByteBuf buffer, List<Object> out);

  protected abstract void encodeRequest(ProtocolRequest request, ByteBuf out);

  protected abstract void encodeReply(ProtocolReply reply, ByteBuf out);

  static void writeString(final ByteBuf buffer, final String value) {
    final ByteBuf buf = buffer.alloc().buffer(ByteBufUtil.utf8MaxBytes(value));
    try {
      final int length = ByteBufUtil.writeUtf8(buf, value);
      buffer.writeShort(length);
      buffer.writeBytes(buf);
    } finally {
      buf.release();
    }
  }

  public static void writeInt(final ByteBuf buf, final int value) {
    if (value >>> 7 == 0) {
      buf.writeByte(value);
    } else if (value >>> 14 == 0) {
      buf.writeByte((value & 0x7F) | 0x80);
      buf.writeByte(value >>> 7);
    } else if (value >>> 21 == 0) {
      buf.writeByte((value & 0x7F) | 0x80);
      buf.writeByte(value >>> 7 | 0x80);
      buf.writeByte(value >>> 14);
    } else if (value >>> 28 == 0) {
      buf.writeByte((value & 0x7F) | 0x80);
      buf.writeByte(value >>> 7 | 0x80);
      buf.writeByte(value >>> 14 | 0x80);
      buf.writeByte(value >>> 21);
    } else {
      buf.writeByte((value & 0x7F) | 0x80);
      buf.writeByte(value >>> 7 | 0x80);
      buf.writeByte(value >>> 14 | 0x80);
      buf.writeByte(value >>> 21 | 0x80);
      buf.writeByte(value >>> 28);
    }
  }

  static void writeLong(final ByteBuf buf, final long value) {
    if (value >>> 7 == 0) {
      buf.writeByte((byte) value);
    } else if (value >>> 14 == 0) {
      buf.writeByte((byte) ((value & 0x7F) | 0x80));
      buf.writeByte((byte) (value >>> 7));
    } else if (value >>> 21 == 0) {
      buf.writeByte((byte) ((value & 0x7F) | 0x80));
      buf.writeByte((byte) (value >>> 7 | 0x80));
      buf.writeByte((byte) (value >>> 14));
    } else if (value >>> 28 == 0) {
      buf.writeByte((byte) ((value & 0x7F) | 0x80));
      buf.writeByte((byte) (value >>> 7 | 0x80));
      buf.writeByte((byte) (value >>> 14 | 0x80));
      buf.writeByte((byte) (value >>> 21));
    } else if (value >>> 35 == 0) {
      buf.writeByte((byte) ((value & 0x7F) | 0x80));
      buf.writeByte((byte) (value >>> 7 | 0x80));
      buf.writeByte((byte) (value >>> 14 | 0x80));
      buf.writeByte((byte) (value >>> 21 | 0x80));
      buf.writeByte((byte) (value >>> 28));
    } else if (value >>> 42 == 0) {
      buf.writeByte((byte) ((value & 0x7F) | 0x80));
      buf.writeByte((byte) (value >>> 7 | 0x80));
      buf.writeByte((byte) (value >>> 14 | 0x80));
      buf.writeByte((byte) (value >>> 21 | 0x80));
      buf.writeByte((byte) (value >>> 28 | 0x80));
      buf.writeByte((byte) (value >>> 35));
    } else if (value >>> 49 == 0) {
      buf.writeByte((byte) ((value & 0x7F) | 0x80));
      buf.writeByte((byte) (value >>> 7 | 0x80));
      buf.writeByte((byte) (value >>> 14 | 0x80));
      buf.writeByte((byte) (value >>> 21 | 0x80));
      buf.writeByte((byte) (value >>> 28 | 0x80));
      buf.writeByte((byte) (value >>> 35 | 0x80));
      buf.writeByte((byte) (value >>> 42));
    } else if (value >>> 56 == 0) {
      buf.writeByte((byte) ((value & 0x7F) | 0x80));
      buf.writeByte((byte) (value >>> 7 | 0x80));
      buf.writeByte((byte) (value >>> 14 | 0x80));
      buf.writeByte((byte) (value >>> 21 | 0x80));
      buf.writeByte((byte) (value >>> 28 | 0x80));
      buf.writeByte((byte) (value >>> 35 | 0x80));
      buf.writeByte((byte) (value >>> 42 | 0x80));
      buf.writeByte((byte) (value >>> 49));
    } else {
      buf.writeByte((byte) ((value & 0x7F) | 0x80));
      buf.writeByte((byte) (value >>> 7 | 0x80));
      buf.writeByte((byte) (value >>> 14 | 0x80));
      buf.writeByte((byte) (value >>> 21 | 0x80));
      buf.writeByte((byte) (value >>> 28 | 0x80));
      buf.writeByte((byte) (value >>> 35 | 0x80));
      buf.writeByte((byte) (value >>> 42 | 0x80));
      buf.writeByte((byte) (value >>> 49 | 0x80));
      buf.writeByte((byte) (value >>> 56));
    }
  }

  @Override
  public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {
    try {
      if (cause instanceof IOException) {
        log.debug("IOException inside channel handling pipeline.", cause);
      } else {
        log.error("non-IOException inside channel handling pipeline.", cause);
      }
    } finally {
      context.close();
    }
  }

  // Effectively same result as one generated by MessageToByteEncoder<InternalMessage>
  @Override
  public final boolean acceptOutboundMessage(final Object msg) throws Exception {
    return msg instanceof ProtocolMessage;
  }

  @Override
  protected void encode(
      final ChannelHandlerContext context, final Object message, final List<Object> out)
      throws Exception {

    // Only encode actual ProtocolMessages.
    // If Netty sends internal control objects down the pipeline, we ignore them.
    if (!(message instanceof final ProtocolMessage protocolMessage)) {
      out.add(message);
      return;
    }

    // 1. Allocate a ByteBuf for the Atomix/Netty protocol headers
    final ByteBuf headerBuffer = context.alloc().buffer(128);
    final ByteBuf footerBuffer = context.alloc().buffer(128);
    boolean addedToOut = false;

    try {
      if (!addressWritten) {
        encodeAddress(protocolMessage, headerBuffer);
        addressWritten = true;
      }

      // 3. Write the message headers (V1/V2 specific)
      encodeMessage(protocolMessage, headerBuffer, out);

      if (headerBuffer.isReadable()) {
        out.add(headerBuffer);
        addedToOut = true;
      }

      if (protocolMessage.payload() != null) {
        protocolMessage.payload().encode(headerBuffer, out);
      }

      if (protocolMessage instanceof ProtocolRequest) {
        encodeRequest((ProtocolRequest) protocolMessage, footerBuffer);
      } else if (protocolMessage instanceof ProtocolReply) {
        encodeReply((ProtocolReply) protocolMessage, footerBuffer);
      }

      if (footerBuffer.isReadable()) {
        out.add(footerBuffer);
      }
    } finally {
      if (!addedToOut) {
        headerBuffer.release();
      }
    }
  }

  protected void encode(
      final ChannelHandlerContext context, final Object rawMessage, final ByteBuf out) {
    if (!addressWritten) {
      encodeAddress((ProtocolMessage) rawMessage, out);
      addressWritten = true;
    }

    encodeMessage((ProtocolMessage) rawMessage, out, null);

    if (rawMessage instanceof ProtocolRequest) {
      encodeRequest((ProtocolRequest) rawMessage, out);
    } else if (rawMessage instanceof ProtocolReply) {
      encodeReply((ProtocolReply) rawMessage, out);
    }
  }
}
