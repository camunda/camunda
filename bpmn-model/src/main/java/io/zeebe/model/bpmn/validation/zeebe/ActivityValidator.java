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

import io.zeebe.model.bpmn.instance.Activity;
import io.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.zeebe.model.bpmn.util.ModelUtil;
import java.util.HashSet;
import java.util.Set;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ActivityValidator implements ModelElementValidator<Activity> {
  @Override
  public Class<Activity> getElementType() {
    return Activity.class;
  }

  @Override
  public void validate(Activity element, ValidationResultCollector validationResultCollector) {
    final Set<String> messageNames = new HashSet<>();
    ModelUtil.getActivityMessageBoundaryEvents(element)
        .forEach(
            event ->
                validateMessageTriggerUniqueness(validationResultCollector, messageNames, event));
  }

  private void validateMessageTriggerUniqueness(
      ValidationResultCollector validationResultCollector,
      Set<String> boundaryEventMessageNames,
      MessageEventDefinition messageDefinition) {
    final String name = messageDefinition.getMessage().getName();
    final boolean didNotContain = boundaryEventMessageNames.add(name);

    if (!didNotContain) {
      validationResultCollector.addError(
          0,
          String.format(
              "Cannot have two message catch boundary events with the same name: %s", name));
    }
  }
}
