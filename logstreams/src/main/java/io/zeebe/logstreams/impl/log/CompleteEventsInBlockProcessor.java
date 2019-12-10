/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.log;

import static io.zeebe.logstreams.impl.log.LogEntryDescriptor.HEADER_BLOCK_LENGTH;
import static io.zeebe.logstreams.impl.log.LogEntryDescriptor.getFragmentLength;
import static io.zeebe.logstreams.spi.LogStorage.OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY;

import io.zeebe.logstreams.spi.ReadResultProcessor;
import java.nio.ByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class CompleteEventsInBlockProcessor implements ReadResultProcessor {
  private final MutableDirectBuffer directBuffer = new UnsafeBuffer(0, 0);
  private long lastReadEventPosition = -1;

  public long getLastReadEventPosition() {
    return lastReadEventPosition;
  }

  @Override
  public int process(ByteBuffer byteBuffer, int readResult) {
    if (byteBuffer.capacity() < HEADER_BLOCK_LENGTH) {
      readResult = (int) OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY;
    }

    directBuffer.wrap(byteBuffer);

    final int startPosition = byteBuffer.position() - readResult;
    readResult = calculateCorrectReadResult(readResult, startPosition);

    if (readResult > 0) {
      byteBuffer.position(startPosition + readResult);
      byteBuffer.limit(startPosition + readResult);
    } else if (readResult == 0) {
      readResult = (int) OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY;
    }
    return readResult;
  }

  /**
   * Iterates over the given logged events and calculates the correct bytes which are read. This
   * means if an event is not read completely it must be excluded. For that the readResult will be
   * decreased by the fragment length of the logged event.
   *
   * @param readResult the given read result, count of bytes which was read
   * @param position the current position in the buffer
   * @return the calculated read result
   */
  private int calculateCorrectReadResult(int readResult, int position) {
    int remainingBytes = readResult;
    while (remainingBytes >= HEADER_BLOCK_LENGTH) {
      final int fragmentLength = getFragmentLength(directBuffer, position);

      if (fragmentLength <= remainingBytes) {
        lastReadEventPosition = LogEntryDescriptor.getPosition(directBuffer, position);
        remainingBytes -= fragmentLength;
        position += fragmentLength;
      } else {
        if (fragmentLength > directBuffer.capacity()) {
          readResult = (int) OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY;
        } else {
          readResult -= remainingBytes;
        }
        remainingBytes = 0;
      }
    }

    if (remainingBytes != 0) {
      readResult -= remainingBytes;
    }

    return readResult;
  }
}
