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

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.FragmentHandler;
import org.agrona.DirectBuffer;

public class ReceiveBufferHandler implements FragmentHandler {
  private final TransportHeaderDescriptor transportHeaderDescriptor =
      new TransportHeaderDescriptor();

  protected final Dispatcher receiveBuffer;

  public ReceiveBufferHandler(Dispatcher receiveBuffer) {
    this.receiveBuffer = receiveBuffer;
  }

  @Override
  public int onFragment(
      DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed) {
    if (receiveBuffer == null) {
      return CONSUME_FRAGMENT_RESULT;
    }

    if (!isMarkedFailed) {
      transportHeaderDescriptor.wrap(buffer, offset);
      if (transportHeaderDescriptor.protocolId() == TransportHeaderDescriptor.CONTROL_MESSAGE) {
        // don't forward control messages
        return CONSUME_FRAGMENT_RESULT;
      }

      final long offerPosition = receiveBuffer.offer(buffer, offset, length, streamId);
      if (offerPosition < 0) {
        return POSTPONE_FRAGMENT_RESULT;
      } else {
        return CONSUME_FRAGMENT_RESULT;
      }
    } else {
      return CONSUME_FRAGMENT_RESULT;
    }
  }
}
