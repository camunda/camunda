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
package io.zeebe.model.bpmn.builder.zeebe;

import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.AbstractBaseElementBuilder;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;

public class MessageBuilder extends AbstractBaseElementBuilder<MessageBuilder, Message> {

  public MessageBuilder(final BpmnModelInstance modelInstance, final Message element) {
    super(modelInstance, element, MessageBuilder.class);
  }

  public MessageBuilder name(final String name) {
    element.setName(name);
    return this;
  }

  public MessageBuilder nameExpression(final String nameExpression) {
    return name(asZeebeExpression(nameExpression));
  }

  public MessageBuilder zeebeCorrelationKey(String correlationKey) {
    final ZeebeSubscription subscription = getCreateSingleExtensionElement(ZeebeSubscription.class);
    subscription.setCorrelationKey(correlationKey);
    return this;
  }

  public MessageBuilder zeebeCorrelationKeyExpression(final String correlationKeyExpression) {
    return zeebeCorrelationKey(asZeebeExpression(correlationKeyExpression));
  }
}
