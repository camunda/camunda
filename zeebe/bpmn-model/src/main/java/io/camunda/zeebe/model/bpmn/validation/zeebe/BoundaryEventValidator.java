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
package io.camunda.zeebe.model.bpmn.validation.zeebe;

import static io.camunda.zeebe.model.bpmn.util.ModelUtil.validateExecutionListenersDefinitionForElement;

import io.camunda.zeebe.model.bpmn.instance.Association;
import io.camunda.zeebe.model.bpmn.instance.BoundaryEvent;
import io.camunda.zeebe.model.bpmn.instance.CompensateEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.ConditionalEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.ErrorEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.EscalationEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.EventDefinition;
import io.camunda.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.SignalEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType;
import io.camunda.zeebe.model.bpmn.util.ModelUtil;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class BoundaryEventValidator implements ModelElementValidator<BoundaryEvent> {

  private static final List<Class<? extends EventDefinition>> SUPPORTED_EVENT_DEFINITIONS =
      Arrays.asList(
          TimerEventDefinition.class,
          MessageEventDefinition.class,
          ErrorEventDefinition.class,
          SignalEventDefinition.class,
          EscalationEventDefinition.class,
          CompensateEventDefinition.class,
          ConditionalEventDefinition.class);

  @Override
  public Class<BoundaryEvent> getElementType() {
    return BoundaryEvent.class;
  }

  @Override
  public void validate(
      final BoundaryEvent element, final ValidationResultCollector validationResultCollector) {
    IdentifiableBpmnElementValidator.validate(element, validationResultCollector);

    if (element.getAttachedTo() == null) {
      validationResultCollector.addError(0, "Must be attached to an activity");
    }

    if (!element.getIncoming().isEmpty()) {
      validationResultCollector.addError(0, "Cannot have incoming sequence flows");
    }

    if (!isValidCompensationDefinition(element)) {
      validationResultCollector.addError(
          0,
          "Compensation boundary events must have a compensation association and no outgoing sequence flows");
    }

    validateEventDefinition(element, validationResultCollector);

    validateExecutionListenersDefinitionForElement(
        element,
        validationResultCollector,
        listeners -> {
          final Collection<EventDefinition> eventDefinitions = element.getEventDefinitions();
          eventDefinitions.stream()
              .findFirst()
              .ifPresent(
                  eventDefinition -> {
                    if (eventDefinition instanceof CompensateEventDefinition) {
                      validationResultCollector.addError(
                          0,
                          "Execution listeners of type 'start' and 'end' are not supported by [compensation] boundary events");
                    } else {
                      final boolean startExecutionListenersDefined =
                          listeners.stream()
                              .map(ZeebeExecutionListener::getEventType)
                              .anyMatch(ZeebeExecutionListenerEventType.start::equals);
                      if (startExecutionListenersDefined) {
                        validationResultCollector.addError(
                            0,
                            "Execution listeners of type 'start' are not supported by boundary events");
                      }
                    }
                  });
        });
  }

  private boolean isValidCompensationDefinition(final BoundaryEvent element) {
    final boolean noCompensationDefinition =
        element.getEventDefinitions().stream()
            .noneMatch(CompensateEventDefinition.class::isInstance);

    if (noCompensationDefinition) {
      // nothing to validate if there is no compensation definition
      return true;
    }

    final Optional<Association> association =
        element.getModelInstance().getModelElementsByType(Association.class).stream()
            .filter(a -> a.getSource().getId().equals(element.getId()))
            .findFirst();

    // A compensation boundary event should have no outgoing sequence flows, and a compensation
    // handler connected by an association.
    return element.getOutgoing().isEmpty() && association.isPresent();
  }

  private void validateEventDefinition(
      final BoundaryEvent element, final ValidationResultCollector validationResultCollector) {
    final Collection<EventDefinition> eventDefinitions = element.getEventDefinitions();

    if (eventDefinitions.size() != 1) {
      validationResultCollector.addError(0, "Must have exactly one event definition");
    }

    eventDefinitions.forEach(
        def -> {
          if (SUPPORTED_EVENT_DEFINITIONS.stream().noneMatch(type -> type.isInstance(def))) {
            validationResultCollector.addError(
                0,
                "Boundary events must be one of: timer, message, error, signal, escalation, conditional");
          }
        });

    ModelUtil.verifyEventDefinition(element, error -> validationResultCollector.addError(0, error));
  }
}
