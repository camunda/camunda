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

import io.zeebe.model.bpmn.instance.Process;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.model.bpmn.util.ModelUtil;
import java.util.Collection;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ProcessValidator implements ModelElementValidator<Process> {

  @Override
  public Class<Process> getElementType() {
    return Process.class;
  }

  @Override
  public void validate(
      final Process element, final ValidationResultCollector validationResultCollector) {

    final Collection<StartEvent> topLevelStartEvents =
        element.getChildElementsByType(StartEvent.class);
    if (topLevelStartEvents.isEmpty()) {
      validationResultCollector.addError(0, "Must have at least one start event");
    } else if (topLevelStartEvents.stream().filter(this::isNoneEvent).count() > 1) {
      validationResultCollector.addError(0, "Multiple none start events are not allowed");
    }

    ModelUtil.verifyNoDuplicatedEventSubprocesses(
        element, error -> validationResultCollector.addError(0, error));
  }

  private boolean isNoneEvent(final StartEvent startEvent) {
    return startEvent.getEventDefinitions().isEmpty();
  }
}
