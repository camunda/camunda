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
package io.zeebe.gossip.dissemination;

import io.zeebe.transport.SocketAddress;
import io.zeebe.util.collection.Reusable;
import org.agrona.*;
import org.agrona.concurrent.UnsafeBuffer;

public class GossipSyncResponsePart implements Reusable {
  private final SocketAddress address = new SocketAddress();

  private final MutableDirectBuffer payloadBuffer = new ExpandableArrayBuffer();
  private final DirectBuffer payloadView = new UnsafeBuffer(payloadBuffer);
  private int payloadLength = 0;

  public void wrap(SocketAddress address, DirectBuffer payload, int offset, int length) {
    this.address.wrap(address);

    this.payloadLength = length;
    this.payloadBuffer.putBytes(0, payload, offset, length);
  }

  public void wrap(SocketAddress address, DirectBuffer payload) {
    this.address.wrap(address);

    this.payloadLength = payload.capacity();
    this.payloadBuffer.putBytes(0, payload, 0, payloadLength);
  }

  public SocketAddress getAddress() {
    return address;
  }

  public DirectBuffer getPayload() {
    payloadView.wrap(payloadBuffer, 0, payloadLength);
    return payloadView;
  }

  @Override
  public void reset() {
    address.reset();
    payloadLength = 0;
  }
}
