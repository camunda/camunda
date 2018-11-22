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

import io.zeebe.model.bpmn.impl.instance.MessageEventDefinitionImpl;
import io.zeebe.model.bpmn.impl.instance.TimerEventDefinitionImpl;
import io.zeebe.model.bpmn.instance.BoundaryEvent;
import io.zeebe.model.bpmn.instance.EventDefinition;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class BoundaryEventValidator implements ModelElementValidator<BoundaryEvent> {
  private static final Map<Class<? extends EventDefinition>, SupportLevel> SUPPORTED_EVENTS =
      new HashMap<>();

  static {
    SUPPORTED_EVENTS.put(TimerEventDefinitionImpl.class, SupportLevel.All);
    SUPPORTED_EVENTS.put(MessageEventDefinitionImpl.class, SupportLevel.Interrupting);
  }

  @Override
  public Class<BoundaryEvent> getElementType() {
    return BoundaryEvent.class;
  }

  @Override
  public void validate(BoundaryEvent element, ValidationResultCollector validationResultCollector) {
    if (element.getAttachedTo() == null) {
      validationResultCollector.addError(0, "Must be attached to an activity");
    }

    if (element.getIncoming().size() > 0) {
      validationResultCollector.addError(0, "Cannot have incoming sequence flows");
    }

    if (element.getOutgoing().size() < 1) {
      validationResultCollector.addError(0, "Must have at least one outgoing sequence flow");
    }

    validateEventDefinition(element, validationResultCollector);
  }

  private void validateEventDefinition(
      BoundaryEvent element, ValidationResultCollector validationResultCollector) {
    final Collection<EventDefinition> eventDefinitions = element.getEventDefinitions();

    if (eventDefinitions.size() != 1) {
      validationResultCollector.addError(0, "Must have exactly one event definition");
    } else {
      final EventDefinition eventDefinition = eventDefinitions.iterator().next();
      final Class<? extends EventDefinition> type = eventDefinition.getClass();
      final SupportLevel supportLevel = SUPPORTED_EVENTS.getOrDefault(type, SupportLevel.None);

      validateSupportLevel(element, validationResultCollector, supportLevel);
    }
  }

  private void validateSupportLevel(
      BoundaryEvent element,
      ValidationResultCollector validationResultCollector,
      SupportLevel supportLevel) {
    switch (supportLevel) {
      case None:
        validationResultCollector.addError(0, "Boundary events must be one of: timer, message");
        break;
      case Interrupting:
        if (!element.cancelActivity()) {
          validationResultCollector.addError(
              0, "Non-interrupting events of this type are not supported");
        }
        break;
      case NonInterrupting:
        if (element.cancelActivity()) {
          validationResultCollector.addError(
              0, "Interrupting events of this type are not supported");
        }
        break;
    }
  }

  public enum SupportLevel {
    None,
    Interrupting,
    NonInterrupting,
    All,
  }
}
