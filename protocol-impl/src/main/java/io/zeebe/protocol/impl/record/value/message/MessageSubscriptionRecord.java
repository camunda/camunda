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
import io.zeebe.msgpack.property.BooleanProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.WorkflowInstanceRelated;
import org.agrona.DirectBuffer;

public class MessageSubscriptionRecord extends UnpackedObject implements WorkflowInstanceRelated {

  private final LongProperty workflowInstanceKeyProp = new LongProperty("workflowInstanceKey");
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey");
  private final LongProperty messageKeyProp = new LongProperty("messageKey");
  private final StringProperty messageNameProp = new StringProperty("messageName", "");
  private final StringProperty correlationKeyProp = new StringProperty("correlationKey", "");
  private final BooleanProperty closeOnCorrelateProp =
      new BooleanProperty("closeOnCorrelate", true);

  public MessageSubscriptionRecord() {
    this.declareProperty(workflowInstanceKeyProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(messageKeyProp)
        .declareProperty(messageNameProp)
        .declareProperty(correlationKeyProp)
        .declareProperty(closeOnCorrelateProp);
  }

  public long getWorkflowInstanceKey() {
    return workflowInstanceKeyProp.getValue();
  }

  public MessageSubscriptionRecord setWorkflowInstanceKey(long key) {
    workflowInstanceKeyProp.setValue(key);
    return this;
  }

  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public MessageSubscriptionRecord setElementInstanceKey(long key) {
    elementInstanceKeyProp.setValue(key);
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

  public boolean shouldCloseOnCorrelate() {
    return closeOnCorrelateProp.getValue();
  }

  public MessageSubscriptionRecord setCloseOnCorrelate(boolean closeOnCorrelate) {
    this.closeOnCorrelateProp.setValue(closeOnCorrelate);
    return this;
  }

  public long getMessageKey() {
    return messageKeyProp.getValue();
  }

  public MessageSubscriptionRecord setMessageKey(long messageKey) {
    this.messageKeyProp.setValue(messageKey);
    return this;
  }
}
