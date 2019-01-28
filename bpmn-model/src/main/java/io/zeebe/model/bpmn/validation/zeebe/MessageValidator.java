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
package io.zeebe.model.bpmn.validation.zeebe;

import io.zeebe.model.bpmn.instance.ExtensionElements;
import io.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.zeebe.model.bpmn.instance.Process;
import io.zeebe.model.bpmn.instance.ReceiveTask;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;
import java.util.Collection;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class MessageValidator implements ModelElementValidator<Message> {

  @Override
  public Class<Message> getElementType() {
    return Message.class;
  }

  @Override
  public void validate(Message element, ValidationResultCollector validationResultCollector) {
    if (isReferedByCatchEvent(element) || isReferedByReceiveTask(element)) {
      if (element.getName() == null || element.getName().isEmpty()) {
        validationResultCollector.addError(0, "Name must be present and not empty");
      }

      final ExtensionElements extensionElements = element.getExtensionElements();

      if (extensionElements == null
          || extensionElements.getChildElementsByType(ZeebeSubscription.class).size() != 1) {
        validationResultCollector.addError(
            0, "Must have exactly one zeebe:subscription extension element");
      }
    } else {
      validateIfReferredByStartEvent(element, validationResultCollector);
    }
  }

  private void validateIfReferredByStartEvent(
      Message element, ValidationResultCollector validationResultCollector) {
    final Collection<StartEvent> startEvents =
        element.getParentElement().getChildElementsByType(Process.class).stream()
            .flatMap(p -> p.getChildElementsByType(StartEvent.class).stream())
            .collect(Collectors.toList());
    final long numReferredStartEvents =
        startEvents.stream()
            .flatMap(i -> i.getEventDefinitions().stream())
            .filter(
                e ->
                    e instanceof MessageEventDefinition
                        && ((MessageEventDefinition) e).getMessage() == element)
            .count();

    if (numReferredStartEvents > 1) {
      validationResultCollector.addError(
          0, "A message cannot be referred by more than one start event");
    } else if (numReferredStartEvents == 1) {
      if (element.getName() == null || element.getName().isEmpty()) {
        validationResultCollector.addError(0, "Name must be present and not empty");
      }
    }
  }

  private boolean isReferedByCatchEvent(Message element) {
    final Collection<IntermediateCatchEvent> intermediateCatchEvents =
        element.getParentElement().getChildElementsByType(Process.class).stream()
            .flatMap(p -> p.getChildElementsByType(IntermediateCatchEvent.class).stream())
            .collect(Collectors.toList());

    return intermediateCatchEvents.stream()
        .flatMap(i -> i.getEventDefinitions().stream())
        .anyMatch(
            e ->
                e instanceof MessageEventDefinition
                    && ((MessageEventDefinition) e).getMessage() == element);
  }

  private boolean isReferedByReceiveTask(Message element) {
    final Collection<ReceiveTask> receiveTasks =
        element.getParentElement().getChildElementsByType(Process.class).stream()
            .flatMap(p -> p.getChildElementsByType(ReceiveTask.class).stream())
            .collect(Collectors.toList());

    return receiveTasks.stream().anyMatch(r -> r.getMessage() == element);
  }
}
