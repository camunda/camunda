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
package io.zeebe.transport.impl.sender;

import java.nio.ByteBuffer;
import org.agrona.MutableDirectBuffer;

public class OutgoingMessage {
  private final TransportHeaderWriter headerWriter = new TransportHeaderWriter();

  private final int remoteStreamId;

  private final MutableDirectBuffer buffer;

  private final long deadline;

  public OutgoingMessage(int remoteStreamId, MutableDirectBuffer buffer, long deadline) {
    this.remoteStreamId = remoteStreamId;
    this.buffer = buffer;
    this.deadline = deadline;
  }

  public int getRemoteStreamId() {
    return remoteStreamId;
  }

  public MutableDirectBuffer getBuffer() {
    return buffer;
  }

  public TransportHeaderWriter getHeaderWriter() {
    return headerWriter;
  }

  public ByteBuffer getAllocatedBuffer() {
    return buffer.byteBuffer();
  }

  public long getDeadline() {
    return deadline;
  }
}
