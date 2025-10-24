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

import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.CompensateEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.ConditionalEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.ErrorEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.EscalationEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.EventDefinition;
import io.camunda.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.SignalEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.camunda.zeebe.model.bpmn.util.ModelUtil;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class SubProcessValidator implements ModelElementValidator<SubProcess> {

  private static final List<Class<? extends EventDefinition>> SUPPORTED_START_TYPES =
      Arrays.asList(
          TimerEventDefinition.class,
          MessageEventDefinition.class,
          ErrorEventDefinition.class,
          SignalEventDefinition.class,
          EscalationEventDefinition.class,
          ConditionalEventDefinition.class);

  @Override
  public Class<SubProcess> getElementType() {
    return SubProcess.class;
  }

  @Override
  public void validate(
      final SubProcess element, final ValidationResultCollector validationResultCollector) {
    IdentifiableBpmnElementValidator.validate(element, validationResultCollector);
    final Collection<StartEvent> startEvents = element.getChildElementsByType(StartEvent.class);

    if (startEvents.size() != 1 && !(element instanceof AdHocSubProcess)) {
      validationResultCollector.addError(0, "Must have exactly one start event");
    }

    if (!startEvents.isEmpty()) {
      final StartEvent startEvent = startEvents.iterator().next();

      if (element.triggeredByEvent()) {
        validateEventSubprocess(validationResultCollector, startEvent, element);
      } else {
        validateEmbeddedSubprocess(validationResultCollector, startEvent);
      }
    }

    ModelUtil.verifyNoDuplicatedEventSubprocesses(
        element, error -> validationResultCollector.addError(0, error));

    ModelUtil.verifyLinkIntermediateEvents(
        element, error -> validationResultCollector.addError(0, error));
  }

  private void validateEmbeddedSubprocess(
      final ValidationResultCollector validationResultCollector, final StartEvent start) {
    if (!start.getEventDefinitions().isEmpty()) {
      validationResultCollector.addError(0, "Start events in subprocesses must be of type none");
    }
  }

  private void validateEventSubprocess(
      final ValidationResultCollector validationResultCollector,
      final StartEvent start,
      final SubProcess element) {
    final Collection<EventDefinition> eventDefinitions = start.getEventDefinitions();
    if (eventDefinitions.isEmpty()) {
      validationResultCollector.addError(
          0,
          "Start events in event subprocesses must be one of: message, timer, error, signal or escalation");
    }

    if (eventDefinitions.stream().anyMatch(CompensateEventDefinition.class::isInstance)
        && !(element.getParentElement() instanceof SubProcess)) {
      validationResultCollector.addError(
          0, "A compensation event subprocess is not allowed on the process level");
    }

    eventDefinitions.forEach(
        def -> {
          if (SUPPORTED_START_TYPES.stream().noneMatch(type -> type.isInstance(def))) {
            validationResultCollector.addError(
                0,
                "Start events in event subprocesses must be one of: message, timer, error, signal, escalation or conditional");
          }
        });

    ModelUtil.verifyEventDefinition(start, error -> validationResultCollector.addError(0, error));

    if (start.isInterrupting() && element.getParentElement() instanceof AdHocSubProcess) {
      validationResultCollector.addError(
          0, "An interrupting event subprocess is not allowed inside an ad-hoc subprocess");
    }
  }
}
