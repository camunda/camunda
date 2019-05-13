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

public class OpenMessageSubscriptionCommand
    extends SbeBufferWriterReader<OpenMessageSubscriptionEncoder, OpenMessageSubscriptionDecoder> {

  private final OpenMessageSubscriptionEncoder encoder = new OpenMessageSubscriptionEncoder();
  private final OpenMessageSubscriptionDecoder decoder = new OpenMessageSubscriptionDecoder();

  private int subscriptionPartitionId;
  private long workflowInstanceKey;
  private long elementInstanceKey;
  private boolean closeOnCorrelate;

  private final UnsafeBuffer messageName = new UnsafeBuffer(0, 0);
  private final UnsafeBuffer correlationKey = new UnsafeBuffer(0, 0);

  @Override
  protected OpenMessageSubscriptionEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected OpenMessageSubscriptionDecoder getBodyDecoder() {
    return decoder;
  }

  @Override
  public int getLength() {
    return super.getLength()
        + OpenMessageSubscriptionDecoder.messageNameHeaderLength()
        + messageName.capacity()
        + OpenMessageSubscriptionDecoder.correlationKeyHeaderLength()
        + correlationKey.capacity();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    super.write(buffer, offset);

    encoder
        .subscriptionPartitionId(subscriptionPartitionId)
        .workflowInstanceKey(workflowInstanceKey)
        .elementInstanceKey(elementInstanceKey)
        .closeOnCorrelate(closeOnCorrelate ? BooleanType.TRUE : BooleanType.FALSE)
        .putMessageName(messageName, 0, messageName.capacity())
        .putCorrelationKey(correlationKey, 0, correlationKey.capacity());
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    super.wrap(buffer, offset, length);

    subscriptionPartitionId = decoder.subscriptionPartitionId();
    workflowInstanceKey = decoder.workflowInstanceKey();
    elementInstanceKey = decoder.elementInstanceKey();
    closeOnCorrelate = decoder.closeOnCorrelate() == BooleanType.TRUE;

    offset = decoder.limit();

    offset += OpenMessageSubscriptionDecoder.messageNameHeaderLength();
    final int messageNameLength = decoder.messageNameLength();
    messageName.wrap(buffer, offset, messageNameLength);
    offset += messageNameLength;
    decoder.limit(offset);

    offset += OpenMessageSubscriptionDecoder.correlationKeyHeaderLength();
    final int correlationKeyLength = decoder.correlationKeyLength();
    correlationKey.wrap(buffer, offset, correlationKeyLength);
    offset += correlationKeyLength;
    decoder.limit(offset);
  }

  @Override
  public void reset() {
    subscriptionPartitionId = OpenMessageSubscriptionDecoder.subscriptionPartitionIdNullValue();
    workflowInstanceKey = OpenMessageSubscriptionDecoder.workflowInstanceKeyNullValue();
    elementInstanceKey = OpenMessageSubscriptionDecoder.elementInstanceKeyNullValue();
    messageName.wrap(0, 0);
    correlationKey.wrap(0, 0);
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

  public DirectBuffer getCorrelationKey() {
    return correlationKey;
  }

  public boolean shouldCloseOnCorrelate() {
    return closeOnCorrelate;
  }

  public void setCloseOnCorrelate(boolean closeOnCorrelate) {
    this.closeOnCorrelate = closeOnCorrelate;
  }
}
