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
package io.zeebe.gossip.protocol;

import static io.zeebe.clustering.gossip.GossipEventEncoder.CustomEventsEncoder.senderIdNullValue;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.gossip.membership.GossipTerm;
import io.zeebe.util.collection.Reusable;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class CustomEvent implements Reusable {
  private final GossipTerm senderGossipTerm = new GossipTerm();
  private long senderId;

  private final MutableDirectBuffer typeBuffer = new ExpandableArrayBuffer();
  private final DirectBuffer typeView = new UnsafeBuffer(typeBuffer);
  private int typeLength = 0;

  private final MutableDirectBuffer payloadBuffer = new ExpandableArrayBuffer();
  private final DirectBuffer payloadView = new UnsafeBuffer(payloadBuffer);
  private int payloadLength = 0;

  public void typeLength(int length) {
    this.typeLength = length;
  }

  public MutableDirectBuffer getTypeBuffer() {
    return typeBuffer;
  }

  public void payloadLength(int length) {
    this.payloadLength = length;
  }

  public MutableDirectBuffer getPayloadBuffer() {
    return payloadBuffer;
  }

  public CustomEvent senderId(int senderId) {
    this.senderId = senderId;
    return this;
  }

  public CustomEvent senderGossipTerm(GossipTerm term) {
    this.senderGossipTerm.wrap(term);
    return this;
  }

  public CustomEvent type(DirectBuffer typeBuffer) {
    this.typeLength = typeBuffer.capacity();
    this.typeBuffer.putBytes(0, typeBuffer, 0, typeLength);
    return this;
  }

  public CustomEvent payload(DirectBuffer payloadBuffer) {
    return payload(payloadBuffer, 0, payloadBuffer.capacity());
  }

  public CustomEvent payload(DirectBuffer payloadBuffer, int offset, int length) {
    this.payloadLength = length;
    this.payloadBuffer.putBytes(0, payloadBuffer, offset, length);
    return this;
  }

  public GossipTerm getSenderGossipTerm() {
    return senderGossipTerm;
  }

  public int getSenderId() {
    return (int) senderId;
  }

  public DirectBuffer getType() {
    typeView.wrap(typeBuffer, 0, typeLength);
    return typeView;
  }

  public DirectBuffer getPayload() {
    payloadView.wrap(payloadBuffer, 0, payloadLength);
    return payloadView;
  }

  public int getTypeLength() {
    return typeLength;
  }

  public int getPayloadLength() {
    return payloadLength;
  }

  @Override
  public void reset() {
    senderGossipTerm.epoch(0L).heartbeat(0L);
    senderId = senderIdNullValue();

    typeLength(0);
    payloadLength(0);
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("CustomEvent [senderId=");
    builder.append(senderId);
    builder.append(", senderGossipTerm=");
    builder.append(senderGossipTerm);
    builder.append(", type=");
    builder.append(bufferAsString(typeView, 0, typeLength));
    builder.append(", payload=");
    builder.append(bufferAsString(payloadView, 0, payloadLength));
    builder.append("]");
    return builder.toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final CustomEvent that = (CustomEvent) obj;
    return senderId == that.senderId
        && Objects.equals(senderGossipTerm, that.senderGossipTerm)
        && Objects.equals(typeView, that.typeView)
        && Objects.equals(payloadView, that.payloadView);
  }

  @Override
  public int hashCode() {
    return Objects.hash(senderGossipTerm, senderId, typeView, payloadView);
  }
}
