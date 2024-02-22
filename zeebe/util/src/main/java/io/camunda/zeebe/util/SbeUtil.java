/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util;

import io.camunda.zeebe.util.buffer.BufferWriter;
import java.nio.ByteOrder;
import org.agrona.sbe.MessageEncoderFlyweight;

public final class SbeUtil {
  private SbeUtil() {}

  /**
   * Writes a {@link BufferWriter} instance as a nested field in an SBE message. This can be useful
   * to avoid intermediate copies to a {@link org.agrona.DirectBuffer} instance and using the
   * encoder to write the copy. This can also be used to write nested SBE messages as well.
   *
   * <p>NOTE: variable length data in SBE is written/read in order. This method should be called as
   * well in the right order so the data is written at the right offset.
   *
   * @param writer the data to write
   * @param headerLength the length of the header corresponding to the data we want to write
   * @param message the SBE message into which we should write
   * @param order the byte order
   */
  public static void writeNested(
      final BufferWriter writer,
      final int headerLength,
      final MessageEncoderFlyweight message,
      final ByteOrder order) {
    final int dataLength = writer.getLength();
    final var limit = message.limit();

    message.limit(limit + headerLength + dataLength);
    message.buffer().putInt(limit, dataLength, order);
    writer.write(message.buffer(), limit + headerLength);
  }
}
