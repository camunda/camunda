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

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListeners;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ExecutionListenersValidator implements ModelElementValidator<ZeebeExecutionListeners> {

  @Override
  public Class<ZeebeExecutionListeners> getElementType() {
    return ZeebeExecutionListeners.class;
  }

  @Override
  public void validate(
      final ZeebeExecutionListeners element,
      final ValidationResultCollector validationResultCollector) {
    final Collection<ZeebeExecutionListener> executionListeners = element.getExecutionListeners();
    if (executionListeners == null || executionListeners.isEmpty()) {
      return;
    }

    final Function<ZeebeExecutionListener, String> eventTypeAndTypeClassifier =
        listener -> listener.getEventType() + "|" + listener.getType();

    // Group listeners by the combination of `eventType` and `type`
    final Map<String, List<ZeebeExecutionListener>> listenersGroupedByType =
        executionListeners.stream().collect(Collectors.groupingBy(eventTypeAndTypeClassifier));

    // Process only the groups with duplicates
    listenersGroupedByType.values().stream()
        .filter(duplicates -> duplicates.size() > 1)
        .forEach(duplicates -> reportDuplicateListeners(duplicates, validationResultCollector));
  }

  private void reportDuplicateListeners(
      final List<ZeebeExecutionListener> duplicates,
      final ValidationResultCollector validationResultCollector) {
    // Assumes all duplicates have the same `eventType` and `type`, so we take the first one
    final ZeebeExecutionListener representative = duplicates.get(0);
    final String errorMessage =
        String.format(
            "Found '%d' duplicates based on eventType[%s] and type[%s], these combinations should be unique.",
            duplicates.size(), representative.getEventType(), representative.getType());

    validationResultCollector.addError(0, errorMessage);
  }
}
