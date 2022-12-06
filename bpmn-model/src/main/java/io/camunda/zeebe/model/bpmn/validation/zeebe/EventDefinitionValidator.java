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

import io.camunda.zeebe.model.bpmn.instance.ConditionalEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.ErrorEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.EscalationEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.EventDefinition;
import io.camunda.zeebe.model.bpmn.instance.LinkEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.SignalEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.TerminateEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.TimerEventDefinition;
import java.util.Arrays;
import java.util.List;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class EventDefinitionValidator implements ModelElementValidator<EventDefinition> {

  private static final List<Class<? extends EventDefinition>> SUPPORTED_EVENT_DEFINITIONS =
      Arrays.asList(
          MessageEventDefinition.class,
          TimerEventDefinition.class,
          ErrorEventDefinition.class,
          TerminateEventDefinition.class,
          SignalEventDefinition.class,
          LinkEventDefinition.class,
          EscalationEventDefinition.class,
          ConditionalEventDefinition.class);

  @Override
  public Class<EventDefinition> getElementType() {
    return EventDefinition.class;
  }

  @Override
  public void validate(
      final EventDefinition element, final ValidationResultCollector validationResultCollector) {

    final Class<?> elementType = element.getElementType().getInstanceType();
    if (!SUPPORTED_EVENT_DEFINITIONS.contains(elementType)) {
      validationResultCollector.addError(0, "Event definition of this type is not supported");
    }
  }
}
