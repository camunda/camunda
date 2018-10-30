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

import io.zeebe.model.bpmn.instance.CatchEvent;
import io.zeebe.model.bpmn.instance.EventDefinition;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.zeebe.model.bpmn.instance.TimeDuration;
import io.zeebe.model.bpmn.instance.TimerEventDefinition;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class CatchEventValidator implements ModelElementValidator<CatchEvent> {
  @Override
  public Class<CatchEvent> getElementType() {
    return CatchEvent.class;
  }

  @Override
  public void validate(CatchEvent element, ValidationResultCollector validationResultCollector) {
    final Collection<EventDefinition> eventDefinitions = element.getEventDefinitions();
    for (final EventDefinition eventDefinition : eventDefinitions) {
      if (eventDefinition instanceof MessageEventDefinition) {
        validateMessageEventDefinition(
            validationResultCollector, (MessageEventDefinition) eventDefinition);
      } else if (eventDefinition instanceof TimerEventDefinition) {
        validateTimerEventDefinition(
            validationResultCollector, (TimerEventDefinition) eventDefinition);
      }
    }
  }

  private void validateMessageEventDefinition(
      ValidationResultCollector validationResultCollector,
      MessageEventDefinition messageEventDefinition) {
    final Message message = messageEventDefinition.getMessage();

    if (message == null) {
      validationResultCollector.addError(0, "Must reference a message");
    }
  }

  private void validateTimerEventDefinition(
      ValidationResultCollector validationResultCollector,
      TimerEventDefinition timerEventDefinition) {
    final TimeDuration timeDuration = timerEventDefinition.getTimeDuration();

    if (timeDuration == null) {
      validationResultCollector.addError(0, "Must have a time duration");
    } else {
      try {
        Duration.parse(timeDuration.getTextContent());
      } catch (DateTimeParseException e) {
        validationResultCollector.addError(0, "Time duration is invalid");
      }
    }
  }
}
