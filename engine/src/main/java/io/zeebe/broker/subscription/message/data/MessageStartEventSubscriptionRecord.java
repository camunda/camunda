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
package io.zeebe.broker.subscription.message.data;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;

public class MessageStartEventSubscriptionRecord extends UnpackedObject {

  private final LongProperty workflowKeyProp = new LongProperty("workflowKey");
  private final StringProperty messageNameProp = new StringProperty("messageName", "");
  private final StringProperty startEventIdProp = new StringProperty("startEventId", "");

  public MessageStartEventSubscriptionRecord() {
    this.declareProperty(workflowKeyProp)
        .declareProperty(messageNameProp)
        .declareProperty(startEventIdProp);
  }

  public MessageStartEventSubscriptionRecord setWorkflowKey(long key) {
    workflowKeyProp.setValue(key);
    return this;
  }

  public long getWorkflowKey() {
    return workflowKeyProp.getValue();
  }

  public MessageStartEventSubscriptionRecord setMessageName(DirectBuffer messageName) {
    messageNameProp.setValue(messageName);
    return this;
  }

  public DirectBuffer getMessageName() {
    return messageNameProp.getValue();
  }

  public MessageStartEventSubscriptionRecord setStartEventId(DirectBuffer startEventId) {
    this.startEventIdProp.setValue(startEventId);
    return this;
  }

  public DirectBuffer getStartEventId() {
    return startEventIdProp.getValue();
  }
}
