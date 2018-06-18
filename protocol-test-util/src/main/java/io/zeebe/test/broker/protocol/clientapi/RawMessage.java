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
package io.zeebe.test.broker.protocol.clientapi;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class RawMessage {

  protected final boolean isResponse;
  protected final UnsafeBuffer message;
  protected final int sequenceNumber;

  public RawMessage(
      boolean isResponse,
      int sequenceNumber,
      DirectBuffer message,
      int messageOffset,
      int messageLength) {
    this.isResponse = isResponse;
    this.sequenceNumber = sequenceNumber;

    this.message = new UnsafeBuffer(new byte[messageLength]);
    this.message.putBytes(0, message, messageOffset, messageLength);
  }

  public boolean isResponse() {
    return isResponse;
  }

  public boolean isMessage() {
    return !isResponse;
  }

  /**
   * Determines the order in which messages have been received. Is only meaningful for messages
   * received on the same channel.
   */
  public int getSequenceNumber() {
    return sequenceNumber;
  }

  /** @return message excluding transport and protocol header */
  public DirectBuffer getMessage() {
    return message;
  }
}
