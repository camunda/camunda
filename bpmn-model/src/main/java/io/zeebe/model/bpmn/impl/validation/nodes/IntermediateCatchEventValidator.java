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
package io.zeebe.model.bpmn.impl.validation.nodes;

import io.zeebe.model.bpmn.impl.error.ErrorCollector;
import io.zeebe.model.bpmn.impl.instance.IntermediateCatchEventImpl;
import io.zeebe.model.bpmn.impl.instance.MessageEventDefinitionImpl;
import io.zeebe.model.bpmn.impl.instance.MessageImpl;
import io.zeebe.model.bpmn.impl.metadata.SubscriptionImpl;
import java.util.Map;

public class IntermediateCatchEventValidator {
  public void validate(
      ErrorCollector validationResult,
      IntermediateCatchEventImpl event,
      Map<String, MessageImpl> messages) {

    final MessageEventDefinitionImpl messageEventDefinition = event.getMessageEventDefinition();
    if (messageEventDefinition == null) {
      validationResult.addError(
          event, "A intermediate catch event must have a message event definition.");
      return;
    }

    final String messageRef = messageEventDefinition.getMessageRef();
    final MessageImpl message = messages.get(messageRef);
    if (message == null) {
      validationResult.addError(
          message, String.format("No message found with id '%s'", messageRef));
      return;
    }

    final String messageName = message.getName();
    if (messageName == null || messageName.isEmpty()) {
      validationResult.addError(message, "A message must have a name.");
      return;
    }

    final SubscriptionImpl subscription = message.getSubscription();
    if (subscription == null) {
      validationResult.addError(message, "A message must have a subscription element.");
      return;
    }

    final String correlationKey = subscription.getCorrelationKey();
    if (correlationKey == null || correlationKey.isEmpty()) {
      validationResult.addError(message, "A subscription must have a correlation-key.");
      return;
    }
  }
}
