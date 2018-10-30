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

import io.zeebe.model.bpmn.instance.BoundaryEvent;
import io.zeebe.model.bpmn.instance.EventDefinition;
import io.zeebe.model.bpmn.instance.TimerEventDefinition;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class BoundaryEventValidator implements ModelElementValidator<BoundaryEvent> {
  private static final List<Class<? extends EventDefinition>> SUPPORTED_EVENTS =
      Arrays.asList(TimerEventDefinition.class);

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

    if (!element.cancelActivity()) {
      validationResultCollector.addError(0, "Non-interrupting boundary events are not supported");
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

      if (SUPPORTED_EVENTS.stream().noneMatch(c -> c.isAssignableFrom(type))) {
        validationResultCollector.addError(0, "Event definition must be one of: timer");
      }
    }
  }
}
