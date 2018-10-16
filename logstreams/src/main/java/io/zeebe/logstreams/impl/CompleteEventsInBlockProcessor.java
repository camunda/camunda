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
package io.zeebe.logstreams.impl;

import static io.zeebe.logstreams.impl.LogEntryDescriptor.HEADER_BLOCK_LENGTH;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.getFragmentLength;
import static io.zeebe.logstreams.spi.LogStorage.OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY;

import io.zeebe.logstreams.spi.ReadResultProcessor;
import java.nio.ByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** @author Christopher Zell <christopher.zell@camunda.com> */
public class CompleteEventsInBlockProcessor implements ReadResultProcessor {
  private final MutableDirectBuffer directBuffer = new UnsafeBuffer(0, 0);
  private long lastReadEventPosition = -1;

  long getLastReadEventPosition() {
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
