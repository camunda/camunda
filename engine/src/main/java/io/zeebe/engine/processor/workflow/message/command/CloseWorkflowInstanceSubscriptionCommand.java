/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor.workflow.message.command;

import io.zeebe.engine.util.SbeBufferWriterReader;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class CloseWorkflowInstanceSubscriptionCommand
    extends SbeBufferWriterReader<
        CloseWorkflowInstanceSubscriptionEncoder, CloseWorkflowInstanceSubscriptionDecoder> {

  private final CloseWorkflowInstanceSubscriptionEncoder encoder =
      new CloseWorkflowInstanceSubscriptionEncoder();
  private final CloseWorkflowInstanceSubscriptionDecoder decoder =
      new CloseWorkflowInstanceSubscriptionDecoder();

  private final DirectBuffer messageName = new UnsafeBuffer(0, 0);
  private int subscriptionPartitionId;
  private long workflowInstanceKey;
  private long elementInstanceKey;

  @Override
  protected CloseWorkflowInstanceSubscriptionEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected CloseWorkflowInstanceSubscriptionDecoder getBodyDecoder() {
    return decoder;
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    super.write(buffer, offset);

    encoder
        .subscriptionPartitionId(subscriptionPartitionId)
        .workflowInstanceKey(workflowInstanceKey)
        .elementInstanceKey(elementInstanceKey)
        .putMessageName(messageName, 0, messageName.capacity());
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    super.wrap(buffer, offset, length);

    subscriptionPartitionId = decoder.subscriptionPartitionId();
    workflowInstanceKey = decoder.workflowInstanceKey();
    elementInstanceKey = decoder.elementInstanceKey();
    decoder.wrapMessageName(messageName);
  }

  @Override
  public void reset() {
    subscriptionPartitionId =
        CloseWorkflowInstanceSubscriptionDecoder.subscriptionPartitionIdNullValue();
    workflowInstanceKey = CloseWorkflowInstanceSubscriptionDecoder.workflowInstanceKeyNullValue();
    elementInstanceKey = CloseWorkflowInstanceSubscriptionDecoder.elementInstanceKeyNullValue();
    messageName.wrap(0, 0);
  }

  @Override
  public int getLength() {
    return super.getLength()
        + CloseWorkflowInstanceSubscriptionDecoder.messageNameHeaderLength()
        + messageName.capacity();
  }

  public int getSubscriptionPartitionId() {
    return subscriptionPartitionId;
  }

  public void setSubscriptionPartitionId(int subscriptionPartitionId) {
    this.subscriptionPartitionId = subscriptionPartitionId;
  }

  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  public void setWorkflowInstanceKey(long workflowInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
  }

  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public void setElementInstanceKey(long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public DirectBuffer getMessageName() {
    return messageName;
  }

  public void setMessageName(DirectBuffer messageName) {
    this.messageName.wrap(messageName);
  }
}
