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
package io.zeebe.distributedlog.restore.log.impl;

import io.zeebe.distributedlog.restore.log.LogReplicationResponse;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class DefaultLogReplicationResponse implements LogReplicationResponse {
  private long toPosition;
  private boolean moreAvailable;
  private byte[] serializedEvents;

  public DefaultLogReplicationResponse() {}

  public DefaultLogReplicationResponse(
      long toPosition, boolean moreAvailable, byte[] serializedEvents) {
    this.toPosition = toPosition;
    this.moreAvailable = moreAvailable;
    this.serializedEvents = serializedEvents;
  }

  @Override
  public long getToPosition() {
    return toPosition;
  }

  public void setToPosition(long toPosition) {
    this.toPosition = toPosition;
  }

  @Override
  public boolean hasMoreAvailable() {
    return moreAvailable;
  }

  public void setMoreAvailable(boolean moreAvailable) {
    this.moreAvailable = moreAvailable;
  }

  @Override
  public byte[] getSerializedEvents() {
    return serializedEvents;
  }

  public void setSerializedEvents(DirectBuffer buffer, int offset, int length) {
    final DirectBuffer wrapper = new UnsafeBuffer(buffer, offset, length);
    this.serializedEvents = BufferUtil.bufferAsArray(wrapper);
  }

  @Override
  public String toString() {
    return "DefaultLogReplicationResponse{"
        + "toPosition="
        + toPosition
        + ", moreAvailable="
        + moreAvailable
        + ", serializedEvents.length="
        + (serializedEvents == null ? 0 : serializedEvents.length)
        + '}';
  }
}
