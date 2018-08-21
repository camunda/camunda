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
import io.zeebe.msgpack.property.DocumentProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;

public class WorkflowInstanceSubscriptionRecord extends UnpackedObject {

  private final LongProperty workflowInstanceKeyProp = new LongProperty("workflowInstanceKey");
  private final LongProperty activityInstanceKeyProp = new LongProperty("activityInstanceKey");
  private final StringProperty messageNameProp = new StringProperty("messageName");
  private final DocumentProperty payloadProp = new DocumentProperty("payload");

  public WorkflowInstanceSubscriptionRecord() {
    this.declareProperty(workflowInstanceKeyProp)
        .declareProperty(activityInstanceKeyProp)
        .declareProperty(messageNameProp)
        .declareProperty(payloadProp);
  }

  public long getWorkflowInstanceKey() {
    return workflowInstanceKeyProp.getValue();
  }

  public WorkflowInstanceSubscriptionRecord setWorkflowInstanceKey(long key) {
    workflowInstanceKeyProp.setValue(key);
    return this;
  }

  public long getActivityInstanceKey() {
    return activityInstanceKeyProp.getValue();
  }

  public WorkflowInstanceSubscriptionRecord setActivityInstanceKey(long key) {
    activityInstanceKeyProp.setValue(key);
    return this;
  }

  public DirectBuffer getMessageName() {
    return messageNameProp.getValue();
  }

  public WorkflowInstanceSubscriptionRecord setMessageName(DirectBuffer messageName) {
    messageNameProp.setValue(messageName);
    return this;
  }

  public DirectBuffer getPayload() {
    return payloadProp.getValue();
  }

  public WorkflowInstanceSubscriptionRecord setPayload(DirectBuffer payload) {
    payloadProp.setValue(payload);
    return this;
  }
}
