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

import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_WAIT_FOR_COMPLETION;
import static io.camunda.zeebe.model.bpmn.util.ModelUtil.validateExecutionListenersDefinitionForElement;

import io.camunda.zeebe.model.bpmn.instance.CompensateEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
import io.camunda.zeebe.model.bpmn.instance.ErrorEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.EscalationEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.EventDefinition;
import io.camunda.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.SignalEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.TerminateEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class EndEventValidator implements ModelElementValidator<EndEvent> {

  private static final List<Class<? extends EventDefinition>> SUPPORTED_EVENT_DEFINITIONS =
      Arrays.asList(
          ErrorEventDefinition.class,
          MessageEventDefinition.class,
          TerminateEventDefinition.class,
          SignalEventDefinition.class,
          EscalationEventDefinition.class,
          CompensateEventDefinition.class);

  @Override
  public Class<EndEvent> getElementType() {
    return EndEvent.class;
  }

  @Override
  public void validate(
      final EndEvent element, final ValidationResultCollector validationResultCollector) {

    if (!element.getOutgoing().isEmpty()) {
      validationResultCollector.addError(
          0, "End events must not have outgoing sequence flows to other elements.");
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
                    if (eventDefinition instanceof ErrorEventDefinition) {
                      final boolean endExecutionListenersDefined =
                          listeners.stream()
                              .map(ZeebeExecutionListener::getEventType)
                              .anyMatch(ZeebeExecutionListenerEventType.end::equals);
                      if (endExecutionListenersDefined) {
                        validationResultCollector.addError(
                            0,
                            "Execution listeners of type 'end' are not supported by [error] end events");
                      }
                    }
                  });
        });
  }

  private void validateEventDefinition(
      final EndEvent element, final ValidationResultCollector validationResultCollector) {
    final Collection<EventDefinition> eventDefinitions = element.getEventDefinitions();

    if (eventDefinitions.size() > 1) {
      validationResultCollector.addError(0, "Must have at most one event definition");
    }

    eventDefinitions.forEach(
        def -> {
          if (SUPPORTED_EVENT_DEFINITIONS.stream().noneMatch(type -> type.isInstance(def))) {
            validationResultCollector.addError(
                0,
                "End events must be one of: none, error, message, terminate, signal, escalation or compensation");
          }

          if (def instanceof CompensateEventDefinition) {
            final String waitForCompletion =
                def.getAttributeValue(BPMN_ATTRIBUTE_WAIT_FOR_COMPLETION);
            if (waitForCompletion != null && !Boolean.parseBoolean(waitForCompletion)) {
              validationResultCollector.addError(
                  0,
                  "A compensation end event waitForCompletion attribute must be true or not present");
            }
          }
        });
  }
}
