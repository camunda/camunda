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
package io.zeebe.dispatcher;

import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.HEADER_LENGTH;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.TYPE_PADDING;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.typeOffset;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Represents a claimed fragment in the buffer.
 *
 * <p>Reusable but not threadsafe.
 */
public class ClaimedFragment {
  protected final UnsafeBuffer buffer;

  private Runnable onCompleteHandler;

  public ClaimedFragment() {
    buffer = new UnsafeBuffer(0, 0);
  }

  public void wrap(
      UnsafeBuffer underlyingbuffer,
      int fragmentOffset,
      int fragmentLength,
      Runnable onCompleteHandler) {
    this.onCompleteHandler = onCompleteHandler;
    buffer.wrap(underlyingbuffer, fragmentOffset, fragmentLength);
  }

  public int getOffset() {
    return HEADER_LENGTH;
  }

  public int getLength() {
    return buffer.capacity() - HEADER_LENGTH;
  }

  public int getFragmentLength() {
    return buffer.capacity();
  }

  /** Returns the claimed fragment to write in. */
  public MutableDirectBuffer getBuffer() {
    return buffer;
  }

  /** Commit the fragment so that it can be read by subscriptions. */
  public void commit() {
    // commit the message by writing the positive framed length
    buffer.putIntOrdered(lengthOffset(0), buffer.capacity());
    onCompleteHandler.run();
    reset();
  }

  /** Commit the fragment and mark it as failed. It will be ignored by subscriptions. */
  public void abort() {
    // abort the message by setting type to padding and writing the positive framed length
    buffer.putInt(typeOffset(0), TYPE_PADDING);
    buffer.putIntOrdered(lengthOffset(0), buffer.capacity());
    onCompleteHandler.run();
    reset();
  }

  private void reset() {
    buffer.wrap(0, 0);
    onCompleteHandler = null;
  }

  public boolean isOpen() {
    return getFragmentLength() > 0;
  }
}
