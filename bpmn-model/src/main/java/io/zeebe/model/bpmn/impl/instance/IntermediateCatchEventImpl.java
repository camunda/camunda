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
package io.zeebe.model.bpmn.impl.instance;

import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.instance.IntermediateMessageCatchEvent;
import io.zeebe.model.bpmn.instance.MessageSubscription;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

public class IntermediateCatchEventImpl extends FlowNodeImpl
    implements IntermediateMessageCatchEvent {

  private MessageEventDefinitionImpl messageEventDefinition;

  private MessageSubscription messageSubscription;

  @XmlElement(
      name = BpmnConstants.BPMN_ELEMENT_MESSAGE_EVENT_DEFINITION,
      namespace = BpmnConstants.BPMN20_NS)
  public void setMessageEventDefinition(MessageEventDefinitionImpl messageEventDefinition) {
    this.messageEventDefinition = messageEventDefinition;
  }

  public MessageEventDefinitionImpl getMessageEventDefinition() {
    return messageEventDefinition;
  }

  @Override
  public MessageSubscription getMessageSubscription() {
    return messageSubscription;
  }

  @XmlTransient
  public void setMessageSubscription(MessageSubscription messageSubscription) {
    this.messageSubscription = messageSubscription;
  }
}
