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
package io.zeebe.model.bpmn.builder;

import io.zeebe.model.bpmn.impl.instance.ExtensionElementsImpl;
import io.zeebe.model.bpmn.impl.instance.MessageImpl;
import io.zeebe.model.bpmn.impl.metadata.SubscriptionImpl;
import java.util.List;

public class BpmnMessageEventBuilder {

  private final List<MessageImpl> messages;

  private String name = "";
  private String correlationKey = "";

  public BpmnMessageEventBuilder(List<MessageImpl> messages) {
    this.messages = messages;
  }

  public MessageImpl done() {
    return messages
        .stream()
        .filter(
            msg ->
                msg.getName().equals(name)
                    && msg.getSubscription().getCorrelationKey().equals(correlationKey))
        .findFirst()
        .orElseGet(this::createMessage);
  }

  private MessageImpl createMessage() {
    final MessageImpl message = new MessageImpl();
    messages.add(message);

    message.setId("message-" + messages.size());
    message.setName(name);

    final ExtensionElementsImpl extensionElements = new ExtensionElementsImpl();
    final SubscriptionImpl subscription = new SubscriptionImpl();
    subscription.setCorrelationKey(correlationKey);

    extensionElements.setSubscription(subscription);
    message.setExtensionElements(extensionElements);

    return message;
  }

  public BpmnMessageEventBuilder messageName(String messageName) {
    this.name = messageName;
    return this;
  }

  public BpmnMessageEventBuilder correlationKey(String correlationKey) {
    this.correlationKey = correlationKey;
    return this;
  }
}
