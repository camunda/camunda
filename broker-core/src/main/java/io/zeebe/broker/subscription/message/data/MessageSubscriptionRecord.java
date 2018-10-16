/*
 * Zeebe Broker Core
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
package io.zeebe.broker.subscription.message.data;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;

public class MessageSubscriptionRecord extends UnpackedObject {

  private final IntegerProperty workflowInstancePartitionIdProp =
      new IntegerProperty("workflowInstancePartitionId");
  private final LongProperty workflowInstanceKeyProp = new LongProperty("workflowInstanceKey");
  private final LongProperty activityInstanceKeyProp = new LongProperty("activityInstanceKey");
  private final StringProperty messageNameProp = new StringProperty("messageName");
  private final StringProperty correlationKeyProp = new StringProperty("correlationKey", "");

  public MessageSubscriptionRecord() {
    this.declareProperty(workflowInstancePartitionIdProp)
        .declareProperty(workflowInstanceKeyProp)
        .declareProperty(activityInstanceKeyProp)
        .declareProperty(messageNameProp)
        .declareProperty(correlationKeyProp);
  }

  public int getWorkflowInstancePartitionId() {
    return workflowInstancePartitionIdProp.getValue();
  }

  public MessageSubscriptionRecord setWorkflowInstancePartitionId(int partitionId) {
    workflowInstancePartitionIdProp.setValue(partitionId);
    return this;
  }

  public long getWorkflowInstanceKey() {
    return workflowInstanceKeyProp.getValue();
  }

  public MessageSubscriptionRecord setWorkflowInstanceKey(long key) {
    workflowInstanceKeyProp.setValue(key);
    return this;
  }

  public long getActivityInstanceKey() {
    return activityInstanceKeyProp.getValue();
  }

  public MessageSubscriptionRecord setActivityInstanceKey(long key) {
    activityInstanceKeyProp.setValue(key);
    return this;
  }

  public DirectBuffer getMessageName() {
    return messageNameProp.getValue();
  }

  public MessageSubscriptionRecord setMessageName(DirectBuffer messageName) {
    messageNameProp.setValue(messageName);
    return this;
  }

  public DirectBuffer getCorrelationKey() {
    return correlationKeyProp.getValue();
  }

  public MessageSubscriptionRecord setCorrelationKey(DirectBuffer correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }
}
