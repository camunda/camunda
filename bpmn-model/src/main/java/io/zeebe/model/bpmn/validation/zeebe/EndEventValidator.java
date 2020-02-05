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

import io.zeebe.model.bpmn.instance.EndEvent;
import io.zeebe.model.bpmn.instance.ErrorEventDefinition;
import io.zeebe.model.bpmn.instance.EventDefinition;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class EndEventValidator implements ModelElementValidator<EndEvent> {

  private static final List<Class<? extends EventDefinition>> SUPPORTED_EVENT_DEFINITIONS =
      Arrays.asList(ErrorEventDefinition.class);

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
            validationResultCollector.addError(0, "End events must be one of: none or error");
          }
        });
  }
}
