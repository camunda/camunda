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

public class CorrelateWorkflowInstanceSubscriptionCommand
    extends SbeBufferWriterReader<
        CorrelateWorkflowInstanceSubscriptionEncoder,
        CorrelateWorkflowInstanceSubscriptionDecoder> {

  private final CorrelateWorkflowInstanceSubscriptionEncoder encoder =
      new CorrelateWorkflowInstanceSubscriptionEncoder();
  private final CorrelateWorkflowInstanceSubscriptionDecoder decoder =
      new CorrelateWorkflowInstanceSubscriptionDecoder();

  private int subscriptionPartitionId;
  private long workflowInstanceKey;
  private long elementInstanceKey;
  private long messageKey;

  private final UnsafeBuffer messageName = new UnsafeBuffer(0, 0);
  private final UnsafeBuffer variables = new UnsafeBuffer(0, 0);

  @Override
  protected CorrelateWorkflowInstanceSubscriptionEncoder getBodyEncoder() {
    return encoder;
  }

  @Override
  protected CorrelateWorkflowInstanceSubscriptionDecoder getBodyDecoder() {
    return decoder;
  }

  @Override
  public int getLength() {
    return super.getLength()
        + CorrelateWorkflowInstanceSubscriptionDecoder.messageNameHeaderLength()
        + messageName.capacity()
        + CorrelateWorkflowInstanceSubscriptionDecoder.variablesHeaderLength()
        + variables.capacity();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    super.write(buffer, offset);

    encoder
        .subscriptionPartitionId(subscriptionPartitionId)
        .workflowInstanceKey(workflowInstanceKey)
        .elementInstanceKey(elementInstanceKey)
        .messageKey(messageKey)
        .putMessageName(messageName, 0, messageName.capacity())
        .putVariables(variables, 0, variables.capacity());
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    super.wrap(buffer, offset, length);

    subscriptionPartitionId = decoder.subscriptionPartitionId();
    workflowInstanceKey = decoder.workflowInstanceKey();
    elementInstanceKey = decoder.elementInstanceKey();
    messageKey = decoder.messageKey();

    offset = decoder.limit();

    offset += CorrelateWorkflowInstanceSubscriptionDecoder.messageNameHeaderLength();
    final int messageNameLength = decoder.messageNameLength();
    messageName.wrap(buffer, offset, messageNameLength);
    offset += messageNameLength;
    decoder.limit(offset);

    offset += CorrelateWorkflowInstanceSubscriptionDecoder.variablesHeaderLength();
    final int variablesLength = decoder.variablesLength();
    variables.wrap(buffer, offset, variablesLength);
    offset += variablesLength;
    decoder.limit(offset);
  }

  @Override
  public void reset() {
    subscriptionPartitionId =
        CorrelateWorkflowInstanceSubscriptionDecoder.subscriptionPartitionIdNullValue();
    workflowInstanceKey =
        CorrelateWorkflowInstanceSubscriptionDecoder.workflowInstanceKeyNullValue();
    elementInstanceKey = CorrelateWorkflowInstanceSubscriptionDecoder.elementInstanceKeyNullValue();
    messageKey = CorrelateWorkflowInstanceSubscriptionDecoder.messageKeyNullValue();

    messageName.wrap(0, 0);
    variables.wrap(0, 0);
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

  public long getMessageKey() {
    return messageKey;
  }

  public void setMessageKey(final long messageKey) {
    this.messageKey = messageKey;
  }

  public DirectBuffer getMessageName() {
    return messageName;
  }

  public DirectBuffer getVariables() {
    return variables;
  }
}
