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
import io.zeebe.msgpack.property.DocumentProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.WorkflowInstanceRelated;
import org.agrona.DirectBuffer;

public class WorkflowInstanceSubscriptionRecord extends UnpackedObject
    implements WorkflowInstanceRelated {

  private final IntegerProperty subscriptionPartitionIdProp =
      new IntegerProperty("subscriptionPartitionId");
  private final LongProperty workflowInstanceKeyProp = new LongProperty("workflowInstanceKey");
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey");
  private final LongProperty messageKeyProp = new LongProperty("messageKey");
  private final StringProperty messageNameProp = new StringProperty("messageName", "");
  private final DocumentProperty variablesProp = new DocumentProperty("variables");
  private final BooleanProperty closeOnCorrelateProp =
      new BooleanProperty("closeOnCorrelate", true);

  public WorkflowInstanceSubscriptionRecord() {
    this.declareProperty(subscriptionPartitionIdProp)
        .declareProperty(workflowInstanceKeyProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(messageKeyProp)
        .declareProperty(messageNameProp)
        .declareProperty(variablesProp)
        .declareProperty(closeOnCorrelateProp);
  }

  public int getSubscriptionPartitionId() {
    return subscriptionPartitionIdProp.getValue();
  }

  public WorkflowInstanceSubscriptionRecord setSubscriptionPartitionId(int partitionId) {
    subscriptionPartitionIdProp.setValue(partitionId);
    return this;
  }

  public long getWorkflowInstanceKey() {
    return workflowInstanceKeyProp.getValue();
  }

  public WorkflowInstanceSubscriptionRecord setWorkflowInstanceKey(long key) {
    workflowInstanceKeyProp.setValue(key);
    return this;
  }

  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public WorkflowInstanceSubscriptionRecord setElementInstanceKey(long key) {
    elementInstanceKeyProp.setValue(key);
    return this;
  }

  public long getMessageKey() {
    return messageKeyProp.getValue();
  }

  public WorkflowInstanceSubscriptionRecord setMessageKey(long messageKey) {
    messageKeyProp.setValue(messageKey);
    return this;
  }

  public DirectBuffer getMessageName() {
    return messageNameProp.getValue();
  }

  public WorkflowInstanceSubscriptionRecord setMessageName(DirectBuffer messageName) {
    messageNameProp.setValue(messageName);
    return this;
  }

  public DirectBuffer getVariables() {
    return variablesProp.getValue();
  }

  public WorkflowInstanceSubscriptionRecord setVariables(DirectBuffer variables) {
    variablesProp.setValue(variables);
    return this;
  }

  public boolean shouldCloseOnCorrelate() {
    return closeOnCorrelateProp.getValue();
  }

  public WorkflowInstanceSubscriptionRecord setCloseOnCorrelate(boolean closeOnCorrelate) {
    this.closeOnCorrelateProp.setValue(closeOnCorrelate);
    return this;
  }
}
