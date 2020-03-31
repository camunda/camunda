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

import io.zeebe.model.bpmn.instance.ErrorEventDefinition;
import io.zeebe.model.bpmn.instance.EventDefinition;
import io.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.model.bpmn.instance.SubProcess;
import io.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.zeebe.model.bpmn.util.ModelUtil;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class SubProcessValidator implements ModelElementValidator<SubProcess> {

  private static final List<Class<? extends EventDefinition>> SUPPORTED_START_TYPES =
      Arrays.asList(
          TimerEventDefinition.class, MessageEventDefinition.class, ErrorEventDefinition.class);

  @Override
  public Class<SubProcess> getElementType() {
    return SubProcess.class;
  }

  @Override
  public void validate(
      final SubProcess element, final ValidationResultCollector validationResultCollector) {
    final Collection<StartEvent> startEvents = element.getChildElementsByType(StartEvent.class);

    if (startEvents.size() != 1) {
      validationResultCollector.addError(0, "Must have exactly one start event");
    }

    if (!startEvents.isEmpty()) {
      final StartEvent startEvent = startEvents.iterator().next();

      if (element.triggeredByEvent()) {
        validateEventSubprocess(validationResultCollector, startEvent);
      } else {
        validateEmbeddedSubprocess(validationResultCollector, startEvent);
      }
    }

    ModelUtil.verifyNoDuplicatedEventSubprocesses(
        element, error -> validationResultCollector.addError(0, error));
  }

  private void validateEmbeddedSubprocess(
      final ValidationResultCollector validationResultCollector, final StartEvent start) {
    if (!start.getEventDefinitions().isEmpty()) {
      validationResultCollector.addError(0, "Start events in subprocesses must be of type none");
    }
  }

  private void validateEventSubprocess(
      final ValidationResultCollector validationResultCollector, final StartEvent start) {
    final Collection<EventDefinition> eventDefinitions = start.getEventDefinitions();
    if (eventDefinitions.isEmpty()) {
      validationResultCollector.addError(
          0, "Start events in event subprocesses must be one of: message, timer, error");
    }

    eventDefinitions.forEach(
        def -> {
          if (SUPPORTED_START_TYPES.stream().noneMatch(type -> type.isInstance(def))) {
            validationResultCollector.addError(
                0, "Start events in event subprocesses must be one of: message, timer, error");
          }
        });

    ModelUtil.verifyEventDefinition(start, error -> validationResultCollector.addError(0, error));
  }
}
