/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.spec;

import java.nio.ByteOrder;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;

public final class MsgPackUtil {

  public static DirectBuffer encodeMsgPack(final CheckedConsumer<MessageBufferPacker> msgWriter) {
    final MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
    try {
      msgWriter.accept(packer);
      packer.close();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    final byte[] bytes = packer.toByteArray();
    return new UnsafeBuffer(bytes);
  }

  public static byte[] toByte(final long value) {
    final UnsafeBuffer buf = new UnsafeBuffer(new byte[8]);
    buf.putLong(0, value, ByteOrder.BIG_ENDIAN);
    return buf.byteArray();
  }

  public static byte[] toByte(final float value) {
    final UnsafeBuffer buf = new UnsafeBuffer(new byte[4]);
    buf.putFloat(0, value, ByteOrder.BIG_ENDIAN);
    return buf.byteArray();
  }

  public static byte[] toByte(final double value) {
    final UnsafeBuffer buf = new UnsafeBuffer(new byte[8]);
    buf.putDouble(0, value, ByteOrder.BIG_ENDIAN);
    return buf.byteArray();
  }

  @FunctionalInterface
  public interface CheckedConsumer<T> {
    void accept(T t) throws Exception;
  }

  @FunctionalInterface
  public interface CheckedToIntFunction<T> {
    int apply(T t) throws Exception;
  }
}
