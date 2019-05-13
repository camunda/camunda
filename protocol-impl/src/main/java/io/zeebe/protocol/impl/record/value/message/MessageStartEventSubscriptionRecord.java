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
package io.zeebe.protocol.impl.record.value.message;

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
